package com.keendly.svetovid.activities;

import com.amazonaws.services.simpleworkflow.flow.DecisionContext;
import com.amazonaws.services.simpleworkflow.flow.DecisionContextProvider;
import com.amazonaws.services.simpleworkflow.flow.DecisionContextProviderImpl;
import com.amazonaws.services.simpleworkflow.flow.core.Promise;
import com.amazonaws.services.simpleworkflow.flow.worker.LambdaFunctionClient;
import com.keendly.svetovid.DeliveryState;

import java.io.IOException;

public abstract class LambdaActivity<T, V> {

    protected abstract String mapInput(T input);

    public abstract V mapOutput(String s) throws IOException;

    /**
     * Returns the state the flow should have after completing the activity
     */
    public abstract DeliveryState getCompletedState();

    protected abstract String getLambdaName();

    public Promise<String> invoke(T request){
        DecisionContextProvider decisionProvider = new DecisionContextProviderImpl();
        DecisionContext decisionContext = decisionProvider.getDecisionContext();
        LambdaFunctionClient lambdaClient = decisionContext.getLambdaFunctionClient();
        return lambdaClient.scheduleLambdaFunction(getLambdaName(), mapInput(request));
    }
}
