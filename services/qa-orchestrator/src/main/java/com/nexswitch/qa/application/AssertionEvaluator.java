package com.nexswitch.qa.application;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.nexswitch.qa.domain.port.outbound.ExpressionEvaluator;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Map;

// LEARN: SpEL (Spring Expression Language) evaluates "auth_response.field39 == '00'" against a
//        Map-backed context. MapAccessor enables dot-notation property access on Map keys.
//        Lives in application layer — Spring libs stay out of the pure-Java domain layer.
public class AssertionEvaluator implements ExpressionEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();

    @Override
    public EvaluationResult evaluate(String expression, Map<String, Object> context) {
        if (expression == null || expression.isBlank()) {
            return new EvaluationResult(false, null, "empty assertion expression");
        }

        if (expression.trim().startsWith("$.")) {
            return evaluateJsonPath(expression, context);
        }

        return evaluateSpel(expression, context);
    }

    private EvaluationResult evaluateSpel(String expression, Map<String, Object> context) {
        try {
            StandardEvaluationContext ctx = new StandardEvaluationContext();
            // LEARN: MapAccessor registers a property accessor for Map<String,Object> roots so that
            //        `field39` in a SpEL expression resolves to map.get("field39") without requiring
            //        the #variable prefix. Without it, dot-access on a Map throws EL1008E.
            ctx.addPropertyAccessor(new MapAccessor());
            ctx.setVariables(context);
            ctx.setRootObject(context);
            Expression expr = parser.parseExpression(expression);
            Object result = expr.getValue(ctx);
            boolean passed = Boolean.TRUE.equals(result) || "true".equalsIgnoreCase(String.valueOf(result));
            return new EvaluationResult(passed, String.valueOf(result),
                    passed ? "OK" : "expression evaluated to: " + result);
        } catch (Exception e) {
            return new EvaluationResult(false, null, "SpEL error: " + e.getMessage());
        }
    }

    // LEARN: JSONPath evaluates against a JSON string stored in context under a response key.
    //        Expression format: "$.auth_response.field39 == '00'" — auth_response is the context
    //        key whose value is the raw JSON; ".field39" is the path within that JSON document.
    private EvaluationResult evaluateJsonPath(String expression, Map<String, Object> context) {
        try {
            String[] parts = expression.split("==", 2);
            if (parts.length != 2) {
                return new EvaluationResult(false, null, "JSONPath assertion must use == operator: " + expression);
            }
            String path     = parts[0].trim();
            String expected = parts[1].trim().replaceAll("^['\"]|['\"]$", "");

            String[] segments = path.split("\\.");
            String rootKey = segments[1];
            Object contextVal = context.get(rootKey);
            if (contextVal == null) {
                return new EvaluationResult(false, null, "context key not found: " + rootKey);
            }
            String json = contextVal instanceof String s ? s : String.valueOf(contextVal);

            int secondDotIdx = path.indexOf('.', path.indexOf('.') + 1);
            String innerPath = secondDotIdx >= 0 ? "$" + path.substring(secondDotIdx) : "$";
            String actual = JsonPath.read(json, innerPath).toString();

            boolean passed = expected.equals(actual);
            return new EvaluationResult(passed, actual,
                    passed ? "OK" : "expected [" + expected + "] but got [" + actual + "]");
        } catch (PathNotFoundException e) {
            return new EvaluationResult(false, null, "JSONPath not found: " + e.getMessage());
        } catch (Exception e) {
            return new EvaluationResult(false, null, "JSONPath error: " + e.getMessage());
        }
    }
}
