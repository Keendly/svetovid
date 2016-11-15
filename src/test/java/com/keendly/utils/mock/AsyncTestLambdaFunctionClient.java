package com.keendly.utils.mock;

import com.amazonaws.services.simpleworkflow.flow.LambdaFunctionException;
import com.amazonaws.services.simpleworkflow.flow.LambdaFunctionFailedException;
import com.amazonaws.services.simpleworkflow.flow.core.Promise;
import com.amazonaws.services.simpleworkflow.flow.test.TestLambdaFunctionClient;

public class AsyncTestLambdaFunctionClient extends TestLambdaFunctionClient {

    private MockedLambdaFunctionInvoker invoker = new MockedLambdaFunctionInvoker();

    @Override
    public Promise<String> scheduleLambdaFunction(String name, String input) {
        return scheduleLambdaFunction(name, input, 300);
    }

    @Override
    public Promise<String> scheduleLambdaFunction(String name, String input,
        long timeoutSeconds) {
        final String id = decisionContextProvider.getDecisionContext()
            .getWorkflowClient().generateUniqueId();

        try {
            return invoker.invoke(name, input, timeoutSeconds);
        } catch (Throwable e) {
            if (e instanceof LambdaFunctionException) {
                throw (LambdaFunctionException) e;
            } else {
                LambdaFunctionFailedException failure = new LambdaFunctionFailedException(
                    0, name, id, e.getMessage());
                failure.initCause(e);
                throw failure;
            }
        }
    }

    public MockedLambdaFunctionInvoker getInvoker(){
        return invoker;
    }
}
