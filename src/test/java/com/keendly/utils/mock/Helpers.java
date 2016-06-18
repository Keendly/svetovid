package com.keendly.utils.mock;

import com.amazonaws.services.simpleworkflow.flow.DecisionContext;
import com.amazonaws.services.simpleworkflow.flow.DecisionContextProvider;
import com.amazonaws.services.simpleworkflow.flow.DecisionContextProviderImpl;
import com.amazonaws.services.simpleworkflow.flow.test.TestLambdaFunctionClient;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;

public class Helpers {

    private static MockedLambdaFunctionInvoker invoker;

    public static void initInvoker(){
        invoker = new MockedLambdaFunctionInvoker();

        DecisionContextProvider decisionProvider = new DecisionContextProviderImpl();
        DecisionContext decisionContext = decisionProvider.getDecisionContext();
        TestLambdaFunctionClient lambdaClient =
            (TestLambdaFunctionClient) decisionContext.getLambdaFunctionClient();
        lambdaClient.setInvoker(invoker);
    }

    public static LambdaMock lambdaMock(String name){
        return new LambdaMock(name);
    }

    public static void registerMock(LambdaMock mock){
        if (invoker == null){
            // TODO
            new RuntimeException("Invoker not initialized!");
        }
        invoker.register(mock);
    }

    public static Stubbing whenInvoked(LambdaMock mock){
        return whenInvokedWith(mock, CoreMatchers.any(String.class));
    }

    public static Stubbing whenInvokedWith(LambdaMock mock, Matcher matcher){
        Stubbing s = new Stubbing();
        mock.addStubbing(matcher, s);
        return s;
    }

    public static void verifyInvokedWith(LambdaMock mock, String request){
        // TODO
        throw new RuntimeException("Not implemented");
    }

    public static class Stubbing {

        private String response;

        private Stubbing(){
        }

        public void thenReturn(String response){
            this.response = response;
        }

        public void thenSerializeAndReturn(Object o){
            // TODO
            throw new RuntimeException("Not implemented!");
        }

        public void thenFail(){
            // TODO
            throw new RuntimeException("Not implemented!");
        }

        protected String getResponse(){
            return response;
        }
    }
}
