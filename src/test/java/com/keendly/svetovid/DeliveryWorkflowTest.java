package com.keendly.svetovid;

import static com.eclipsesource.json.Json.*;
import static com.keendly.utils.mock.Helpers.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.simpleworkflow.flow.DecisionContextProvider;
import com.amazonaws.services.simpleworkflow.flow.DecisionContextProviderImpl;
import com.amazonaws.services.simpleworkflow.flow.WorkflowClock;
import com.amazonaws.services.simpleworkflow.flow.core.Promise;
import com.amazonaws.services.simpleworkflow.flow.core.Task;
import com.amazonaws.services.simpleworkflow.flow.junit.FlowBlockJUnit4ClassRunner;
import com.amazonaws.services.simpleworkflow.flow.junit.WorkflowTest;
import com.amazonaws.util.IOUtils;
import com.amazonaws.util.json.Jackson;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.keendly.svetovid.s3.S3ClientHolder;
import com.keendly.svetovid.utils.Config;
import com.keendly.svetovid.utils.MessageKeyGenerator;
import com.keendly.svetovid.utils.WorkflowUtils;
import com.keendly.utils.mock.LambdaMock;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.io.File;
import java.io.InputStream;
import java.util.List;

@RunWith(FlowBlockJUnit4ClassRunner.class)
public class DeliveryWorkflowTest {

    @Rule
    public WorkflowTest workflowTest = new WorkflowTest();

    private DeliveryWorkflowClientFactory workflowFactory = new DeliveryWorkflowClientFactoryImpl();

    private DeliveryWorkflowClient workflow;

    private AmazonS3 amazonS3;
    private WorkflowUtils workflowUtils;
    private MessageKeyGenerator messageKeyGenerator;

    @Before
    public void setUp() throws Exception {
        // create workflow client
        workflowTest.addWorkflowImplementationType(DeliveryWorkflowImpl.class);
        workflow = workflowFactory.getClient();

        // initialize lambda test invoker
        initInvoker();

        initMocks();

        Config.reset();
    }

    private void initMocks(){
        amazonS3 = Mockito.mock(AmazonS3.class);
        S3ClientHolder.set(amazonS3);

        workflowUtils = mock(WorkflowUtils.class);
        WorkflowUtils.set(workflowUtils);

        messageKeyGenerator = mock(MessageKeyGenerator.class);
        MessageKeyGenerator.set(messageKeyGenerator);
    }

