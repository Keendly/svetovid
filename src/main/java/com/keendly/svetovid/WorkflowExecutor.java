package com.keendly.svetovid;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient;
import com.amazonaws.services.simpleworkflow.model.ListWorkflowTypesRequest;
import com.amazonaws.services.simpleworkflow.model.RegistrationStatus;
import com.amazonaws.services.simpleworkflow.model.Run;
import com.amazonaws.services.simpleworkflow.model.StartWorkflowExecutionRequest;
import com.amazonaws.services.simpleworkflow.model.WorkflowType;
import com.amazonaws.services.simpleworkflow.model.WorkflowTypeInfo;
import com.amazonaws.util.json.Jackson;
import com.keendly.svetovid.model.DeliveryArticle;
import com.keendly.svetovid.model.DeliveryItem;
import com.keendly.svetovid.model.DeliveryRequest;

public class WorkflowExecutor {

    private static final String DOMAIN = "keendly";
    private static final AmazonSimpleWorkflow swfClient = getSWFClient();

    public static void main(String[] args) throws Exception {

        WorkflowType workflowType = getWorkflowType("DeliveryWorkflow.deliver");
        if (workflowType == null){
            throw new RuntimeException("workflow type not found");
        }

        Run run = runWorkflow(workflowType, Jackson.toJsonString(getRequest()));

        System.out.println("Started helloWorld workflow with workflowType=\"" + workflowType.getName()
            + "\" and runId=\"" + run.getRunId() + "\"");

//
//        DeliveryWorkflowClientExternalFactory clientFactory = new DeliveryWorkflowClientExternalFactoryImpl(simpleWorkflow, domain);
//        DeliveryWorkflowClientExternal workflow = clientFactory.getClient();
//
//        // Start Wrokflow Execution
//        StartWorkflowOptions options = new StartWorkflowOptions().withLambdaRole("arn:aws:iam::625416862388:role/swf_lambda_role");
//        workflow.deliver(Jackson.toJsonString(getRequest()), options);
//
//        // WorkflowExecution is available after workflow creation
//        WorkflowExecution workflowExecution = workflow.getWorkflowExecution();
//        System.out.println("Started helloWorld workflow with workflowId=\"" + workflowExecution.getWorkflowId()
//            + "\" and runId=\"" + workflowExecution.getRunId() + "\"");

//
//        Thread.sleep(5000);
//        workflow.generationFinished("ebooks/eb6c2a7d-188d-4f16-a202-4c3836db55f0");
    }

    public static AmazonSimpleWorkflow getSWFClient() {
        AmazonSimpleWorkflow client = new AmazonSimpleWorkflowClient(getCredentials());
        client.setRegion(Region.getRegion(Regions.EU_WEST_1));
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

    private static WorkflowType getWorkflowType(String name){
        ListWorkflowTypesRequest req = new ListWorkflowTypesRequest();
        req.setDomain(DOMAIN);
        req.setRegistrationStatus(RegistrationStatus.REGISTERED);
        for (WorkflowTypeInfo info : swfClient.listWorkflowTypes(req).getTypeInfos()){
            if (info.getWorkflowType().getName().equals(name)){
                return info.getWorkflowType();
            }
        }

        return null;
    }

    private static Run runWorkflow(WorkflowType type, String input){
        StartWorkflowExecutionRequest startRequest = new StartWorkflowExecutionRequest();

        // HACKY way to imitate the way Flow Framework starts workflows
        List<String> objects = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        objects.add(input);
        parameters.add("[Ljava.lang.Object;");
        parameters.add(objects);

        startRequest.setDomain(DOMAIN);
        startRequest.setWorkflowId(UUID.randomUUID().toString());
        startRequest.setWorkflowType(type);
        startRequest.setInput(Jackson.toJsonString(parameters));
        startRequest.setLambdaRole("arn:aws:iam::625416862388:role/swf_lambda_role");
        return swfClient.startWorkflowExecution(startRequest);
    }
}
