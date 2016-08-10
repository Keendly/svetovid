package com.keendly.svetovid;

import com.amazonaws.services.simpleworkflow.flow.annotations.Execute;
import com.amazonaws.services.simpleworkflow.flow.annotations.GetState;
import com.amazonaws.services.simpleworkflow.flow.annotations.Signal;
import com.amazonaws.services.simpleworkflow.flow.annotations.Workflow;
import com.amazonaws.services.simpleworkflow.flow.annotations.WorkflowRegistrationOptions;
import com.keendly.svetovid.activities.extract.model.ExtractFinished;

import java.io.IOException;

@Workflow
@WorkflowRegistrationOptions(defaultExecutionStartToCloseTimeoutSeconds = 30 * 60, // 30 minutes
                             defaultTaskStartToCloseTimeoutSeconds = 300) // 10 minutes
public interface DeliveryWorkflow {

    @Execute(version = "1.0")
    void deliver(String deliveryRequest) throws IOException;

    @Signal
    void generationFinished(String generateResult);

    @Signal
    void extractionFinished(ExtractFinished extractFinished);

    @GetState
    String getState();
}
