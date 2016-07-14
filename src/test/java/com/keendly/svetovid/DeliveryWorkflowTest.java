package com.keendly.svetovid;

import com.amazonaws.services.simpleworkflow.flow.junit.FlowBlockJUnit4ClassRunner;
import com.amazonaws.services.simpleworkflow.flow.junit.WorkflowTest;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.keendly.utils.mock.LambdaMock;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.eclipsesource.json.Json.*;
import static com.keendly.utils.mock.Helpers.*;

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
                    .add("withImages", TRUE)
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


        LambdaMock veles = lambdaMock("veles");
        LambdaMock jindleTrigger = lambdaMock("jariloTrigger");

        // when
        whenInvoked(veles).thenReturn(extractResults);

        workflow.deliver(deliveryRequest.toString());

        // then
        verifyInvokedWith(veles, object().add("requests", array()
            .add(object()
                .add("url", "http://www.fcbarca.com/70699-pedro-nie-pomylilem-sie-odchodzac-z-barcelony.html")
                .add("withImages", TRUE)
                .add("withMetadata", FALSE))));
    }

    private JsonArray array(){
        return new JsonArray();
    }
}
