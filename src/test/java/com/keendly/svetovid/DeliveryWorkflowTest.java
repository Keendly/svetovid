package com.keendly.svetovid;

import static com.keendly.utils.mock.Helpers.*;

import com.amazonaws.services.simpleworkflow.flow.junit.FlowBlockJUnit4ClassRunner;
import com.amazonaws.services.simpleworkflow.flow.junit.WorkflowTest;
import com.amazonaws.util.json.Jackson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.keendly.svetovid.activities.extract.model.ExtractResult;
import com.keendly.svetovid.model.DeliveryArticle;
import com.keendly.svetovid.model.DeliveryItem;
import com.keendly.svetovid.model.DeliveryRequest;
import com.keendly.utils.mock.LambdaMock;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(FlowBlockJUnit4ClassRunner.class)
public class DeliveryWorkflowTest {

    @Rule
    public WorkflowTest workflowTest = new WorkflowTest();

    private DeliveryWorkflowClientFactory workflowFactory = new DeliveryWorkflowClientFactoryImpl();

    DeliveryWorkflowClient workflow;

    @Before
    public void setUp() throws Exception {
        // Register activity implementation to be used during test invoke
//        BookingActivities activities = new BookingActivitiesImpl(trace);
        workflowTest.addWorkflowImplementationType(DeliveryWorkflowImpl.class);
        workflow = workflowFactory.getClient();

        initInvoker();
//        workflowTest.setDisableOutstandingTasksCheck(true);
    }

//    @After
//    public void tearDown() throws Exception {
//        trace = null;
//    }

    @Test
    public void testDeliver() throws Exception {
        // given
        List<ExtractResult> extractResults = new ArrayList<>();
        ExtractResult extractResult = new ExtractResult();
        extractResult.text = "blabla";
        extractResult.url = "atam";
        extractResults.add(extractResult);

        // when
        LambdaMock mock = lambdaMock("veles");

        List<ExtractResult> results = new ArrayList<>();
        results.add(extractResult);
        whenInvoked(mock).thenSerializeAndReturn(results);



        workflow.deliver(Jackson.toJsonString(getRequest()));
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = mapper.createObjectNode();
        ArrayNode requests = mapper.createArrayNode();


        verifyInvokedWith(mock, "{\"requests\":[{\"url\":\"http://www.fcbarca.com/70699-pedro-nie-pomylilem-sie-odchodzac-z-barcelony.html?utm_source=newsList&utm_campaign=news\",\"withImages\":true,\"withMetadata\":false}]}");
//
//        new Task(promise) {
//            @Override
//            protected void doExecute() throws Throwable {
//                assertEquals("{\"requests\":[{\"url\":\"http://www.fcbarca.com/70699-pedro-nie-pomylilem-sie-odchodzac-z-barcelony.html?utm_source=newsList&utm_campaign=news\",\"withImages\":true,\"withMetadata\":false}]}", lambdaInputs.get("veles"));
//            }
//        };
//        lambdaInputs.put("veles", new Settable<>());
//        AsyncAssert.assertEquals("niwim", lambdaInput("veles"));
        //        System.out.println(lambdaInputs.get("veles"));
    }

//    private Promise<String> lambdaInput(String lambdaName){
//        if (!lambdaInputs.containsKey(lambdaName)){
//            lambdaInputs.put(lambdaName, new Settable<>());
//        }
//        return lambdaInputs.get(lambdaName);
//    }


    public void testDeliver_OLD() throws IOException {
        DeliveryWorkflowClient workflow = workflowFactory.getClient();
//        Promise<Void> booked = workflow.makeBooking(123, 345, true, true);
        workflow.deliver(Jackson.toJsonString(getRequest()));
        List<String> expected = new ArrayList<String>();
        expected.add("reserveCar-123");
        expected.add("reserveAirline-123");
        expected.add("sendConfirmation-345");
//        AsyncAssert.assertEqualsWaitFor("invalid booking", expected, trace, booked);
    }

    private static DeliveryRequest getRequest() throws IOException {
        DeliveryRequest request = new DeliveryRequest();
        DeliveryItem item = new DeliveryItem();

        DeliveryArticle article = new DeliveryArticle();
        article.url = "http://www.fcbarca.com/70699-pedro-nie-pomylilem-sie-odchodzac-z-barcelony.html?utm_source=newsList&utm_campaign=news";

        item.articles  = Collections.singletonList(article);

        item.withImages = Boolean.TRUE;
        request.items = Collections.singletonList(item);

        return request;
    }
//
//    private void mockLambdaCall(String lambdaName, Object output) {
//        lambdaOutputs.put(lambdaName, Jackson.toJsonString(output));
//    }
//
//    private void assertLambdaCalled(String lambdaName, String input) {
//        assertEquals(input, lambdaInputs.get(lambdaName));
//    }
}
