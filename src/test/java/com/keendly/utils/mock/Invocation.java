package com.keendly.utils.mock;

import java.util.Date;

public class Invocation {

    private Date timestamp;
    private String request;

    public Invocation(String request){
        this.timestamp = new Date();
        this.request = request;
    }

    public String getRequest(){
        return request;
    }
}
