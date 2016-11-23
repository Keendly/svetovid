package com.keendly.svetovid;

import static com.keendly.svetovid.DeliveryWorkflowMapper.*;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.simpleworkflow.flow.DecisionContextProvider;
import com.amazonaws.services.simpleworkflow.flow.DecisionContextProviderImpl;
import com.amazonaws.services.simpleworkflow.flow.WorkflowClock;
import com.amazonaws.services.simpleworkflow.flow.annotations.Asynchronous;
import com.amazonaws.services.simpleworkflow.flow.core.OrPromise;
import com.amazonaws.services.simpleworkflow.flow.core.Promise;
import com.amazonaws.services.simpleworkflow.flow.core.Settable;
import com.amazonaws.services.simpleworkflow.flow.core.TryCatch;
import com.amazonaws.util.IOUtils;
import com.amazonaws.util.json.Jackson;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keendly.svetovid.activities.extract.ExtractArticlesActivity;
import com.keendly.svetovid.activities.extract.model.ExtractFinished;
import com.keendly.svetovid.activities.extract.model.ExtractRequest;
import com.keendly.svetovid.activities.extract.model.ExtractResult;
import com.keendly.svetovid.activities.generate.GenerateEbookActivity;
import com.keendly.svetovid.activities.generate.model.Book;
import com.keendly.svetovid.activities.generate.model.GenerateFinished;
import com.keendly.svetovid.activities.generate.model.TriggerGenerateRequest;
import com.keendly.svetovid.activities.generatelinks.model.ActionLink;
import com.keendly.svetovid.activities.generatelinks.model.GenerateLinksRequest;
import com.keendly.svetovid.activities.generatelinks.model.GenerateLinksResponse;
import com.keendly.svetovid.activities.send.SendEbookActivity;
import com.keendly.svetovid.activities.send.model.SendRequest;
import com.keendly.svetovid.activities.update.UpdateDeliveryActivity;
import com.keendly.svetovid.activities.update.model.UpdateRequest;
import com.keendly.svetovid.model.DeliveryItem;
import com.keendly.svetovid.model.DeliveryRequest;
import com.keendly.svetovid.s3.S3ClientHolder;
import com.keendly.svetovid.utils.Config;
import com.keendly.svetovid.utils.LambdaInvoker;
import com.keendly.svetovid.utils.MessageKeyGenerator;
import com.keendly.svetovid.utils.WorkflowUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DeliveryWorkflowImpl implements DeliveryWorkflow {

    private static final Logger LOG = LoggerFactory.getLogger(DeliveryWorkflowImpl.class);
    private static final String CANCEL_EXECUTION_DESCRIPTION = "cancelExecution";
    private static final int REQUEST_MAX_SIZE = 32000;

    private final Settable<GenerateFinished> generateResult = new Settable<>();
    private final Settable<ExtractFinished> extractResult = new Settable<>();

    private DecisionContextProvider contextProvider = new DecisionContextProviderImpl();
    private WorkflowClock clock = contextProvider.getDecisionContext().getWorkflowClock();

    private final AmazonS3 s3 = S3ClientHolder.get();
    private final WorkflowUtils workflowUtils = WorkflowUtils.get();
    private final MessageKeyGenerator messageKeyGenerator = MessageKeyGenerator.get();

    private DeliveryState state;

    @Override
    public void deliver(String requestString) throws IOException {
        setState(DeliveryState.STARTED);

        // map request string to delivery request object
        DeliveryRequest request = deserializeDeliveryRequest(requestString);

        new TryCatch() {
            @Override
            protected void doTry() throws Throwable {
                Settable<Boolean> cancelExecution = new Settable<>();
                cancelExecution.setDescription(CANCEL_EXECUTION_DESCRIPTION);

                // extract articles
                ExtractArticlesActivity extractArticlesActivity = new ExtractArticlesActivity();

                Optional<ExtractRequest> extractRequestOptional = mapDeliveryRequestToExtractArticlesRequest(request);
                if (!extractRequestOptional.isPresent()){
                    cancelExecution.set(Boolean.TRUE);
                    return;
                }

                // generate action links
                Promise<String> generateLinksResult = invokeGenerateActionLinks(request);

                ExtractRequest extractRequest = extractRequestOptional.get();
                if (isTooBig(extractRequest)){
                    String key = storeInS3(Jackson.toJsonString(extractRequest.content));
                    extractRequest.s3Content = key;
                    extractRequest.content = null;
                }

                extractRequest.runId = workflowUtils.getRunId();
                extractRequest.workflowId = workflowUtils.getWorkFlowId();
                LOG.trace("Triggering extract with {}", Jackson.toJsonString(extractRequest));

                Promise<String> triggerExtractResult =
                    extractArticlesActivity.invoke(Jackson.toJsonString(extractRequest));

                Promise timer = clock.createTimer(Config.getExtractionTimeout());

                // generate ebook
                Promise<String> triggerGenerateResult =
                    invokeTriggerGenerate(request, new OrPromise(extractResult, cancelExecution, timer),
                        new OrPromise(generateLinksResult, cancelExecution));

                // send email
                Promise<String> sendResult =
                    invokeSendEmail(request, new OrPromise(generateResult, cancelExecution));

                // update delivery
                Promise<String> updateResult =
                    invokeUpdateDelivery(request, new OrPromise(sendResult, cancelExecution));

//                setState(updateDeliveryActivity.getCompletedState(), updateResult);
            }

            @Override
            protected void doCatch(Throwable e) throws Throwable {
                LOG.error("Error during workflow execution", e);
                throw e;
//                setState(DeliveryState.ERROR);
            }
        };
    }

    private static boolean isTooBig(ExtractRequest request){
        return Jackson.toJsonString(request).length() > REQUEST_MAX_SIZE;
    }

    @Asynchronous
    public Promise<String> invokeTriggerGenerate(DeliveryRequest deliveryRequest,
        OrPromise extractResultsOrCancelOrTimeout, OrPromise generateLinksResultOrCancel)
        throws IOException {
        if (isExecutionCanceled(extractResultsOrCancelOrTimeout)){
            clock.cancelTimer(getTimeOutTask(extractResultsOrCancelOrTimeout));
            return Promise.asPromise("canceled");
        }

        GenerateEbookActivity generateEbookActivity = new GenerateEbookActivity();
        Book book;
        if (isTimeOut(extractResultsOrCancelOrTimeout)){
            LOG.trace("Timeout waiting for extraction result after {} seconds", Config.getExtractionTimeout());
            book = mapDeliveryRequestToBook(deliveryRequest);
        } else {
            Promise<ExtractFinished> extractFinished = getReadyOne(extractResultsOrCancelOrTimeout);
            clock.cancelTimer(getTimeOutTask(extractResultsOrCancelOrTimeout));
            LOG.trace("Got extract results {}", extractFinished.get());

            if (!extractFinished.get().success){
                LOG.error("Error during extracting");
                throw new RuntimeException("Error during extraction: " + extractFinished.get().error);
            }

            // fetch extract results from S3
            GetObjectRequest getObjectRequest = new GetObjectRequest("keendly", extractFinished.get().key);
            S3Object object = s3.getObject(getObjectRequest);

            JavaType type = constructListType(ExtractResult.class);
            book =
                mapDeliveryRequestAndExtractResultToBook(deliveryRequest,
                    mapToOutput(IOUtils.toString(object.getObjectContent()), type));
        }

        Promise<String> generatedLinks = getReadyOne(generateLinksResultOrCancel);
        GenerateLinksResponse generateLinksResponse =  mapToOutput(generatedLinks.get(), GenerateLinksResponse.class);
        if (generateLinksResponse.s3Links != null){
            GetObjectRequest getObjectRequest = new GetObjectRequest(
                generateLinksResponse.s3Links.bucket, generateLinksResponse.s3Links.key);
            S3Object object = s3.getObject(getObjectRequest);

            Map<String, List<ActionLink>> links = new ObjectMapper()
                .readValue(IOUtils.toString(object.getObjectContent()).getBytes("UTF8"),
                    new TypeReference< Map<String, List<ActionLink>>>(){});

            generateLinksResponse.links = links;
        }
        book = addActionLinksToBook(book, generateLinksResponse);

        // store ebook generation request in s3
        String key = storeInS3(Jackson.toJsonString(book));
        TriggerGenerateRequest triggerGenerateRequest = new TriggerGenerateRequest();
        triggerGenerateRequest.s3Content = key;
        triggerGenerateRequest.runId = workflowUtils.getRunId();
        triggerGenerateRequest.workflowId = workflowUtils.getWorkFlowId();

        String request = Jackson.toJsonString(triggerGenerateRequest);
        LOG.trace("Triggering generate with {}", request);

        Promise<String> triggerResponse =
                    generateEbookActivity.invoke(request);

        return triggerResponse;
    }

    private String storeInS3(String content){
        String key = messageKeyGenerator.generate();
        s3.putObject("keendly", key, content);
        return key;
    }

    @Asynchronous
    public Promise<String> invokeSendEmail(DeliveryRequest deliveryRequest, OrPromise generateResultOrCancel){
        if (isExecutionCanceled(generateResultOrCancel)){
            return Promise.asPromise("canceled");
        }

        SendEbookActivity sendEbookActivity = new SendEbookActivity();
        LOG.trace("Got generate results {}", generateResult.get());

        if (!generateResult.get().success){
            throw new RuntimeException("Error generating ebook " + generateResult.get().error);
        }

        SendRequest sendRequest = mapDeliveryRequestAndGenerateResultToSendRequest(deliveryRequest,
            generateResult.get().key);

        String request = Jackson.toJsonString(sendRequest);
        LOG.trace("Triggering send with {}", request);

        return sendEbookActivity.invoke(request);
    }

    @Asynchronous
    public Promise<String> invokeUpdateDelivery(DeliveryRequest deliveryRequest, OrPromise sendResultOrCancel) {
        if (isExecutionCanceled(sendResultOrCancel)){
            return Promise.asPromise("canceled");
        }

        UpdateDeliveryActivity updateDeliveryActivity = new UpdateDeliveryActivity();
        UpdateRequest updateRequest = mapDeliveryRequestAndSendResultToUpdateRequest(deliveryRequest);

        String request = Jackson.toJsonString(updateRequest);
        LOG.trace("Triggering update with {}", request);

        return updateDeliveryActivity.invoke(request);
    }

    public Promise<String> invokeGenerateActionLinks(DeliveryRequest deliveryRequest){
        GenerateLinksRequest generateLinksRequest = mapDeliveryRequestToGenerateLinksRequest(deliveryRequest);
        if (Jackson.toJsonString(generateLinksRequest).length() > REQUEST_MAX_SIZE){
            String key = storeInS3(Jackson.toJsonString(generateLinksRequest.articles));
            generateLinksRequest.articles = null;
            generateLinksRequest.s3Articles = key;
        }
        String request = Jackson.toJsonString(generateLinksRequest);
        LOG.trace("Triggering generate links with {}", request);
        return LambdaInvoker.invoke("action-api", request);
    }

//    @Asynchronous
//    public Book mapDeliveryRequestAndExtractResultToGenerateRequestAsync
//        (DeliveryRequest request, Promise<String> extractResults){
//
//
//        LOG.error( extractResults.isReady() + "");
//        LOG.error("kakademona");
//        return mapDeliveryRequestAndExtractResultToBook(request, extractResults.get());
//    }
//
//    @Asynchronous
//    public SendRequest mapDeliveryRequestAndGenerateResultToSendRequestAsync
//        (DeliveryRequest request, Promise<GenerateResult> generateResult){
//
//        return mapDeliveryRequestAndGenerateResultToSendRequest(request, generateResult.get());
//    }
//
//    @Asynchronous
//    public UpdateRequest mapDeliveryRequestAndSendResultToUpdateRequestAsync
//        (DeliveryRequest request, Promise<SendResult> sendResult){
//
//        return mapDeliveryRequestAndSendResultToUpdateRequest(request, sendResult.get());
//    }

    private DeliveryRequest deserializeDeliveryRequest(String s) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        DeliveryRequest request = mapper.readValue(s.getBytes("UTF8"), DeliveryRequest.class);

        if (request.s3Items != null){
            GetObjectRequest getObjectRequest = new GetObjectRequest(request.s3Items.bucket, request.s3Items.key);
            S3Object object = s3.getObject(getObjectRequest);

            List<DeliveryItem> items = mapper
                .readValue(IOUtils.toString(object.getObjectContent()).getBytes("UTF8"),
                    new TypeReference<List<DeliveryItem>>(){});

            request.items = items;
        }

        return request;
    }


    @Override
    public void generationFinished(String generateResult) {
        GenerateFinished generateFinished = Jackson.fromJsonString(generateResult, GenerateFinished.class);
        this.generateResult.set(generateFinished);
    }

    @Override
    public void extractionFinished(String extractResult) {
        ExtractFinished extractFinished = Jackson.fromJsonString(extractResult, ExtractFinished.class);
        this.extractResult.set(extractFinished);
    }

    @Override
    public String getState() {
        return state.name();
    }

    @Asynchronous
    public void setState(DeliveryState state, Promise waitFor){
        this.state = state;
    }

    private void setState(DeliveryState state){
        this.state = state;
    }

    protected <T> T mapToOutput(String s, Class<T> clazz){
        if (s == null){
            return null;
        }
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            return mapper.readValue(s.getBytes(), clazz);
        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    protected <T> List<T> mapToOutput(String s, JavaType type){
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            return mapper.readValue(s.getBytes(), type);
        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    private <T> JavaType constructListType(Class<T> clazz){
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        JavaType type = mapper.getTypeFactory().
            constructCollectionType(List.class, clazz);
        return type;
    }

    private boolean isExecutionCanceled(OrPromise promises){
        for (Promise promise : promises.getValues()){
            if (promise != null && promise.isReady() && promise.getDescription() != null &&
                promise.getDescription().equals(CANCEL_EXECUTION_DESCRIPTION)){
                return true;
            }
        }
        return false;
    }

    private boolean isTimeOut(OrPromise promises){
        return getTimeOutTask(promises).isReady();
    }

    private Promise getTimeOutTask(OrPromise promises){
        for (Promise promise : promises.getValues()){
            if (promise.getDescription() != null && promise.getDescription().contains("createTimer")){
                return promise;
            }
        }
        return null;
    }

    private <T> Promise<T> getReadyOne(OrPromise promises){
        for (Promise promise : promises.getValues()){
            if (promise.isReady()){
                return promise;
            }
        }
        return null;
    }
}
