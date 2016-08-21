package com.keendly.svetovid.utils;

import com.amazonaws.services.simpleworkflow.flow.DecisionContext;
import com.amazonaws.services.simpleworkflow.flow.DecisionContextProvider;
import com.amazonaws.services.simpleworkflow.flow.DecisionContextProviderImpl;

public class WorkflowUtils {

    private static WorkflowUtils INSTANCE = new WorkflowUtils();

    public static WorkflowUtils get(){
        return INSTANCE;
    }

    // to allow mocking in unit tests
    public static void set(WorkflowUtils mock){
        INSTANCE = mock;
    }

    private WorkflowUtils(){
        // singleton
    }

    public String getWorkFlowId(){
        DecisionContext context = getContext();
        return context.getWorkflowContext().getWorkflowExecution().getWorkflowId();
    }

    public String getRunId(){
        DecisionContext context = getContext();
        return context.getWorkflowContext().getWorkflowExecution().getRunId();
    }

    private DecisionContext getContext(){
        DecisionContextProvider contextProvider
            = new DecisionContextProviderImpl();
        return contextProvider.getDecisionContext();
    }
}
