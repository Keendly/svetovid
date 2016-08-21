package com.keendly.svetovid.s3;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;

public class S3ClientHolder {

    private static AmazonS3 s3 = new AmazonS3Client();

    // to allow mocking the client in unit tests
    public static void set(AmazonS3 client){
        s3 = client;
    }

    public static AmazonS3 get(){
        return s3;
    }
}
