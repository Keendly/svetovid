package com.keendly.svetovid.activities.extract;

import com.keendly.svetovid.DeliveryState;
import com.keendly.svetovid.activities.LambdaActivity;
import com.keendly.svetovid.activities.extract.model.ExtractRequest;
import com.keendly.svetovid.activities.extract.model.ExtractResult;

import java.util.List;

public class ExtractArticlesActivity extends LambdaActivity<ExtractRequest, List<ExtractResult>> {

    private static final String LAMBDA = "veles";

    @Override
    protected String getLambdaName() {
        return LAMBDA;
    }

    @Override
    public DeliveryState getCompletedState() {
        return DeliveryState.ARTICLES_EXTRACTED;
    }
}
