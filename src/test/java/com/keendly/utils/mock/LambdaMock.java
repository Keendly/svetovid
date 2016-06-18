package com.keendly.utils.mock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hamcrest.Matcher;

public class LambdaMock {

    private String name;

    private List<Invocation> invocations = new ArrayList<>();
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

    public String getStubbedResponse(String request){
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

    public void logInvocation(String request){
        Invocation invocation = new Invocation(request);
        invocations.add(invocation);
    }
}
