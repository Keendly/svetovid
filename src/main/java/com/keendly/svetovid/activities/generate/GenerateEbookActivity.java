package com.keendly.svetovid.activities.generate;

import com.keendly.svetovid.DeliveryState;
import com.keendly.svetovid.activities.LambdaActivity;
import com.keendly.svetovid.activities.generate.model.TriggerGenerateRequest;
import com.keendly.svetovid.activities.generate.model.TriggerGenerateResponse;

public class GenerateEbookActivity
    extends LambdaActivity<TriggerGenerateRequest, TriggerGenerateResponse> {

    @Override
    public DeliveryState getCompletedState() {
        return DeliveryState.GENERATION_TRIGGERED;
    }

    @Override
    protected String getLambdaName() {
        return "jindle_trigger";
    }
}
