package com.keendly.svetovid.activities;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import com.amazonaws.services.simpleworkflow.flow.DecisionContext;
import com.amazonaws.services.simpleworkflow.flow.DecisionContextProvider;
import com.amazonaws.services.simpleworkflow.flow.DecisionContextProviderImpl;
import com.amazonaws.services.simpleworkflow.flow.core.Promise;
import com.amazonaws.services.simpleworkflow.flow.worker.LambdaFunctionClient;
import com.keendly.svetovid.DeliveryState;
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

public abstract class LambdaActivity<T, S> {

    // even though lambda itself has 5 minute timeout, it seems that sometimes SWF starts timer before the function
    // execution actually begins
    private static final int TIMEOUT = 10 * 60; // 10 minutes

    private ParameterizedTypeImpl outputType;
    private Class<S> outputClass;

    protected LambdaActivity(){
        Type type = ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[1];
        try {
            outputClass = (Class<S>) type;
        } catch(ClassCastException e) {
            outputType = (ParameterizedTypeImpl) type;
        }
    }

    /**
     * Returns the state the flow should have after completing the activity
     */
    public abstract DeliveryState getCompletedState();

    /**
     * Returns the name of the function to invoke
     */
    protected abstract String getLambdaName();

    public Promise<String> invoke(String request){
        DecisionContextProvider decisionProvider = new DecisionContextProviderImpl();
        DecisionContext decisionContext = decisionProvider.getDecisionContext();
        LambdaFunctionClient lambdaClient = decisionContext.getLambdaFunctionClient();
        return lambdaClient.scheduleLambdaFunction(getLambdaName(), request, TIMEOUT);
    }

}
