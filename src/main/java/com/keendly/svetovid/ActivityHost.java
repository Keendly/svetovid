package com.keendly.svetovid;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient;

public class ActivityHost {

    private static final String ACTIVITIES_TASK_LIST = "HelloWorld";

    public static void main(String[] args) throws Exception {
//        AmazonSimpleWorkflow swfService = createSWFClient();
//        String domain = "sample";
//
//        final ActivityWorker worker = new ActivityWorker(swfService, domain, ACTIVITIES_TASK_LIST);
//
//        // Create activity implementations
//        DeliveryActivities helloWorldActivitiesImpl = new DeliveryActivitiesImpl();
//        worker.addActivitiesImplementation(helloWorldActivitiesImpl);
//
//        worker.start();
//
//        System.out.println("LambdaActivity Worker Started for Task List: " + worker.getTaskListToPoll());
//
//        Runtime.getRuntime().addShutdownHook(new Thread() {
//
//            public void invoke() {
//                try {
//                    worker.shutdownAndAwaitTermination(1, TimeUnit.MINUTES);
//                    System.out.println("LambdaActivity Worker Exited.");
//                }
//                catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
//        });
//
//        System.out.println("Please press any key to terminate service.");
//
//        try {
//            System.in.read();
//        }
//        catch (IOException e) {
//            e.printStackTrace();
//        }
//        System.exit(0);

    }

    public static AmazonSimpleWorkflow createSWFClient() {
        //        AWSCredentials awsCredentials = new BasicAWSCredentials();
        AmazonSimpleWorkflow client = new AmazonSimpleWorkflowClient();
        client.setRegion(Region.getRegion(Regions.EU_WEST_1));
        //        client.setEndpoint(this.swfServiceUrl);
        return client;
    }
}