    @Test
    public void given_itemsIncluded_when_deliver_then_ok() throws Exception {
        String workflowId = "myWorkflowId";
        String runId = "myRunId";

        String generateMessageKey = "myGenerateMessage";

        // given
        when(workflowUtils.getWorkFlowId()).thenReturn(workflowId);
        when(workflowUtils.getRunId()).thenReturn(runId);

        when(messageKeyGenerator.generate()).thenReturn(generateMessageKey);

        JsonObject deliveryRequest = object()
            .add("id", 1)
            .add("userId", 2)
            .add("provider", "INOREADER")
            .add("email", "contact@keendly.com")
            .add("timestamp", System.currentTimeMillis())
            .add("dryRun", false)
            .add("items", array()
                .add(object()
                    .add("feedId", "feed/http://www.fcbarca.com/feed")
                    .add("title", "FCBarca")
                    .add("includeImages", TRUE)
                    .add("fullArticle", TRUE)
                    .add("markAsRead", FALSE)
                    .add("articles", array()
                        .add(object()
                            .add("id", "1232ca7865")
                            .add("url", "http://www.fcbarca.com/70699-pedro-nie-pomylilem-sie-odchodzac-z-barcelony.html")
                            .add("title", "Pedro: Nie pomyliłem się, odchodząc z Barcelony")
                            .add("timestamp", 1465584508000L)
                            .add("author", "Dariusz Maruszczak")
                            .add("content", "this is the article snippet from the feed")))));

        JsonObject extractFinishedCallback = object()
            .add("success", true)
            .add("key", "messages/blablabla");

        JsonArray extractResults = array()
            .add(
                object()
                    .add("url", "http://www.fcbarca.com/70699-pedro-nie-pomylilem-sie-odchodzac-z-barcelony.html")
                    .add("text", "this is the article text extracted from website"));

        JsonObject actionLinks = object()
            .add("links", object()
                .add("1232ca7865", array()
                    .add(object()
                        .add("action", "mark_as_read")
                        .add("link", "http://api.keendly.com/action?a=blabla"))
                    .add(object()
                        .add("action", "save_article")
                        .add("link", "http://api.keendly.com/action?a=lala"))
                    ));

        JsonObject generateFinishedCallback = object()
            .add("success", true)
            .add("key", "ebooks/86a80e65-02be-480e-81e3-629053f2b66a/keendly.mobi");

        LambdaMock velesTrigger = lambdaMock("veles_trigger");
        LambdaMock jariloTrigger = lambdaMock("jarilo_trigger");
        LambdaMock action = lambdaMock("action-api");
        LambdaMock perun = lambdaMock("perun_swf");
        LambdaMock bylun = lambdaMock("bylun");

        whenInvoked(action).thenReturn(actionLinks);

        mockS3Object("messages/blablabla", extractResults.toString(), amazonS3);

        // when
        workflow.deliver(deliveryRequest.toString());

        new Task(delay(1)) {
            @Override
            protected void doExecute() throws Throwable {
                workflow.extractionFinished(extractFinishedCallback.toString());
            }
        };

        new Task(delay(2)) {
            @Override
            protected void doExecute() throws Throwable {
                workflow.generationFinished(generateFinishedCallback.toString());
            }
        };

        // then
        verifyInvokedWith(velesTrigger, object()
            .add("workflowId", workflowId)
            .add("runId", runId)
            .add("content", array()
            .add(object()
                .add("url", "http://www.fcbarca.com/70699-pedro-nie-pomylilem-sie-odchodzac-z-barcelony.html")
                .add("withImages", TRUE)
                .add("withMetadata", FALSE))));

        JsonObject book = object()
                .add("title", "Keendly Feeds")
                .add("language", "en-GB")
                .add("creator", "Keendly")
                .add("subject", "News")
                .add("sections", array()
                    .add(object()
                        .add("title", "FCBarca")
                        .add("articles", array()
                            .add(object()
                                .add("id", "1232ca7865")
                                .add("url", "http://www.fcbarca.com/70699-pedro-nie-pomylilem-sie-odchodzac-z-barcelony.html")
                                .add("author", "Dariusz Maruszczak")
                                .add("title", "Pedro: Nie pomyliłem się, odchodząc z Barcelony")
                                .add("content", "this is the article text extracted from website")
                                .add("date", 1465584508000L)
                                .add("actions", object()
                                    .add("save_article", "http://api.keendly.com/action?a=lala")
                                    .add("mark_as_read", "http://api.keendly.com/action?a=blabla"))))));

        // after generation triggered, check if correct file got uploaded to S3
        executeAfterInvoked(jariloTrigger, () -> {
            ArgumentCaptor<String> savedCaptor = ArgumentCaptor.forClass(String.class);
            verify(amazonS3).putObject(eq("keendly"), eq(generateMessageKey), savedCaptor.capture());
            JSONAssert.assertEquals(book.toString(), savedCaptor.getValue(), JSONCompareMode.LENIENT);
        });

        verifyInvokedWith(action, object()
            .add("userId", 2)
            .add("provider", "INOREADER")
            .add("articles", object()
                .add("1232ca7865", array()
                    .add(object()
                        .add("title", "Pedro: Nie pomyliłem się, odchodząc z Barcelony")
                        .add("operation", "save_article"))
                    .add(object()
                        .add("title", "Pedro: Nie pomyliłem się, odchodząc z Barcelony")
                        .add("operation", "mark_as_read")))));

        verifyInvokedWith(jariloTrigger, object()
            .add("workflowId", workflowId)
            .add("runId", runId)
            .add("s3Content", generateMessageKey));

        // check send email got triggered with file returned from generation
        verifyInvokedWith(perun, object()
            .add("subject", "Keendly Delivery")
            .add("sender", "kindle@keendly.com")
            .add("recipient", "contact@keendly.com")
            .add("message", "Enjoy!")
            .add("attachment", object()
                .add("bucket", "keendly")
                .add("key", generateFinishedCallback.get("key")))
            .add("dryRun", false));

        // TODO test that 'date' is there too
        verifyInvokedWith(bylun, object()
            .add("userId", 2)
            .add("deliveryId", 1)
            .add("dryRun", false));
    }

