# Ticket #143 — Frontend E2E QA — Playwright Scenarios for QA Portal

## What

- Added `data-testid` attributes to sidebar nav links, reports page stat cards, and suites page suite cards
- 4 PLAYWRIGHT-channel QA scenarios covering all major portal pages: scenarios list, runs trigger, suites page, recorder page
- `portal-ui-run.yml` run definition (STATELESS, 4 scenarios)
- `PlaywrightTestAdapter` stub (`@ConditionalOnProperty`) already present — activates with `qa.channel.playwright.enabled=true`

## Why

The QA portal itself needs automated test coverage — it is test infrastructure, not exempt from testing. Playwright matches elements by `data-testid` instead of CSS paths, making tests resilient to visual refactors. The stub adapter allows scenarios to be authored, reviewed, and run through the qa-orchestrator pipeline now; real browser execution activates when Playwright browsers are installed and the property flag is enabled.

## Design Decisions

- **Stub-first**: `PlaywrightTestAdapter` is `@ConditionalOnProperty` — the adapter logs and always passes in stub mode. Scenarios can be registered and executed in the qa-orchestrator pipeline without a real browser, making them safe to run in CI before Playwright is wired up (#130+).
- **`data-testid` naming convention**: `nav-{page}` for sidebar links, `stat-{label}` for stat cards, `{component}-{element}` for interactive controls. All kebab-case lowercase.
- **data-testid strip in prod**: `next.config.ts` must configure `compiler.reactRemoveProperties` in production builds to strip all `data-testid` attributes — this is a standing security spec §11 requirement.
- **One scenario per page**: each scenario owns exactly one page's golden path. Cross-page journeys (e.g. trigger run → watch live view) are deferred to a future integration scenario once real browser wiring lands.
- **Portal port 3003**: all `page_url` values use `http://localhost:3003` — the QA portal's dev server port.

## Test Coverage

### `data-testid` attributes added
| Component | Attribute | Element |
|---|---|---|
| `Sidebar.tsx` | `nav-scenarios`, `nav-runs`, `nav-reports`, `nav-suites`, `nav-recorder` | Nav link `<Link>` per route |
| `reports/page.tsx` | `stat-total-runs`, `stat-passed`, `stat-failed`, `stat-pass-rate` | `StatCard` wrapper `<div>` |
| `suites/page.tsx` | `suite-card` | `SuiteCard` wrapper `<div>` |
| `RecorderPanel.tsx` | Already present from Phase 4 | proxy-start-btn, proxy-stop-btn, har-file-input, har-import-btn |
| `RunTriggerForm.tsx` | Already present | run-selector, trigger-run-button |
| `scenarios/page.tsx` | Already present | scenario-card |
| `runs/page.tsx` | Already present | execution-row |
| `suites/page.tsx` | Already present | suite-trigger-btn |

### PLAYWRIGHT scenarios (`qa-portal` platform)
| Scenario | Steps | What it tests |
|---|---|---|
| `portal-scenarios-page` | navigate → assert_visible ×2 → screenshot | /scenarios renders nav link + scenario cards |
| `portal-runs-trigger` | navigate → assert_visible ×2 → fill → click → screenshot | /runs: run selector, trigger button, click trigger |
| `portal-suites-page` | navigate → assert_visible ×3 → screenshot | /suites: nav link, suite card, trigger button |
| `portal-recorder-page` | navigate → assert_visible ×5 → screenshot | /recorder: nav link, proxy start/stop, HAR controls |

## How to Verify

```bash
# Run QA orchestrator tests
mvn -pl services/qa-orchestrator test -Dgroups=unit

# Run portal-ui-run (stub mode — always passes, logs actions)
curl -X POST http://localhost:8700/api/qa/runs/trigger \
  -H 'Content-Type: application/json' \
  -d '{"runId":"portal-ui-run"}'

# Enable real browser execution (requires Playwright browsers installed)
# Add to qa-orchestrator application.yml:
#   qa.channel.playwright.enabled=true
```
