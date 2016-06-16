package com.keendly.svetovid;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient;
import com.amazonaws.services.simpleworkflow.flow.StartWorkflowOptions;
import com.amazonaws.services.simpleworkflow.model.WorkflowExecution;
import com.amazonaws.util.json.Jackson;
import com.keendly.svetovid.model.DeliveryArticle;
import com.keendly.svetovid.model.DeliveryItem;
import com.keendly.svetovid.model.DeliveryRequest;

import java.io.IOException;
import java.util.Collections;

public class WorkflowExecutor {

    public static void main(String[] args) throws Exception {
        AmazonSimpleWorkflow swfService = createSWFClient();
        String domain = "sample";

        DeliveryWorkflowClientExternalFactory clientFactory = new DeliveryWorkflowClientExternalFactoryImpl(swfService, domain);
        DeliveryWorkflowClientExternal workflow = clientFactory.getClient();

        // Start Wrokflow Execution
        StartWorkflowOptions options = new StartWorkflowOptions().withLambdaRole("arn:aws:iam::625416862388:role/swf_lambda_role");
        workflow.deliver(Jackson.toJsonString(getRequest()), options);

        // WorkflowExecution is available after workflow creation
        WorkflowExecution workflowExecution = workflow.getWorkflowExecution();
        System.out.println("Started helloWorld workflow with workflowId=\"" + workflowExecution.getWorkflowId()
            + "\" and runId=\"" + workflowExecution.getRunId() + "\"");
    }

    public static AmazonSimpleWorkflow createSWFClient() {
        //        AWSCredentials awsCredentials = new BasicAWSCredentials();
        AmazonSimpleWorkflow client = new AmazonSimpleWorkflowClient();
        client.setRegion(Region.getRegion(Regions.EU_WEST_1));
        //        client.setEndpoint(this.swfServiceUrl);
        return client;
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
}