    @Test
    public void given_itemsOnS3_when_deliver_then_downloadThem() throws Exception {
        String workflowId = "myWorkflowId";
        String runId = "myRunId";

        String deliveryRequestItemsKey = "myDeliveryRequestKey";
        String generateMessageKey = "myGenerateMessage";
        String extractionResultKey = "myExtractionMessageResultKey";

        // given
        when(workflowUtils.getWorkFlowId()).thenReturn(workflowId);
        when(workflowUtils.getRunId()).thenReturn(runId);

        when(messageKeyGenerator.generate()).thenReturn(generateMessageKey);

        JsonArray deliveryRequestItems = array()
            .add(object()
                .add("feedId", "feed/http://www.fcbarca.com/feed")
                .add("title", "FCBarca")
                .add("includeImages", TRUE)
                .add("fullArticle", TRUE)
                .add("markAsRead", TRUE)
                .add("articles", array()
                    .add(object()
                        .add("id", "123abc")
                        .add("url", "http://www.fcbarca.com/70699-pedro-nie-pomylilem-sie-odchodzac-z-barcelony.html")
                        .add("title", "Pedro: Nie pomyliłem się, odchodząc z Barcelony")
                        .add("timestamp", 1465584508000L)
                        .add("author", "Dariusz Maruszczak")
                        .add("content", "this is the article snippet from the feed"))));

        mockS3Object(deliveryRequestItemsKey, deliveryRequestItems.toString(), amazonS3);

        JsonObject deliveryRequest = object()
            .add("id", 1)
            .add("userId", 2)
            .add("email", "contact@keendly.com")
            .add("timestamp", System.currentTimeMillis())
            .add("s3Items", object()
                .add("bucket", "keendly")
                .add("key", deliveryRequestItemsKey));

        JsonObject extractFinishedCallback = object()
            .add("success", true)
            .add("key", extractionResultKey);

        JsonArray extractResults = array()
            .add(
                object()
                    .add("url", "http://www.fcbarca.com/70699-pedro-nie-pomylilem-sie-odchodzac-z-barcelony.html")
                    .add("text", "this is the article text extracted from website"));

        JsonObject actionLinks = object()
            .add("links", object()
                .add("1232ca7865", array()
                    .add(object()
                        .add("action", "keep_unread")
                        .add("link", "http://api.keendly.com/action?a=blabla"))
                ));

        JsonObject generateFinishedCallback = object()
            .add("success", true)
            .add("key", "ebooks/86a80e65-02be-480e-81e3-629053f2b66a/keendly.mobi");

        LambdaMock action = lambdaMock("action-api");
        LambdaMock velesTrigger = lambdaMock("veles_trigger");
        LambdaMock jariloTrigger = lambdaMock("jarilo_trigger");
        LambdaMock perun = lambdaMock("perun_swf");
        LambdaMock bylun = lambdaMock("bylun");

        whenInvoked(action).thenReturn(actionLinks);

        mockS3Object(extractionResultKey, extractResults.toString(), amazonS3);

        // when
        workflow.deliver(deliveryRequest.toString());

        new Task(delay(1)) {
            @Override
            protected void doExecute() throws Throwable {
                workflow.extractionFinished(extractFinishedCallback.toString());
            }
        };

        new Task(delay(2)) {
            @Override
            protected void doExecute() throws Throwable {
                workflow.generationFinished(generateFinishedCallback.toString());
            }
        };

        // then
        verifyInvokedWith(velesTrigger, object()
            .add("workflowId", workflowId)
            .add("runId", runId)
            .add("content", array()
                .add(object()
                    .add("url", "http://www.fcbarca.com/70699-pedro-nie-pomylilem-sie-odchodzac-z-barcelony.html")
                    .add("withImages", TRUE)
                    .add("withMetadata", FALSE))));

        JsonObject book = object()
            .add("title", "Keendly Feeds")
            .add("language", "en-GB")
            .add("creator", "Keendly")
            .add("subject", "News")
            .add("sections", array()
                .add(object()
                    .add("title", "FCBarca")
                    .add("articles", array()
                        .add(object()
                            .add("id", "123abc")
                            .add("url", "http://www.fcbarca.com/70699-pedro-nie-pomylilem-sie-odchodzac-z-barcelony.html")
                            .add("author", "Dariusz Maruszczak")
                            .add("title", "Pedro: Nie pomyliłem się, odchodząc z Barcelony")
                            .add("content", "this is the article text extracted from website")
                            .add("date", 1465584508000L)))));

        // after generation triggered, check if correct file got uploaded to S3
        executeAfterInvoked(jariloTrigger, () -> {
            ArgumentCaptor<String> savedCaptor = ArgumentCaptor.forClass(String.class);
            verify(amazonS3).putObject(eq("keendly"), eq(generateMessageKey), savedCaptor.capture());
            JSONAssert.assertEquals(book.toString(), savedCaptor.getValue(), JSONCompareMode.LENIENT);
        });
    }

