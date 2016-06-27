package com.keendly.utils.mock;

import com.amazonaws.services.simpleworkflow.flow.DecisionContext;
import com.amazonaws.services.simpleworkflow.flow.DecisionContextProvider;
import com.amazonaws.services.simpleworkflow.flow.DecisionContextProviderImpl;
import com.amazonaws.services.simpleworkflow.flow.WorkflowClock;
import com.amazonaws.services.simpleworkflow.flow.core.Promise;
import com.amazonaws.services.simpleworkflow.flow.core.Settable;
import com.amazonaws.services.simpleworkflow.flow.core.Task;
import com.amazonaws.services.simpleworkflow.flow.test.TestDecisionContext;
import com.amazonaws.services.simpleworkflow.flow.worker.CurrentDecisionContext;
import com.amazonaws.util.json.Jackson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.junit.Assert;

public class Helpers {

    private static MockedLambdaFunctionInvoker invoker;

    public static void initInvoker(){
        AsyncTestLambdaFunctionClient client = new AsyncTestLambdaFunctionClient();
        invoker = client.getInvoker();

        DecisionContextProvider decisionProvider = new DecisionContextProviderImpl();
        DecisionContext decisionContext = decisionProvider.getDecisionContext();

        CurrentDecisionContext.set(new TestDecisionContext(decisionContext.getActivityClient(), decisionContext.getWorkflowClient(),
            decisionContext.getWorkflowClock(), decisionContext.getWorkflowContext(), client));

    }

    public static LambdaMock lambdaMock(String name){
        LambdaMock mock = new LambdaMock(name);
        registerMock(mock);
        return mock;
    }

    private static void registerMock(LambdaMock mock){
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
        new Task(mock.getInvocation()) {

            @Override
            protected void doExecute() throws Throwable {
                Assert.assertEquals(String.format("Expected invocation with %s not logged", request),
                    request, mock.getInvocation().get().getRequest());
            }
        };
    }

    public static void verifyInvokedWith(LambdaMock mock, JsonNode request){
        ObjectMapper mapper = new ObjectMapper();
        new Task(mock.getInvocation()) {

            @Override
            protected void doExecute() throws Throwable {
                Assert.assertEquals(String.format("Expected invocation with %s not logged", request),
                    request, mapper.readTree(mock.getInvocation().get().getRequest()));
            }
        };
    }

    public static class Stubbing {

        private String response;
        private Answer answer;

        private DecisionContextProvider contextProvider = new DecisionContextProviderImpl();
        private WorkflowClock clock = contextProvider.getDecisionContext().getWorkflowClock();

        private Stubbing(){
        }

        public void thenReturn(String response){
            this.response = response;
        }

        public void thenAnswer(Answer answer){
            this.answer = answer;
        }

        public void thenSerializeAndReturn(Object o){
            this.response = Jackson.toJsonString(o);
        }

        public void thenFail(){
            // TODO
            throw new RuntimeException("Not implemented!");
        }

        protected Promise<String> getResponse(){
            Settable<String> ret = new Settable<>();

            // need to have a delay because otherwise task is being executed synchronously
            // and we want to simulate async call to lambda
            Promise<Void> timer = clock.createTimer(1);
            new Task(timer) {
                @Override
                protected void doExecute() throws Throwable {
                    if (response != null){
                        ret.set(response);
                    } else if (answer != null){
                        ret.set(answer.run());
                    }
                }
            };

            return ret;
        }
    }
}
