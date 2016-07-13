package com.keendly.svetovid;

import java.io.IOException;
import java.util.Collections;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
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

public class WorkflowExecutor {

    public static void main(String[] args) throws Exception {
        AmazonSimpleWorkflow swfService = createSWFClient();

        String domain = "keendly";

        DeliveryWorkflowClientExternalFactory clientFactory = new DeliveryWorkflowClientExternalFactoryImpl(swfService, domain);
        DeliveryWorkflowClientExternal workflow = clientFactory.getClient();

        // Start Wrokflow Execution
        StartWorkflowOptions options = new StartWorkflowOptions().withLambdaRole("arn:aws:iam::625416862388:role/swf_lambda_role");
        workflow.deliver(Jackson.toJsonString(getRequest()), options);

        // WorkflowExecution is available after workflow creation
        WorkflowExecution workflowExecution = workflow.getWorkflowExecution();
        System.out.println("Started helloWorld workflow with workflowId=\"" + workflowExecution.getWorkflowId()
            + "\" and runId=\"" + workflowExecution.getRunId() + "\"");

//
//        Thread.sleep(5000);
//        workflow.generationFinished("ebooks/eb6c2a7d-188d-4f16-a202-4c3836db55f0");
    }

    public static AmazonSimpleWorkflow createSWFClient() {
        //        AWSCredentials awsCredentials = new BasicAWSCredentials();
        AmazonSimpleWorkflow client = new AmazonSimpleWorkflowClient(getCredentials());
        client.setRegion(Region.getRegion(Regions.EU_WEST_1));
//                client.setEndpoint(this.swfServiceUrl);
        return client;
    }

    private static AWSCredentialsProvider getCredentials(){
        ProfileCredentialsProvider credentialsProvider = new ProfileCredentialsProvider("keendly");
        return credentialsProvider;
    }


    private static DeliveryRequest getRequest() throws IOException {
        DeliveryRequest request = new DeliveryRequest();
        DeliveryItem item = new DeliveryItem();

        DeliveryArticle article = new DeliveryArticle();
        article.url = "http://www.fcbarca.com/70699-pedro-nie-pomylilem-sie-odchodzac-z-barcelony.html?utm_source=newsList&utm_campaign=news";
        article.title = "Pedro blabla";

        item.articles  = Collections.singletonList(article);
        item.title = "Section title";

        item.withImages = Boolean.TRUE;
        request.items = Collections.singletonList(item);
        request.email = "moomeen@gmail.com";

        return request;
    }
}
