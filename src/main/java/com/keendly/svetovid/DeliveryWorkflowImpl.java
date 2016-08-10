package com.keendly.svetovid;

import static com.keendly.svetovid.DeliveryWorkflowMapper.*;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.simpleworkflow.flow.DecisionContext;
import com.amazonaws.services.simpleworkflow.flow.DecisionContextProvider;
import com.amazonaws.services.simpleworkflow.flow.DecisionContextProviderImpl;
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
import com.keendly.svetovid.activities.generate.model.TriggerGenerateRequest;
import com.keendly.svetovid.activities.send.SendEbookActivity;
import com.keendly.svetovid.activities.send.model.SendRequest;
import com.keendly.svetovid.model.DeliveryItem;
import com.keendly.svetovid.model.DeliveryRequest;
import com.keendly.svetovid.s3.S3ClientHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class DeliveryWorkflowImpl implements DeliveryWorkflow {

    private static final Logger LOG = LoggerFactory.getLogger(DeliveryWorkflowImpl.class);
    private static final String CANCEL_EXECUTION_DESCRIPTION = "cancelExecution";

    private final Settable<String> generateResult = new Settable<>();
    private final Settable<ExtractFinished> extractResult = new Settable<>();

    private final AmazonS3 s3 = S3ClientHolder.get();

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

                Optional<ExtractRequest> extractRequest = mapDeliveryRequestToExtractArticlesRequest(request);
                if (!extractRequest.isPresent()){
                    cancelExecution.set(Boolean.TRUE);
                    return;
                }

                extractRequest.get().runId = getRunId();
                extractRequest.get().workflowId = getWorkFlowId();
                Promise<String> triggerExtractResult =
                    extractArticlesActivity.invoke(Jackson.toJsonString(extractRequest.get()));

                // generate ebook
                Promise<String> triggerGenerateResult =
                    invokeTriggerGenerate(request, new OrPromise(extractResult, cancelExecution));

                // send email
                Promise<String> sendEmail =
                    invokeSendEmail(request, new OrPromise(generateResult, cancelExecution));

//                // update delivery
//                UpdateDeliveryActivity updateDeliveryActivity = new UpdateDeliveryActivity();
//                Promise<UpdateResult> updateResult =
//                    updateDeliveryActivity.invoke(
//                        mapDeliveryRequestAndSendResultToUpdateRequestAsync(request, sendResult));
//
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

    @Asynchronous
    public Promise<String> invokeTriggerGenerate(DeliveryRequest deliveryRequest, OrPromise extractResultsOrCancel)
        throws IOException {
        if (isExecutionCanceled(extractResultsOrCancel)){
            return Promise.asPromise("canceled");
        }

        Promise<ExtractFinished> extractFinished = getReadyOne(extractResultsOrCancel);

        GenerateEbookActivity generateEbookActivity = new GenerateEbookActivity();
        LOG.trace("Got extract results {}", extractFinished.get());

        if (!extractFinished.get().success){
            LOG.error("Error during extracting");
            throw new RuntimeException("Error during extraction: " +extractFinished.get().error);
        }

        // fetch extract results from S3
        GetObjectRequest getObjectRequest = new GetObjectRequest("keendly", extractFinished.get().key);
        S3Object object = s3.getObject(getObjectRequest);

        JavaType type = constructListType(ExtractResult.class);
        Book book =
            mapDeliveryRequestAndExtractResultToBook(deliveryRequest,
                mapToOutput(IOUtils.toString(object.getObjectContent()), type));

        TriggerGenerateRequest triggerGenerateRequest = new TriggerGenerateRequest();
        triggerGenerateRequest.content = book;
        triggerGenerateRequest.runId = getRunId();
        triggerGenerateRequest.workflowId = getWorkFlowId();

        String request = Jackson.toJsonString(triggerGenerateRequest);
        LOG.trace("Triggering generate with {}", request);

//        Promise<String> triggerResponse =
//            generateEbookActivity.invoke(Jackson.toJsonString(request));

        Promise<String> triggerResponse =
                    generateEbookActivity.invoke(Jackson.toJsonString(triggerGenerateRequest));


        return triggerResponse;
    }

    private String getWorkFlowId(){
        DecisionContext context = getContext();
        return context.getWorkflowContext().getWorkflowExecution().getWorkflowId();
    }

    private String getRunId(){
        DecisionContext context = getContext();
        return context.getWorkflowContext().getWorkflowExecution().getRunId();
    }

    private DecisionContext getContext(){
        DecisionContextProvider contextProvider
            = new DecisionContextProviderImpl();
        return contextProvider.getDecisionContext();
    }

    @Asynchronous
    public Promise<String> invokeSendEmail(DeliveryRequest deliveryRequest, OrPromise generateResultOrCancel){
        if (isExecutionCanceled(generateResultOrCancel)){
            return Promise.asPromise("canceled");
        }

        SendEbookActivity sendEbookActivity = new SendEbookActivity();
        LOG.trace("Got generate results {}", generateResult.get());

        if (generateResult.get().contains("ERROR")){
            throw new RuntimeException("Error generating ebook");
        }

        SendRequest sendRequest = mapDeliveryRequestAndGenerateResultToSendRequest(deliveryRequest,
            generateResult.get());

        String request = Jackson.toJsonString(sendRequest);
        LOG.trace("Triggering send with {}", request);

        return sendEbookActivity.invoke(Jackson.toJsonString(sendRequest));
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
        this.generateResult.set(generateResult);
    }

    @Override
    public void extractionFinished(ExtractFinished extractFinished) {
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

    private <T> Promise<T> getReadyOne(OrPromise promises){
        for (Promise promise : promises.getValues()){
            if (promise.isReady()){
                return promise;
            }
        }
        return null;
    }
}
