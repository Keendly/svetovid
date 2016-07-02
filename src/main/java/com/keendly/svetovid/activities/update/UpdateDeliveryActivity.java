package com.keendly.svetovid.activities.update;

import com.keendly.svetovid.DeliveryState;
import com.keendly.svetovid.activities.LambdaActivity;
import com.keendly.svetovid.activities.update.model.UpdateResult;
import com.keendly.svetovid.activities.update.model.UpdateRequest;

public class UpdateDeliveryActivity extends LambdaActivity<UpdateRequest, UpdateResult> {

    @Override
    public DeliveryState getCompletedState() {
        return DeliveryState.UPDATED;
    }

    @Override
    protected String getLambdaName() {
        return "bylun";
    }
}
