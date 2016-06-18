package com.keendly.utils.mock;

import java.util.Date;

public class Invocation {

    private Date timestamp;
    private String argument;

    public Invocation(String request){
        this.timestamp = new Date();
        this.argument = request;
    }

    private String getArgument(){
        return argument;
    }
}
