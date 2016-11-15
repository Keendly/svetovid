package com.keendly.svetovid.activities.generatelinks.model;

import com.keendly.svetovid.model.S3Object;

import java.util.List;
import java.util.Map;

public class GenerateLinksResponse {

    public Map<String, List<ActionLink>> links;
    public S3Object s3Links;
}
