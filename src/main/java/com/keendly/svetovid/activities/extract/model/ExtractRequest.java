package com.keendly.svetovid.activities.extract.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ExtractRequest implements Serializable {

    public String workflowId;
    public String runId;
    public List<ExtractRequestItem> content = new ArrayList<>();

    public static class ExtractRequestItem {
        public String url;
        public Boolean withImages;
        public Boolean withMetadata;
    }
}
