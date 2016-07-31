package com.keendly.svetovid;

import static com.eclipsesource.json.Json.*;
import static com.keendly.utils.mock.Helpers.*;

import com.amazonaws.services.simpleworkflow.flow.DecisionContextProvider;
import com.amazonaws.services.simpleworkflow.flow.DecisionContextProviderImpl;
import com.amazonaws.services.simpleworkflow.flow.WorkflowClock;
import com.amazonaws.services.simpleworkflow.flow.core.Promise;
import com.amazonaws.services.simpleworkflow.flow.core.Task;
import com.amazonaws.services.simpleworkflow.flow.junit.FlowBlockJUnit4ClassRunner;
import com.amazonaws.services.simpleworkflow.flow.junit.WorkflowTest;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.keendly.utils.mock.LambdaMock;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(FlowBlockJUnit4ClassRunner.class)
public class DeliveryWorkflowTest {

    @Rule
    public WorkflowTest workflowTest = new WorkflowTest();

    private DeliveryWorkflowClientFactory workflowFactory = new DeliveryWorkflowClientFactoryImpl();

    private DeliveryWorkflowClient workflow;

    @Before
    public void setUp() throws Exception {
        // create workflow client
        workflowTest.addWorkflowImplementationType(DeliveryWorkflowImpl.class);
        workflow = workflowFactory.getClient();

        // initialize lambda test invoker
        initInvoker();
    }

    @Test
    public void testDeliver() throws Exception {
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
                    .add("markAsRead", TRUE)
                    .add("articles", array()
                        .add(object()
                            .add("url", "http://www.fcbarca.com/70699-pedro-nie-pomylilem-sie-odchodzac-z-barcelony.html")
                            .add("title", "Pedro: Nie pomyliłem się, odchodząc z Barcelony")
                            .add("timestamp", 1465584508000L)
                            .add("author", "Dariusz Maruszczak")
                            .add("content", "this is the article snippet from the feed")))));

        JsonArray extractResults = array()
            .add(
                object()
                    .add("url", "http://www.fcbarca.com/70699-pedro-nie-pomylilem-sie-odchodzac-z-barcelony.html")
                    .add("text", "this is the article text extracted from website"));

        String generateFinishedCallback
            = "ebooks/86a80e65-02be-480e-81e3-629053f2b66a/keendly.mobi";


        LambdaMock veles = lambdaMock("veles");
        LambdaMock jariloTrigger = lambdaMock("jariloTrigger");
        LambdaMock perun = lambdaMock("perun_swf");

        whenInvoked(veles).thenReturn(extractResults);

        // when
        workflow.deliver(deliveryRequest.toString());

        new Task(delay(1)) {
            @Override
            protected void doExecute() throws Throwable {
                workflow.generationFinished(generateFinishedCallback);
            }
        };

        // then
        verifyInvokedWith(veles, object().add("requests", array()
            .add(object()
                .add("url", "http://www.fcbarca.com/70699-pedro-nie-pomylilem-sie-odchodzac-z-barcelony.html")
                .add("withImages", TRUE)
                .add("withMetadata", FALSE))));

        verifyInvokedWith(jariloTrigger, object()
            .add("content", object()
                .add("title", "Keendly Feeds")
                .add("language", "en-GB")
                .add("creator", "Keendly")
                .add("subject", "News")
                .add("sections", array()
                    .add(object()
                        .add("title", "FCBarca")
                        .add("articles", array()
                            .add(object()
                                .add("url", "http://www.fcbarca.com/70699-pedro-nie-pomylilem-sie-odchodzac-z-barcelony.html")
                                .add("author", "Dariusz Maruszczak")
                                .add("title", "Pedro: Nie pomyliłem się, odchodząc z Barcelony")
                                .add("content", "this is the article text extracted from website")
                                .add("date", 1465584508000L)))))));

        verifyInvokedWith(perun, object()
            .add("subject", "Keendly Delivery")
            .add("sender", "kindle@keendly.com")
            .add("recipient", "contact@keendly.com")
            .add("message", "Enjoy!")
            .add("attachment", object()
                .add("bucket", "keendly")
                .add("key", generateFinishedCallback)));
    }

    @Test
    public void testDeliver_noArticles() throws Exception {
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
