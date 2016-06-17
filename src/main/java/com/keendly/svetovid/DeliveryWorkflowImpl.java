package com.keendly.svetovid;

import com.amazonaws.services.simpleworkflow.flow.DecisionContext;
import com.amazonaws.services.simpleworkflow.flow.DecisionContextProvider;
import com.amazonaws.services.simpleworkflow.flow.DecisionContextProviderImpl;
import com.amazonaws.services.simpleworkflow.flow.annotations.Asynchronous;
import com.amazonaws.services.simpleworkflow.flow.core.Promise;
import com.amazonaws.services.simpleworkflow.flow.core.Settable;
import com.amazonaws.services.simpleworkflow.flow.worker.LambdaFunctionClient;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keendly.svetovid.activities.extract.ExtractArticlesActivity;
import com.keendly.svetovid.activities.extract.model.ExtractRequest;
import com.keendly.svetovid.activities.extract.model.ExtractResult;
import com.keendly.svetovid.model.DeliveryArticle;
import com.keendly.svetovid.model.DeliveryItem;
import com.keendly.svetovid.model.DeliveryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DeliveryWorkflowImpl implements DeliveryWorkflow {

    private static final Logger LOG = LoggerFactory.getLogger(DeliveryWorkflowImpl.class);
    private static final String BUCKET = "deliveries";
    private static final String GENERATE_QUEUE_URL =
        "https://sqs.eu-west-1.amazonaws.com/625416862388/generation-queue";

//    private static AmazonSQSClient amazonSQSClient = new AmazonSQSClient();

    private final Settable<String> ebookFile = new Settable<>();

    private DeliveryState state;

    @Override
    public Promise<String> deliver(String requestString) throws IOException {
        setState(DeliveryState.STARTED);

        // map request string to delivery request object
        DeliveryRequest request = convert(requestString);

        // extract articles
        ExtractArticlesActivity extractArticlesActivity = new ExtractArticlesActivity();
        Promise<String> resultPromise = runExtraction(extractArticlesActivity, request);
//        List<ExtractResult> extractResults = mapExtractionResult(extractArticlesActivity, resultPromise);
        setState(extractArticlesActivity.getCompletedState(), resultPromise);
        return resultPromise;
//
//        logAsync(resultPromise);
//
//        triggerEbookGeneration(request, articlesPromise);
//        state(State.GENERATION_TRIGGERED);
//
//        Promise<String> sendPromise = send(ebookFile, request.getEmail());
//        state(State.SENT, sendPromise);
//
//        Promise<String> updatePromise = update(request, sendPromise);
//        state(State.FINISHED, updatePromise);
    }

    private Promise<String> runExtraction(ExtractArticlesActivity activity, DeliveryRequest deliveryRequest){
        List<ExtractRequest> extractRequests = mapToExtractRequests(deliveryRequest);
        DecisionContextProvider decisionProvider = new DecisionContextProviderImpl();
        DecisionContext decisionContext = decisionProvider.getDecisionContext();
        LambdaFunctionClient lambdaClient = decisionContext.getLambdaFunctionClient();
        return lambdaClient.scheduleLambdaFunction("veles", activity.mapInput(extractRequests));
//        return activity.invoke(extractRequests);
    }

    @Asynchronous
    private List<ExtractResult> mapExtractionResult(ExtractArticlesActivity activity, Promise<String> resultPromise)
        throws IOException {
        return activity.mapOutput(resultPromise.get());
    }

    private List<ExtractRequest> mapToExtractRequests(DeliveryRequest request){
        List<ExtractRequest> extractRequests = new ArrayList<>();
        for (DeliveryItem item : request.items){
            for (DeliveryArticle article : item.articles){
                ExtractRequest extractRequest = new ExtractRequest();
                extractRequest.url = article.url;
                extractRequest.withImages = item.withImages;
                extractRequest.withMetadata = Boolean.FALSE;
                extractRequests.add(extractRequest);
            }
        }
        return extractRequests;
    }

    @Asynchronous
    private void logAsync(Promise<String> text){
        LOG.debug(text.get());
    }

    private DeliveryRequest convert(String s) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(s.getBytes("UTF8"), DeliveryRequest.class);
    }

//    /**
//     * Triggers ebook generation
//     */
//    @Asynchronous
//    public void triggerEbookGeneration(DeliveryRequest request, Promise<String> articles) throws IOException {
//        Book book = toBook(request, articles);
//        SendMessageRequest sendMessageRequest = SQSUtils.message(Jackson.toJsonString(book),
//            new SQSUtils.Attribute("content", "inline"));
//        sendMessageRequest.setQueueUrl(GENERATE_QUEUE_URL);
//
//        amazonSQSClient.sendMessage(sendMessageRequest);
//    }
//
//    private Book toBook(DeliveryRequest request, Promise<String> articles) throws IOException {
//        List<ExtractResult> extractResults = fromJson(articles.get());
//        return BookMapper.toBook(request, extractResults);
//    }

    private List<ExtractResult> fromJson(String json){
        ObjectMapper mapper = new ObjectMapper();
        JavaType type = mapper.getTypeFactory().
            constructCollectionType(List.class, ExtractResult.class);
        try {
            return mapper.readValue(json, type);
        } catch (IOException e) {
            LOG.error("Error deserializing: " + json, e);
            return null;
        }
    }

    @Override
    public void ebookGenerated(String key) {
        ebookFile.set(key);
    }

//    /**
//     * Sends generated ebook by email
//     */
//    @Asynchronous
//    public Promise<String> send(Promise<String> ebookFile, String email) {
//        SendRequest request = new SendRequest();
//        request.setMessage("Enjoy!");
//        request.setRecipient(email);
//        request.setSender("kindle@keendly.com");
//        request.setSubject("Keendly Delivery");
//        Attachment attachment = new Attachment();
//        attachment.setBucket(BUCKET);
//        attachment.setKey(ebookFile.get());
//        request.setAttachment(attachment);
//
//        return callLambda("perun_java", Jackson.toJsonString(request));
//    }
//
//    /**
//     * Updates delivery with send date
//     */
//    @Asynchronous
//    public Promise<String> update(DeliveryRequest deliveryRequest, Promise<String> waitFor) {
//        SendResult sendResult = Jackson.fromJsonString(waitFor.get(), SendResult.class);
//
//        UpdateRequest request = new UpdateRequest();
//        request.setDeliveryId(deliveryRequest.getId());
//        request.setUserId(deliveryRequest.getUserId());
//        request.setDate(sendResult.getDate());
//        request.setStatus(sendResult.getStatus().name());
//        request.setErrorDescription(sendResult.getErrorDescription());
//
//        return callLambda("bylun", Jackson.toJsonString(request));
//    }

    @Override
    public String getState() {
        return state.name();
    }

    @Asynchronous
    private void setState(DeliveryState state, Promise waitFor){
        this.state = state;
    }

    private void setState(DeliveryState state){
        this.state = state;
    }

}