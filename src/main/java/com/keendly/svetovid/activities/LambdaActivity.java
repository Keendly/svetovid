package com.keendly.svetovid.activities;

import com.amazonaws.services.simpleworkflow.flow.core.Promise;
import com.keendly.svetovid.DeliveryState;
import com.keendly.svetovid.utils.LambdaInvoker;
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public abstract class LambdaActivity<T, S> {

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
        return LambdaInvoker.invoke(getLambdaName(), request);
    }
}