    @Test
    public void given_extractRequestTooLong_when_deliver_then_storeInS3() throws Exception {
        String workflowId = "myWorkflowId";
        String runId = "myRunId";

        String deliveryRequestItemsKey = "myDeliveryRequestKey";
        String extractMessageKey = "myExtractMessage";
        String generateMessageKey = "myGenerateMessage";
        String extractionResultKey = "myExtractionMessageResultKey";

        // given
        when(workflowUtils.getWorkFlowId()).thenReturn(workflowId);
        when(workflowUtils.getRunId()).thenReturn(runId);

        when(messageKeyGenerator.generate()).thenReturn(extractMessageKey, generateMessageKey);

        InputStream deliveryRequestItems =
            this.getClass().getResourceAsStream(File.separator + "very_long_items_list.json");
        mockS3Object(deliveryRequestItemsKey, IOUtils.toString(deliveryRequestItems), amazonS3);

        JsonObject deliveryRequest = object()
            .add("id", 1)
            .add("userId", 2)
            .add("email", "contact@keendly.com")
            .add("timestamp", System.currentTimeMillis())
            .add("s3Items", object()
                .add("bucket", "keendly")
                .add("key", deliveryRequestItemsKey));

        JsonObject extractFinishedCallback = object()
            .add("success", true)
            .add("key", extractionResultKey);

        JsonArray extractResults = array()
            .add(
                object()
                    .add("url", "http://www.fcbarca.com/70699-pedro-nie-pomylilem-sie-odchodzac-z-barcelony.html")
                    .add("text", "this is the article text extracted from website"));

        JsonObject generateFinishedCallback = object()
            .add("success", true)
            .add("key", "ebooks/86a80e65-02be-480e-81e3-629053f2b66a/keendly.mobi");

        JsonObject actionLinks = object()
            .add("links", object()
                .add("1232ca7865", array()
                    .add(object()
                        .add("action", "keep_unread")
                        .add("link", "http://api.keendly.com/action?a=blabla"))
                ));

        LambdaMock action = lambdaMock("action-api");
        LambdaMock velesTrigger = lambdaMock("veles_trigger");
        LambdaMock jariloTrigger = lambdaMock("jarilo_trigger");
        LambdaMock perun = lambdaMock("perun_swf");
        LambdaMock bylun = lambdaMock("bylun");


        mockS3Object(extractionResultKey, extractResults.toString(), amazonS3);

        whenInvoked(action).thenReturn(actionLinks);

        // when
        workflow.deliver(deliveryRequest.toString());

        new Task(delay(1)) {
            @Override
            protected void doExecute() throws Throwable {
                workflow.extractionFinished(extractFinishedCallback.toString());
            }
        };

        new Task(delay(2)) {
            @Override
            protected void doExecute() throws Throwable {
                workflow.generationFinished(generateFinishedCallback.toString());
            }
        };

        // then
        verifyInvokedWith(velesTrigger, object()
            .add("workflowId", workflowId)
            .add("runId", runId)
            .add("s3Content", extractMessageKey));

        executeAfterInvoked(velesTrigger, () -> {
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(amazonS3).putObject(eq("keendly"), eq(extractMessageKey), captor.capture());
            assertEquals(487, Jackson.fromJsonString(captor.getValue(), List.class).size());
        });
    }

