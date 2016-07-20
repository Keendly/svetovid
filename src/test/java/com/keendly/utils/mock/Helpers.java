package com.keendly.utils.mock;

import com.amazonaws.services.simpleworkflow.flow.DecisionContext;
import com.amazonaws.services.simpleworkflow.flow.DecisionContextProvider;
import com.amazonaws.services.simpleworkflow.flow.DecisionContextProviderImpl;
import com.amazonaws.services.simpleworkflow.flow.WorkflowClock;
import com.amazonaws.services.simpleworkflow.flow.core.Promise;
import com.amazonaws.services.simpleworkflow.flow.core.Settable;
import com.amazonaws.services.simpleworkflow.flow.core.Task;
import com.amazonaws.services.simpleworkflow.flow.core.TryCatch;
import com.amazonaws.services.simpleworkflow.flow.test.TestDecisionContext;
import com.amazonaws.services.simpleworkflow.flow.worker.CurrentDecisionContext;
import com.amazonaws.util.json.Jackson;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.skyscreamer.jsonassert.JSONAssert;

import static org.junit.Assert.*;

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

    public static void verifyInvokedWith(LambdaMock mock, JsonObject request){
        new TryCatch() {
            @Override
            protected void doTry() throws Throwable {
                new Task(mock.getInvocation()) {

                    @Override
                    protected void doExecute() throws Throwable {
                        JSONAssert.assertEquals(request.toString(), mock.getInvocation().get().getRequest(), false);
                    }
                };
            }

            @Override
            protected void doCatch(Throwable e) throws Throwable {
                throw e;
            }
        };
    }

    public static void verifyNotInvoked(LambdaMock mock){
        new TryCatch() {
            @Override
            protected void doTry() throws Throwable {
                assertFalse(mock.getInvocation().isReady());
            }

            @Override
            protected void doCatch(Throwable e) throws Throwable {
                throw e;
            }
        };
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

        public void thenReturn(JsonValue response){
            this.response = response.toString();
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
            new Task() {
                @Override
                protected void doExecute() throws Throwable {
                    if (response != null){
                        ret.set(response);
                    } else if (answer != null){
                        ret.set(answer.run());
                    } else {
                        throw new RuntimeException("aaa");
                    }
                }
            };

//            new TryCatch() {
//                @Override
//                protected void doTry() throws Throwable {
//
//                }
//
//                @Override
//                protected void doCatch(Throwable e) throws Throwable {
//                    throw e;
//                }
//            };
            return ret;
        }
    }
}
