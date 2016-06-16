package com.keendly.svetovid.activities.extract;

import com.amazonaws.util.json.Jackson;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keendly.svetovid.DeliveryState;
import com.keendly.svetovid.activities.LambdaActivity;
import com.keendly.svetovid.activities.extract.model.ExtractRequest;
import com.keendly.svetovid.activities.extract.model.ExtractResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExtractArticlesActivity extends LambdaActivity<List<ExtractRequest>, List<ExtractResult>> {

    private static final Logger LOG = LoggerFactory.getLogger(ExtractArticlesActivity.class);
    private static final String LAMBDA = "veles";

    @Override
    public String mapInput(List<ExtractRequest> input) {
        Map<String, List<ExtractRequest>> extractRequest = new HashMap<>();
        extractRequest.put("requests", input);
        return Jackson.toJsonString(extractRequest);
    }

    @Override
    public List<ExtractResult> mapOutput(String s) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JavaType type = mapper.getTypeFactory().
            constructCollectionType(List.class, ExtractResult.class);
        return mapper.readValue(s, type);
    }

    @Override
    protected String getLambdaName() {
        return LAMBDA;
    }

    @Override
    public DeliveryState getCompletedState() {
        return DeliveryState.ARTICLES_EXTRACTED;
    }
}
