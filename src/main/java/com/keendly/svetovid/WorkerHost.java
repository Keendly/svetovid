package com.keendly.svetovid;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient;
import com.amazonaws.services.simpleworkflow.flow.WorkflowWorker;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class WorkerHost {

    private static final String DECISION_TASK_LIST = "HelloWorldWorkflow";

    public static void main(String[] args) throws Exception {
        AmazonSimpleWorkflow swfService = createSWFClient();
        String domain = "sample";

        final WorkflowWorker worker = new WorkflowWorker(swfService, domain, DECISION_TASK_LIST);
        worker.addWorkflowImplementationType(DeliveryWorkflowImpl.class);
        worker.start();

        System.out.println("Workflow Host Service Started...");

        Runtime.getRuntime().addShutdownHook(new Thread() {

            public void run() {
                try {
                    worker.shutdownAndAwaitTermination(1, TimeUnit.MINUTES);
                    System.out.println("Workflow Host Service Terminated...");
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        System.out.println("Please press any key to terminate service.");

        try {
            System.in.read();
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        System.exit(0);

    }

    public static AmazonSimpleWorkflow createSWFClient() {
//        AWSCredentials awsCredentials = new BasicAWSCredentials();
        AmazonSimpleWorkflow client = new AmazonSimpleWorkflowClient();
        client.setRegion(Region.getRegion(Regions.EU_WEST_1));
//        client.setEndpoint(this.swfServiceUrl);
        return client;
    }
}
