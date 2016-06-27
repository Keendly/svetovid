package com.keendly.utils.mock;

import com.amazonaws.services.simpleworkflow.flow.core.Promise;

public interface AsyncTestLambdaFunctionInvoker {

    Promise<String> invoke(String name, String input, long timeout);
}
