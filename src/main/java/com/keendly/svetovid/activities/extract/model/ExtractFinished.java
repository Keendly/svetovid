package com.keendly.svetovid.activities.extract.model;

public class ExtractFinished {

    public String key;
    public boolean success;
    public String error;

    @Override
    public String toString() {
        return "ExtractFinished{" +
            "key='" + key + '\'' +
            ", success=" + success +
            ", error='" + error + '\'' +
            '}';
    }
}
