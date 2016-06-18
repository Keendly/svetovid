package com.keendly.utils.mock;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.simpleworkflow.flow.test.TestLambdaFunctionInvoker;

public class MockedLambdaFunctionInvoker implements TestLambdaFunctionInvoker {

    private Map<String, LambdaMock> mocks = new HashMap();

    @Override
    public String invoke(String functionName, String input, long timeout) {
        if (isMocked(functionName)){
            LambdaMock mock = getMock(functionName);
            mock.logInvocation(input);
            return mock.getStubbedResponse(input);
        }

        return null;
    }

    private boolean isMocked(String functionName){
        return mocks.containsKey(functionName);
    }

    private LambdaMock getMock(String functionName){
        return mocks.get(functionName);
    }

    public void register(LambdaMock mock){
        mocks.put(mock.getName(), mock);
    }
}
