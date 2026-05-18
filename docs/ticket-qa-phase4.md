# Ticket QA Phase 4 — Recorder, HAR Importer, Playwright, Scheduler

## What
Extends the QA orchestrator with four capabilities that close the recorder-first authoring loop
and make suite execution fully automated:

1. **`Iso8583RecorderProxy`** — TCP MITM proxy on port 8001 that forwards frames to
   acquiring-service:8000, captures each request/response pair, and auto-generates scenario YAML.
2. **`HarImporter`** — `POST /recorder/import-har` multipart endpoint that parses a Chrome/Firefox
   HAR file and emits a REST scenario YAML with one `send` + one `assert` step per entry.
3. **`PlaywrightTestAdapter`** — stub channel adapter for `ChannelType.PLAYWRIGHT`, conditionally
   loaded via `qa.channel.playwright.enabled=true`. Validates `data-testid` payload conventions
   and returns `StepResult.Passed` for each action (navigate / click / fill / assert_text / screenshot).
4. **`SuiteScheduler`** — `SchedulingConfigurer` that reads `TestSuite.schedule` cron expressions
   from YAML at startup and registers them dynamically; fires `TriggerSuiteUseCase` at each tick.
5. **`NotificationPort` + `WebhookNotificationAdapter`** — outbound domain port for failure alerts;
   HTTP POST to a configurable webhook URL (Slack-compatible); `NoopNotificationAdapter` is the
   fallback when no URL is set.
6. **Portal**: `/suites` page with scheduled badge, `/recorder` page with proxy controls + HAR import.

## Why
Phase 3 delivered channels and suite triggering. Phase 4 closes the feedback loop: engineers can
now record real ISO 8583 sessions from the terminal simulator and replay them as regression tests
without writing YAML by hand. Cron scheduling enables nightly regression runs with Slack alerts.

## Design decisions
- **Proxy is `@ConditionalOnProperty`** — opt-in at startup; defaults to off so port 8001 isn't
  bound unless `qa.recorder.proxy.enabled=true` is set in docker-compose.
- **PlaywrightTestAdapter is a stub** — data-testid conventions are established now; actual browser
  driving is deferred to when frontend components carry `data-testid` attributes. The stub means
  PLAYWRIGHT scenarios can be written today and activated by adding the Playwright JAR later.
- **`SchedulingConfigurer` over `@Scheduled`** — cron expressions live in YAML files, not
  annotations; `SchedulingConfigurer.configureTasks()` reads them at context startup.
- **`NotificationPort` in domain** — follows the hexagonal pattern; `WebhookNotificationAdapter`
  lives in `adapter.notification` and is created only when `qa.notifications.webhook-url` is set.

## Test coverage
- `Iso8583RecorderProxyTest` — 4 tests: initial state, approved auth YAML, declined YAML, reversal MTI
- `HarImporterTest` — 5 tests: GET entry, POST with body, multiple entries, empty entries, missing log
- `PlaywrightTestAdapterTest` — 9 tests: supports(), each action type, unknown action, missing keys
- `SuiteSchedulerTest` — 5 tests: cron registration, blank schedule skip, empty suites, failure notification, success no-notify

## How to verify
```bash
# 1. Start recorder proxy
curl -X POST http://localhost:8700/recorder/proxy/start
# Open terminal-simulator on port 8001 (instead of 8000), run a transaction

# 2. List captured YAMLs
curl http://localhost:8700/recorder/recordings

# 3. Import HAR from browser DevTools
curl -X POST http://localhost:8700/recorder/import-har \
  -F file=@my-session.har -F scenarioId=payment-flow-recorded

# 4. Check suites with schedules
curl http://localhost:8700/api/qa/suites | jq '.[] | {id, schedule}'

# 5. Portal suites page
open http://localhost:3003/suites  # shows schedule badge for suites with cron

# 6. Portal recorder page
open http://localhost:3003/recorder  # proxy start/stop, HAR import, recordings list
```
