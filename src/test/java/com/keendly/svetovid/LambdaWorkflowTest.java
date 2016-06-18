package com.keendly.svetovid;

import java.util.Map;

import com.amazonaws.services.simpleworkflow.flow.core.Settable;

public abstract class LambdaWorkflowTest {

    private Map<String, Settable<String>> lambdaInputs;
    private Map<String, String> lambdaOutputs;


    protected void mockLambdaInvocation(String functionName, String ifInput, String theOutput){
//        lambdaOutputs.put()
    }

    protected void assertLambdaInvoked(String functionName, String content){


    }

}

