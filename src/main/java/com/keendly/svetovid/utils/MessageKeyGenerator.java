package com.keendly.svetovid.utils;

import java.util.UUID;

public class MessageKeyGenerator {

    private static final String S3_MESSAGES_DIR = "messages";

    private static MessageKeyGenerator INSTANCE = new MessageKeyGenerator();

    private MessageKeyGenerator(){
        // singleton
    }

    public static MessageKeyGenerator get(){
        return INSTANCE;
    }

    // to allow mocking in unit tests
    public static void set(MessageKeyGenerator mock){
        INSTANCE = mock;
    }

    public String generate(){
        return S3_MESSAGES_DIR + "/" + UUID.randomUUID().toString().replace("-", "") + ".json";
    }
}
