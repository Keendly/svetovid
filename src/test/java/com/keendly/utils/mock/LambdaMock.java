package com.keendly.utils.mock;

import com.amazonaws.services.simpleworkflow.flow.core.Promise;
import com.amazonaws.services.simpleworkflow.flow.core.Settable;
import org.hamcrest.Matcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LambdaMock {

    private String name;

    private Settable<Invocation> invocation = new Settable<>();
    private Map<Matcher, Helpers.Stubbing> stubs = new HashMap<>();

    public LambdaMock(String name){
        this.name = name;
    }

    public String getName(){
        return name;
    }

    protected void addStubbing(Matcher matcher, Helpers.Stubbing stubbing){
        stubs.put(matcher, stubbing);
    }

    protected Promise<String> getStubbedResponse(String request){
        List<Helpers.Stubbing> matched = new ArrayList();

        stubs.forEach((k, v) -> {
            if (k.matches(request)){
                matched.add(v);
            }
        });

        if (matched.isEmpty()){
            return null;
        }

        if (matched.size() > 1){
            // TODO
            throw new RuntimeException("Not implemented!");
        }

        return matched.get(0).getResponse();
    }

    protected void logInvocation(String request){
        Invocation invocation = new Invocation(request);
        this.invocation.set(invocation);
    }

    public Promise<Invocation> getInvocation(){
        return invocation;
    }

    public void reset(){
        invocation = new Settable<>();
        stubs = new HashMap<>();
    }
}
