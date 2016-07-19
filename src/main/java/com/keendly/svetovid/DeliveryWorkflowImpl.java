package com.keendly.svetovid;

import static com.keendly.svetovid.DeliveryWorkflowMapper.*;

import com.amazonaws.services.simpleworkflow.flow.DecisionContext;
import com.amazonaws.services.simpleworkflow.flow.DecisionContextProvider;
import com.amazonaws.services.simpleworkflow.flow.DecisionContextProviderImpl;
import com.amazonaws.services.simpleworkflow.flow.annotations.Asynchronous;
import com.amazonaws.services.simpleworkflow.flow.core.Promise;
import com.amazonaws.services.simpleworkflow.flow.core.Settable;
import com.amazonaws.services.simpleworkflow.flow.core.TryCatch;
import com.amazonaws.util.json.Jackson;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keendly.svetovid.activities.extract.ExtractArticlesActivity;
import com.keendly.svetovid.activities.extract.model.ExtractRequest;
import com.keendly.svetovid.activities.extract.model.ExtractResult;
import com.keendly.svetovid.activities.generate.GenerateEbookActivity;
import com.keendly.svetovid.activities.generate.model.Book;
import com.keendly.svetovid.activities.generate.model.TriggerGenerateRequest;
import com.keendly.svetovid.activities.send.SendEbookActivity;
import com.keendly.svetovid.activities.send.model.SendRequest;
import com.keendly.svetovid.model.DeliveryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class DeliveryWorkflowImpl implements DeliveryWorkflow {

    private static final Logger LOG = LoggerFactory.getLogger(DeliveryWorkflowImpl.class);

    private final Settable<String> generateResult = new Settable<>();

    private DeliveryState state;

    @Override
    public void deliver(String requestString) throws IOException {
        setState(DeliveryState.STARTED);

        // map request string to delivery request object
        DeliveryRequest request = deserializeDeliveryRequest(requestString);

        new TryCatch() {
            @Override
            protected void doTry() throws Throwable {
                // extract articles
                ExtractArticlesActivity extractArticlesActivity = new ExtractArticlesActivity();
                ExtractRequest extractRequest = mapDeliveryRequestToExtractArticlesRequest(request);
                Promise<String> extractResults =
                    extractArticlesActivity.invoke(Jackson.toJsonString(extractRequest));

                // generate ebook
                Promise<String> triggerGenerateResult =
                    invokeTriggerGenerate(request, extractResults);

                // send email
                Promise<String> sendEmail =
                    invokeSendEmail(request, generateResult);

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
    public Promise<String> invokeTriggerGenerate(DeliveryRequest deliveryRequest, Promise<String> extractResults){
        GenerateEbookActivity generateEbookActivity = new GenerateEbookActivity();
        LOG.trace("Got extract results {}", extractResults.get());

        JavaType type = constructListType(ExtractResult.class);
        Book book =
            mapDeliveryRequestAndExtractResultToBook(deliveryRequest,
                mapToOutput(extractResults.get(), type));

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
    public Promise<String> invokeSendEmail(DeliveryRequest deliveryRequest, Promise<String> generateResult){
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
        return mapper.readValue(s.getBytes("UTF8"), DeliveryRequest.class);
    }

    @Override
    public void generationFinished(String generateResult) {
        this.generateResult.set(generateResult);
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
}