    @Test
    public void given_extractionTimeout_when_deliver_then_continue() throws Exception {
        String workflowId = "myWorkflowId";
        String runId = "myRunId";

        String generateMessageKey = "myGenerateMessage";

        // given
        when(workflowUtils.getWorkFlowId()).thenReturn(workflowId);
        when(workflowUtils.getRunId()).thenReturn(runId);

        when(messageKeyGenerator.generate()).thenReturn(generateMessageKey);

        // timeout extraction after 1 sec
        Config.setExtractionTimeout(1);

        JsonObject deliveryRequest = object()
            .add("id", 1)
            .add("userId", 2)
            .add("email", "contact@keendly.com")
            .add("timestamp", System.currentTimeMillis())
            .add("dryRun", false)
            .add("items", array()
                .add(object()
                    .add("feedId", "feed/http://www.fcbarca.com/feed")
                    .add("title", "FCBarca")
                    .add("includeImages", TRUE)
                    .add("fullArticle", TRUE)
                    .add("markAsRead", TRUE)
                    .add("articles", array()
                        .add(object()
                            .add("id", "1232ca7865")
                            .add("url", "http://www.fcbarca.com/70699-pedro-nie-pomylilem-sie-odchodzac-z-barcelony.html")
                            .add("title", "Pedro: Nie pomyliłem się, odchodząc z Barcelony")
                            .add("timestamp", 1465584508000L)
                            .add("author", "Dariusz Maruszczak")
                            .add("content", "this is the article snippet from the feed")))));

        JsonObject generateFinishedCallback = object()
            .add("success", true)
            .add("key", "ebooks/86a80e65-02be-480e-81e3-629053f2b66a/keendly.mobi");

        JsonObject actionLinks = object()
            .add("links", object()
                .add("1232ca7865", array()
                    .add(object()
                        .add("action", "keep_unread")
                        .add("link", "http://api.keendly.com/action?a=blabla"))
                ));

        LambdaMock velesTrigger = lambdaMock("veles_trigger");
        LambdaMock jariloTrigger = lambdaMock("jarilo_trigger");
        LambdaMock action = lambdaMock("action-api");
        LambdaMock perun = lambdaMock("perun_swf");
        LambdaMock bylun = lambdaMock("bylun");

        whenInvoked(action).thenReturn(actionLinks);

        // when
        workflow.deliver(deliveryRequest.toString());

        new Task(delay(2)) {
            @Override
            protected void doExecute() throws Throwable {
                workflow.generationFinished(generateFinishedCallback.toString());
            }
        };

        // then
        verifyInvokedWith(velesTrigger, object()
            .add("workflowId", workflowId)
            .add("runId", runId)
            .add("content", array()
                .add(object()
                    .add("url", "http://www.fcbarca.com/70699-pedro-nie-pomylilem-sie-odchodzac-z-barcelony.html")
                    .add("withImages", TRUE)
                    .add("withMetadata", FALSE))));

        JsonObject book = object()
            .add("title", "Keendly Feeds")
            .add("language", "en-GB")
            .add("creator", "Keendly")
            .add("subject", "News")
            .add("sections", array()
                .add(object()
                    .add("title", "FCBarca")
                    .add("articles", array()
                        .add(object()
                            .add("id", "1232ca7865")
                            .add("url", "http://www.fcbarca.com/70699-pedro-nie-pomylilem-sie-odchodzac-z-barcelony.html")
                            .add("author", "Dariusz Maruszczak")
                            .add("title", "Pedro: Nie pomyliłem się, odchodząc z Barcelony")
                            .add("content", "this is the article snippet from the feed")
                            .add("date", 1465584508000L)))));

        // after generation triggered, check if correct file got uploaded to S3
        executeAfterInvoked(jariloTrigger, () -> {
            ArgumentCaptor<String> savedCaptor = ArgumentCaptor.forClass(String.class);
            verify(amazonS3).putObject(eq("keendly"), eq(generateMessageKey), savedCaptor.capture());
            JSONAssert.assertEquals(book.toString(), savedCaptor.getValue(), JSONCompareMode.LENIENT);
        });

        verifyInvokedWith(jariloTrigger, object()
            .add("workflowId", workflowId)
            .add("runId", runId)
            .add("s3Content", generateMessageKey));
    }

