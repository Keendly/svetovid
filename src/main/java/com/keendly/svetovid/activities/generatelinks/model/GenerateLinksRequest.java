package com.keendly.svetovid.activities.generatelinks.model;

import java.util.List;
import java.util.Map;

public class GenerateLinksRequest {

    public Map<String, List<GenerateLinksArticle>> articles;
    public String provider;
    public Integer userId;
    public String s3Articles;
}
