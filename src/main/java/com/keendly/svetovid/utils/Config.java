package com.keendly.svetovid.utils;

public class Config {

    public static final String VERSION = "1.2";

    private final static int EXTRACTION_TIMEOUT_IN_SECONDS = 10 * 60; // 10minutes

    private static int extractionTimeout = EXTRACTION_TIMEOUT_IN_SECONDS;

    public static int getExtractionTimeout(){
        return extractionTimeout;
    }

    public static void setExtractionTimeout(int timeout){
        extractionTimeout = timeout;
    }

    public static void reset(){
        extractionTimeout = EXTRACTION_TIMEOUT_IN_SECONDS;
    }

}