    @Test
    @Ignore
    public void given_extractionError_when_deliver_then_retry() throws Exception {
        // TODO to be implemented
    }

    @Test
    @Ignore
    public void given_generationError_when_deliver_then_retry() throws Exception {
        // TODO to be implemented
    }

    @Test
    @Ignore
    public void given_generateRequestTooLong_when_deliver_then_storeInS3() throws Exception {
        // TODO to be implemented, so far always stores in S3
        // should be same as for extraction, only if msg is too big
    }

    @Test
    public void given_noArticles_when_deliver_then_cancel() throws Exception {
        // given
        JsonObject deliveryRequest = object()
            .add("id", 1)
            .add("userId", 2)
            .add("email", "contact@keendly.com")
            .add("timestamp", System.currentTimeMillis())
            .add("items", array()
                .add(object()
                    .add("feedId", "feed/http://www.fcbarca.com/feed")
                    .add("title", "FCBarca")
                    .add("includeImages", TRUE)
                    .add("fullArticle", TRUE)
                    .add("markAsRead", TRUE)));


        LambdaMock veles = lambdaMock("veles");

        // when
        workflow.deliver(deliveryRequest.toString());

        // then
        verifyNotInvoked(veles);
    }

    @Test
    public void given_dryRun_when_deliver_then_dryRun() throws Exception {
        String workflowId = "myWorkflowId";
        String runId = "myRunId";

        String generateMessageKey = "myGenerateMessage";

        // given
        when(workflowUtils.getWorkFlowId()).thenReturn(workflowId);
        when(workflowUtils.getRunId()).thenReturn(runId);

        when(messageKeyGenerator.generate()).thenReturn(generateMessageKey);

        JsonObject deliveryRequest = object()
            .add("id", 1)
            .add("userId", 2)
            .add("email", "contact@keendly.com")
            .add("timestamp", System.currentTimeMillis())
            .add("dryRun", true)
            .add("items", array()
                .add(object()
                    .add("feedId", "feed/http://www.fcbarca.com/feed")
                    .add("title", "FCBarca")
                    .add("includeImages", TRUE)
                    .add("fullArticle", TRUE)
                    .add("markAsRead", TRUE)
                    .add("articles", array()
                        .add(object()
                            .add("id", "123abc")
                            .add("url", "http://www.fcbarca.com/70699-pedro-nie-pomylilem-sie-odchodzac-z-barcelony.html")
                            .add("title", "Pedro: Nie pomyliłem się, odchodząc z Barcelony")
                            .add("timestamp", 1465584508000L)
                            .add("author", "Dariusz Maruszczak")
                            .add("content", "this is the article snippet from the feed")))));

        JsonObject extractFinishedCallback = object()
            .add("success", true)
            .add("key", "messages/blablabla");

        JsonArray extractResults = array()
            .add(
                object()
                    .add("url", "http://www.fcbarca.com/70699-pedro-nie-pomylilem-sie-odchodzac-z-barcelony.html")
                    .add("text", "this is the article text extracted from website"));

        JsonObject generateFinishedCallback = object()
            .add("success", true)
            .add("key", "ebooks/86a80e65-02be-480e-81e3-629053f2b66a/keendly.mobi");

        JsonObject actionLinks = object()
            .add("links", object());

        LambdaMock velesTrigger = lambdaMock("veles_trigger");
        LambdaMock jariloTrigger = lambdaMock("jarilo_trigger");
        LambdaMock action = lambdaMock("action-api");
        LambdaMock perun = lambdaMock("perun_swf");
        LambdaMock bylun = lambdaMock("bylun");

        mockS3Object("messages/blablabla", extractResults.toString(), amazonS3);

        whenInvoked(action).thenReturn(actionLinks);

        // when
        workflow.deliver(deliveryRequest.toString());

        new Task(delay(1)) {
            @Override
            protected void doExecute() throws Throwable {
                workflow.extractionFinished(extractFinishedCallback.toString());
            }
        };

        new Task(delay(2)) {
            @Override
            protected void doExecute() throws Throwable {
                workflow.generationFinished(generateFinishedCallback.toString());
            }
        };

        // then

        // check send email got triggered with file returned from generation
        verifyInvokedWith(perun, object()
            .add("subject", "Keendly Delivery")
            .add("sender", "kindle@keendly.com")
            .add("recipient", "contact@keendly.com")
            .add("message", "Enjoy!")
            .add("attachment", object()
                .add("bucket", "keendly")
                .add("key", generateFinishedCallback.get("key")))
            .add("dryRun", true));

        // verify no action links created
        verifyInvokedWith(action, object()
            .add("articles", object()));

        // TODO test that 'date' is there too
        verifyInvokedWith(bylun, object()
            .add("userId", 2)
            .add("deliveryId", 1)
            .add("dryRun", true));
    }

