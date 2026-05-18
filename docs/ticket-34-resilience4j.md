# Ticket #34 — Resilience4j: Circuit Breaker, Bulkhead, Cache Stampede Prevention

## What
Added four resilience patterns to the acquiring-service adapter layer:
1. **Circuit breaker + bulkhead** on HSM (`ResilientHsmAdapter`) and network auth (`ResilientNetworkAuthAdapter`) — decorator wrappers with Resilience4j AOP annotations
2. **Two-level BIN cache** (`CachingBinLookupAdapter`) — Caffeine L1 (JVM-local, 60s) + Redis L2 (5min ± 10% jitter) with Redis SETNX distributed lock for stampede prevention
3. **Load shedding** — `Iso8583RequestHandler` catches `CallNotPermittedException` and returns Field 39 = "91" (switch inoperative) immediately instead of queuing behind a broken dependency
4. **Kafka backpressure** — `CircuitBreakerKafkaMonitor` listens to CB state transition events and pauses/resumes Kafka consumers when the network circuit opens/closes

## Why
Without circuit breakers, a 15-second HSM or network timeout on every 0100 message would exhaust Netty's thread pool in seconds during a downstream outage. The cascading failure would take down the acquiring-service itself — the pattern being protected causes the protector to fail.

## Design Decisions

**Decorator pattern, not annotation-on-mock**: `ResilientHsmAdapter` and `ResilientNetworkAuthAdapter` wrap the `Mock*Adapter` delegates. This keeps the mock adapters clean and means future production adapters automatically inherit the resilience layer by following the same `@Qualifier` injection pattern.

**`@Qualifier` injection to avoid circular beans**: Both resilient adapters implement the same port interface as their delegates. Spring's bean injection would create ambiguity. Using `@Qualifier("mockHsmAdapter")` (default bean name from class name) resolves the correct delegate.

**HSM circuit open → `CallNotPermittedException` propagates**: The HSM adapter has no fallback method — it lets the exception bubble to `Iso8583RequestHandler`. This is correct: HSM failure means we cannot verify ARQC, so the only safe response is "91" (not a fake approval). The ISO handler catches `CallNotPermittedException` and returns "91".

**Network circuit open → well-typed Declined("91")**: The network adapter has fallback methods returning `AuthorizationResult.Declined("91", "UPSTREAM_UNAVAILABLE")` and `ReversalResult.Failed("UPSTREAM_UNAVAILABLE")`. These propagate through the `AuthorizationService` sealed switch and map correctly to Field 39 = "91" — no exception escapes the domain boundary.

**TTL jitter ±10%**: Redis TTL is randomised per entry via `ThreadLocalRandom`. Prevents all instances from racing to refill the same BIN key at the same moment when it expires (thundering herd). Caffeine L1 uses a fixed 60s TTL — JVM-local so no cross-instance coordination needed.

**`slowCallDurationThreshold` for timeout tracking**: Resilience4j circuit breaker tracks calls exceeding the configured duration as "slow calls". When `slowCallRateThreshold`% of calls are slow, the circuit opens — effectively treating a degraded (slow) HSM the same as a failed one.

**Half-open probe = 3 calls**: `permittedNumberOfCallsInHalfOpenState: 3` limits the test probes during recovery. After `waitDurationInOpenState` the circuit allows exactly 3 calls; if ≥50% succeed, it closes — otherwise it re-opens. Prevents re-triggering open state from a single lucky/unlucky probe.

## Test Coverage

| Test class | Tests | What's covered |
|---|---|---|
| `CachingBinLookupAdapterTest` | 6 | L1 hit (delegate not called twice), Redis hit, full miss + lock, lock contention fallthrough, absent BIN, "null" sentinel |
| `ResilientAdaptersTest` | 5 | Delegate called on healthy path (authorize + reverse), fallback returns `Declined("91")` on `CallNotPermittedException`, fallback handles `RuntimeException`, `ReversalResult.Failed` on reverse fallback |

## How to Verify

```bash
# 1. Compile and unit-test
mvn test -pl adapters

# 2. Check circuit breaker health endpoint (service must be running)
curl http://localhost:8080/actuator/health | jq '.components.circuitBreakers'

# 3. Simulate open circuit — force open via Actuator
curl -X POST http://localhost:8080/actuator/circuitbreakers/network/transition \
  -H 'Content-Type: application/json' -d '{"transitionTo":"FORCE_OPEN"}'

# 4. Send a 0100 via terminal-simulator — should get Field 39 = "91" in <200ms
# 5. Reset circuit
curl -X POST http://localhost:8080/actuator/circuitbreakers/network/transition \
  -H 'Content-Type: application/json' -d '{"transitionTo":"CLOSE"}'
```
