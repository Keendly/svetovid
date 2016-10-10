package com.keendly.svetovid.activities.generate.model;

public class GenerateFinished {

    public String key;
    public boolean success;
    public String error;

    @Override
    public String toString() {
        return "GenerateFinished{" +
            "key='" + key + '\'' +
            ", success=" + success +
            ", error='" + error + '\'' +
            '}';
    }
}
