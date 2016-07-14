package com.keendly.svetovid;

import java.util.concurrent.TimeUnit;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient;
import com.amazonaws.services.simpleworkflow.flow.WorkflowWorker;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

public class WorkerHost {

    private static final String DECISION_TASK_LIST = "DeliveryWorkflow"; // no idea what is this :D
    private static final String DOMAIN = "keendly";

    public static void main(String[] args) throws Exception {
        Arguments arguments = new Arguments();
        JCommander jc = new JCommander(arguments);

        try {
            jc.parse(args);
        } catch (Exception e){
            jc.usage();
            System.exit(1);
        }

        AmazonSimpleWorkflow swfClient = getSWFClient(arguments.profile);

        final WorkflowWorker worker = new WorkflowWorker(swfClient, DOMAIN, DECISION_TASK_LIST);
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
    }

    private static AmazonSimpleWorkflow getSWFClient(String profile) {
        AmazonSimpleWorkflow client;
        if (profile == null){
            client = new AmazonSimpleWorkflowClient();
        } else {
            ProfileCredentialsProvider credentialsProvider = new ProfileCredentialsProvider(profile);
            client = new AmazonSimpleWorkflowClient(credentialsProvider);
        }
        client.setRegion(Region.getRegion(Regions.EU_WEST_1));
        return client;
    }

    private static class Arguments {

        @Parameter(names = "--profile", description = "AWS Credentials profile")
        String profile;
    }
}
