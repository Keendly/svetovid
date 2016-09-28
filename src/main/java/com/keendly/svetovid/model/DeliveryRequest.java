package com.keendly.svetovid.model;

import java.util.List;

public class DeliveryRequest {

    public Integer id;
    public Integer userId;
    public String email;
    public Long timestamp;

    public List<DeliveryItem> items;
    public S3Object s3Items;
    public boolean dryRun;
}
