package com.keendly.svetovid.activities.send;

import com.keendly.svetovid.DeliveryState;
import com.keendly.svetovid.activities.LambdaActivity;
import com.keendly.svetovid.activities.send.model.SendRequest;
import com.keendly.svetovid.activities.send.model.SendResult;

public class SendEbookActivity extends LambdaActivity<SendRequest, SendResult> {

    @Override
    public DeliveryState getCompletedState() {
        return DeliveryState.SENT;
    }

    @Override
    protected String getLambdaName() {
        return "perun_swf";
    }
}
