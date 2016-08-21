package com.keendly.utils.mock;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
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
import org.hamcrest.BaseMatcher;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.json.JSONException;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.io.ByteArrayInputStream;

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
                        JSONAssert.assertEquals(request.toString(), mock.getInvocation().get().getRequest(), JSONCompareMode.LENIENT);
                    }
                };
            }

            @Override
            protected void doCatch(Throwable e) throws Throwable {
                throw e;
            }
        };
    }

    public interface Callback {
        void execute() throws JSONException;
    }

    public static void executeAfterInvoked(LambdaMock mock, Callback callback){
        new TryCatch() {

            @Override
            protected void doTry() throws Throwable {
                new Task(mock.getInvocation()){

                    @Override
                    protected void doExecute() throws Throwable {
                        callback.execute();
                    }
                };
            }

            @Override
            protected void doCatch(Throwable throwable) throws Throwable {
                throw throwable;
            }
        };
    }

    public static void mockS3Object(String key, String content, AmazonS3 mockClient){
        S3Object s3Object = mock(S3Object.class);
        S3ObjectInputStream is = new S3ObjectInputStream(new ByteArrayInputStream(content.getBytes()), null);
        when(s3Object.getObjectContent()).thenReturn(is);
        when(mockClient.getObject(argThat(new BaseMatcher<GetObjectRequest>() {

            @Override
            public void describeTo(Description description) {

            }

            @Override
            public boolean matches(Object item) {
                if (!(item instanceof GetObjectRequest)){
                    return false;
                }
                GetObjectRequest o = (GetObjectRequest) item;
                return o.getBucketName().equals("keendly") && o.getKey().equals(key);
            }
        }))).thenReturn(s3Object);
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