    @Test
    public void given_actionLinksOnS3_when_deliver_then_downloadThem() throws Exception {
        String workflowId = "myWorkflowId";
        String runId = "myRunId";

        String actionLinksKey = "myActionLinksKey";
        String generateMessageKey = "myGenerateMessage";

        // given
        when(workflowUtils.getWorkFlowId()).thenReturn(workflowId);
        when(workflowUtils.getRunId()).thenReturn(runId);

        when(messageKeyGenerator.generate()).thenReturn(generateMessageKey);

        JsonObject deliveryRequest = object()
            .add("id", 1)
            .add("userId", 2)
            .add("email", "contact@keendly.com")
            .add("timestamp", System.currentTimeMillis())
            .add("dryRun", false)
            .add("items", array()
                .add(object()
                    .add("feedId", "feed/http://www.fcbarca.com/feed")
                    .add("title", "FCBarca")
                    .add("includeImages", TRUE)
                    .add("fullArticle", TRUE)
                    .add("markAsRead", TRUE)
                    .add("articles", array()
                        .add(object()
                            .add("id", "1232ca7865")
                            .add("url", "http://www.fcbarca.com/70699-pedro-nie-pomylilem-sie-odchodzac-z-barcelony.html")
                            .add("title", "Pedro: Nie pomyliłem się, odchodząc z Barcelony")
                            .add("timestamp", 1465584508000L)
                            .add("author", "Dariusz Maruszczak")
                            .add("content", "this is the article snippet from the feed")))));

        JsonObject extractFinishedCallback = object()
            .add("success", true)
            .add("key", "messages/blablabla");

        JsonArray extractResults = array()
            .add(
                object()
                    .add("url", "http://www.fcbarca.com/70699-pedro-nie-pomylilem-sie-odchodzac-z-barcelony.html")
                    .add("text", "this is the article text extracted from website"));

        JsonObject actionLinksList = object()
            .add("1232ca7865", array()
                .add(object()
                    .add("action", "keep_unread")
                    .add("link", "http://api.keendly.com/action?a=blabla")));

        mockS3Object(actionLinksKey, actionLinksList.toString(), amazonS3);

        JsonObject actionLinks = object()
            .add("s3Links", object()
                .add("bucket", "keendly")
                .add("key", actionLinksKey));

        JsonObject generateFinishedCallback = object()
            .add("success", true)
            .add("key", "ebooks/86a80e65-02be-480e-81e3-629053f2b66a/keendly.mobi");

        LambdaMock velesTrigger = lambdaMock("veles_trigger");
        LambdaMock jariloTrigger = lambdaMock("jarilo_trigger");
        LambdaMock action = lambdaMock("action-api");
        LambdaMock perun = lambdaMock("perun_swf");
        LambdaMock bylun = lambdaMock("bylun");

        whenInvoked(action).thenReturn(actionLinks);

        mockS3Object("messages/blablabla", extractResults.toString(), amazonS3);

        // when
        workflow.deliver(deliveryRequest.toString());

        new Task(delay(1)) {
            @Override
            protected void doExecute() throws Throwable {
                workflow.extractionFinished(extractFinishedCallback.toString());
            }
        };

        new Task(delay(2)) {
            @Override
            protected void doExecute() throws Throwable {
                workflow.generationFinished(generateFinishedCallback.toString());
            }
        };

        // then
        JsonObject book = object()
            .add("title", "Keendly Feeds")
            .add("language", "en-GB")
            .add("creator", "Keendly")
            .add("subject", "News")
            .add("sections", array()
                .add(object()
                    .add("title", "FCBarca")
                    .add("articles", array()
                        .add(object()
                            .add("id", "1232ca7865")
                            .add("url", "http://www.fcbarca.com/70699-pedro-nie-pomylilem-sie-odchodzac-z-barcelony.html")
                            .add("author", "Dariusz Maruszczak")
                            .add("title", "Pedro: Nie pomyliłem się, odchodząc z Barcelony")
                            .add("content", "this is the article text extracted from website")
                            .add("date", 1465584508000L)
                            .add("actions", object()
                                .add("keep_unread", "http://api.keendly.com/action?a=blabla"))))));

        // after generation triggered, check if correct file got uploaded to S3
        executeAfterInvoked(jariloTrigger, () -> {
            ArgumentCaptor<String> savedCaptor = ArgumentCaptor.forClass(String.class);
            verify(amazonS3).putObject(eq("keendly"), eq(generateMessageKey), savedCaptor.capture());
            JSONAssert.assertEquals(book.toString(), savedCaptor.getValue(), JSONCompareMode.LENIENT);
        });
    }

    private JsonArray array(){
        return new JsonArray();
    }

    private static Promise<Void> delay(int seconds){

        DecisionContextProvider contextProvider
            = new DecisionContextProviderImpl();

        WorkflowClock clock
            = contextProvider.getDecisionContext().getWorkflowClock();
        return clock.createTimer(seconds);
    }

}
