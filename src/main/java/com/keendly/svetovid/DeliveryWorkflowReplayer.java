package com.keendly.svetovid;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient;
import com.amazonaws.services.simpleworkflow.flow.WorkflowException;
import com.amazonaws.services.simpleworkflow.flow.WorkflowReplayer;
import com.amazonaws.services.simpleworkflow.model.WorkflowExecution;

public class DeliveryWorkflowReplayer {

    public static void main(String[] args) throws Exception{
        String workflowId = "d04a9923-d18d-4817-9b48-2b998a967ba1";
        String runId = "22qiqllC8BiVXXyvGu0Dbv0SZlc0as5COTlxYs+ZdjZj4=";
        Class<DeliveryWorkflowImpl> workflowImplementationType = DeliveryWorkflowImpl.class;
        WorkflowExecution workflowExecution = new WorkflowExecution();
        workflowExecution.setWorkflowId(workflowId);
        workflowExecution.setRunId(runId);

        WorkflowReplayer<DeliveryWorkflowImpl> replayer = new WorkflowReplayer<>(
            createSWFClient(), "sample", workflowExecution, workflowImplementationType);

        try {
            Object workflow = replayer.loadWorkflow();
//            System.out.println(workflow);
//            String flowThreadDump = replayer.getAsynchronousThreadDumpAsString();
//            List<AsyncTaskInfo> tasks = replayer.getAsynchronousThreadDump();
            System.out.println("Workflow asynchronous thread dump:");
//            System.out.println(flowThreadDump);
        }
        catch (WorkflowException e) {
            System.out.println("No asynchronous thread dump available as workflow has failed: " + e);
        }


    }

    public static AmazonSimpleWorkflow createSWFClient() {
        //        AWSCredentials awsCredentials = new BasicAWSCredentials();
        AmazonSimpleWorkflow client = new AmazonSimpleWorkflowClient();
        client.setRegion(Region.getRegion(Regions.EU_WEST_1));
        //        client.setEndpoint(this.swfServiceUrl);
        return client;
    }
}
