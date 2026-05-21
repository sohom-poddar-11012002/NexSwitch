# Ticket #137 — REST API Input Validation — Bean Validation + Global Exception Handler

## What

- `spring-boot-starter-validation` added to acquiring-service — brings Hibernate Validator (Jakarta Bean Validation 3.x)
- `@Valid` added to `QrController.generate()` and `UpiCreditController.credit()` request bodies
- Validation annotations on all request record fields:
  - `GenerateRequest`: `@NotBlank` + `@Size` on merchantId, terminalId, orderId; `@Pattern(regexp="\\d+\\.\\d{2}")` on amount; `@Size(min=3, max=3)` on currency
  - `UpiCreditRequest`: `@NotBlank` + `@Size` on npciTxnId; `@Pattern(regexp="[\\w.]+@[\\w]+")` on payerVpa/payeeVpa; `@Pattern` on amount; `@NotBlank` on txnRef
- `GlobalExceptionHandler` (`@RestControllerAdvice`) handles:
  - `MethodArgumentNotValidException` → 400 with `{"status":400,"error":"Validation Failed","violations":[{"field":"...","message":"..."}]}`
  - `HttpMessageNotReadableException` → 400 Bad Request (malformed JSON)
  - `IllegalArgumentException` → 400 Bad Request
  - `Exception` → 500 Internal Server Error

## Why

Without validation, any caller could send `"amount":"abc"` or omit `merchantId` entirely — the service would throw a `NumberFormatException` or NPE deep inside the domain, returning an unstructured 500. The global handler ensures every error surface returns a consistent, machine-readable structure that client SDKs can parse.

All future REST endpoints in acquiring-service (including #39 Static QR + UPI Collect) inherit the handler automatically — no per-controller setup needed.

## Design decisions

1. **`@RestControllerAdvice` over per-controller `@ExceptionHandler`** — one handler covers all controllers, present and future. Spring MVC's exception resolution chain checks advice before any controller-level handler.

2. **`standaloneSetup()` MockMvc over `@WebMvcTest`** — Spring Boot 4.0.6 removed `@WebMvcTest` from `spring-boot-test-autoconfigure`. Standalone MockMvc wires exactly the controller + advice under test with no Spring context overhead; runs in ~50ms vs seconds.

3. **`spring-boot-starter-test` explicitly in acquiring-service pom** — `spring-boot-starter-test` inside `test-support` (compile scope) did not transitively reach acquiring-service's test classpath in Maven's scope-mediation model. Explicit declaration fixes the gap.

4. **`ApiError` with nullable `message` and `violations`** — single response type covers both validation errors (violations list set, message null) and single-message errors (message set, violations null). Clients distinguish by checking which field is non-null.

## Test coverage

| Class | Tests | Approach |
|---|---|---|
| `GlobalExceptionHandler` | 6 | Unit — MockMvc standaloneSetup |
| QA scenarios | 2 | REST channel via qa-orchestrator |

## QA scenarios

| Scenario | Run |
|---|---|
| `validation-missing-field` | `rest-validation-run` |
| `validation-bad-format` | `rest-validation-run` |

## How to verify

```bash
# Missing field → 400 with violations
curl -X POST http://localhost:8080/qr/generate \
  -H 'Content-Type: application/json' \
  -d '{"terminalId":"TERM0001","amount":"100.00","currency":"INR","orderId":"O1"}'
# → {"status":400,"error":"Validation Failed","violations":[{"field":"merchantId","message":"must not be blank"}]}

# Bad amount format → 400 with field name
curl -X POST http://localhost:8080/qr/generate \
  -H 'Content-Type: application/json' \
  -d '{"merchantId":"MERCH0000999","terminalId":"TERM0001","amount":"100","currency":"INR","orderId":"O1"}'
# → {"status":400,"error":"Validation Failed","violations":[{"field":"amount","message":"must be a decimal with 2 places e.g. 100.00"}]}

# Malformed JSON → 400 Bad Request
curl -X POST http://localhost:8080/qr/generate \
  -H 'Content-Type: application/json' \
  -d 'not json'
# → {"status":400,"error":"Bad Request","message":"Malformed or missing JSON body"}
```
