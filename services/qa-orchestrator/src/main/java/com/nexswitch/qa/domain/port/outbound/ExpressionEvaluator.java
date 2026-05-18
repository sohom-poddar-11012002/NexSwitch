package com.nexswitch.qa.domain.port.outbound;

import java.util.Map;

// LEARN: Outbound port for expression evaluation — keeps SpEL/JSONPath (Spring libraries)
//        out of the domain layer. The domain defines the contract; the application layer
//        supplies the SpEL+JSONPath implementation via constructor injection.
public interface ExpressionEvaluator {

    EvaluationResult evaluate(String expression, Map<String, Object> context);

    record EvaluationResult(boolean passed, String actual, String message) {}
}
