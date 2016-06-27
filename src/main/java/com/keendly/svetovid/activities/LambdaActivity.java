package com.keendly.svetovid.activities;

import com.amazonaws.services.simpleworkflow.flow.DecisionContext;
import com.amazonaws.services.simpleworkflow.flow.DecisionContextProvider;
import com.amazonaws.services.simpleworkflow.flow.DecisionContextProviderImpl;
import com.amazonaws.services.simpleworkflow.flow.core.Promise;
import com.amazonaws.services.simpleworkflow.flow.core.Settable;
import com.amazonaws.services.simpleworkflow.flow.core.Task;
import com.amazonaws.services.simpleworkflow.flow.worker.LambdaFunctionClient;
import com.amazonaws.util.json.Jackson;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keendly.svetovid.DeliveryState;
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

public abstract class LambdaActivity<T, V> {

    private ParameterizedTypeImpl outputType;
    private Class<V> outputClass;

    protected LambdaActivity(){
        Type type = ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[1];
        try {
            outputClass = (Class<V>) type;
        } catch(ClassCastException e) {
            outputType = (ParameterizedTypeImpl) type;
        }
    }

    /**
     * Returns the state the flow should have after completing the activity
     */
    public abstract DeliveryState getCompletedState();

    protected abstract String getLambdaName();

    public Promise<V> invoke(T request){
        DecisionContextProvider decisionProvider = new DecisionContextProviderImpl();
        DecisionContext decisionContext = decisionProvider.getDecisionContext();
        LambdaFunctionClient lambdaClient = decisionContext.getLambdaFunctionClient();
        Promise<String> stringResultPromise = lambdaClient.scheduleLambdaFunction(getLambdaName(),
            Jackson.toJsonString(request));

        Settable<V> result = new Settable<>();
        new Task(stringResultPromise) {
            @Override
            protected void doExecute() throws Throwable {
                String resString = stringResultPromise.get();
                result.set(mapToOutput(resString));
            }
        };
        return result;
    }

    private V mapToOutput(String s){
        if (outputClass != null){
            return Jackson.fromJsonString(s, outputClass);
        } else {
            if (outputType.getRawType().equals(List.class)){
                ObjectMapper mapper = new ObjectMapper();
                JavaType type = mapper.getTypeFactory().
                    constructCollectionType(List.class, (Class) outputType.getActualTypeArguments()[0]);

                try {
                    return mapper.readValue(s, type);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                throw new RuntimeException("Not supported raw type: " +
                    outputType.getRawType().getName());
            }
        }
    }
}
