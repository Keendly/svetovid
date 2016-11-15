package com.keendly.svetovid;

import com.amazonaws.services.simpleworkflow.flow.annotations.Execute;
import com.amazonaws.services.simpleworkflow.flow.annotations.GetState;
import com.amazonaws.services.simpleworkflow.flow.annotations.Signal;
import com.amazonaws.services.simpleworkflow.flow.annotations.Workflow;
import com.amazonaws.services.simpleworkflow.flow.annotations.WorkflowRegistrationOptions;
import com.keendly.svetovid.utils.Config;

import java.io.IOException;

@Workflow
@WorkflowRegistrationOptions(defaultExecutionStartToCloseTimeoutSeconds = 30 * 60, // 30 minutes
                             defaultTaskStartToCloseTimeoutSeconds = 300) // 10 minutes
public interface DeliveryWorkflow {

    @Execute(version = Config.VERSION)
    void deliver(String deliveryRequest) throws IOException;

    @Signal
    void generationFinished(String generateResult);

    @Signal
    void extractionFinished(String extractResult);

    @GetState
    String getState();
}
