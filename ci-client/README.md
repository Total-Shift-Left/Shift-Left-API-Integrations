# @totalshiftleft/ci

Run [Shift-Left Studio](https://totalshiftleft.ai) test packs from any CI system.

Triggers a test run pack over the public API, waits for **that** run to finish, applies a quality
gate, and writes a JSON summary plus JUnit XML your CI can publish.

This package is the single implementation behind every Total Shift Left CI integration — the
GitHub Action, the Azure DevOps task, the CircleCI orb, the Bitbucket pipe and the GitLab
component all call it, so they behave identically.

## Use it

Any CI system, via the CLI:

```bash
npx @totalshiftleft/ci
```

Or the container, for agents without Node:

```bash
docker run --rm \
  -e SHIFTLEFT_SERVER_URL="$SHIFTLEFT_URL" \
  -e SHIFTLEFT_API_EMAIL="$SHIFTLEFT_EMAIL" \
  -e SHIFTLEFT_API_PASSWORD="$SHIFTLEFT_PASSWORD" \
  -e SHIFTLEFT_PACK_ID="$SHIFTLEFT_TEST_PACK_ID" \
  -v "$PWD:/workspace" -w /workspace \
  totalshiftleft/ci-runner:1
```

Exit code is `0` when the gate passes and `1` when it fails, so the step gates the build.

## Configuration

| Environment variable | Default | Meaning |
|---|---|---|
| `SHIFTLEFT_SERVER_URL` | *required* | Base URL of your deployment, no trailing slash |
| `SHIFTLEFT_API_EMAIL` | *required* | API user — needs a role allowed on the public API |
| `SHIFTLEFT_API_PASSWORD` | *required* | Store as a secret |
| `SHIFTLEFT_PACK_ID` | *required* | Test run pack to execute |
| `SHIFTLEFT_TENANT_ID` | — | Multi-tenant installs only (`X-Tenant-ID`) |
| `SHIFTLEFT_WAIT_FOR_COMPLETION` | `true` | `false` triggers and returns immediately |
| `SHIFTLEFT_POLL_INTERVAL_SECONDS` | `10` | |
| `SHIFTLEFT_TIMEOUT_MINUTES` | `60` | |
| `SHIFTLEFT_PASS_THRESHOLD_PERCENT` | `100` | Minimum pass rate; `0` disables the threshold |
| `SHIFTLEFT_FAIL_ON_ERROR_TESTS` | `true` | Any test in `ERROR` fails the gate |
| `SHIFTLEFT_GATE_FAILURE_RESULT` | `failed` | Or `succeeded-with-issues` to warn instead of fail |
| `SHIFTLEFT_WRITE_JSON_SUMMARY` | `true` | |
| `SHIFTLEFT_JSON_SUMMARY_PATH` | `shiftleft-test-pack-summary.json` | |
| `SHIFTLEFT_WRITE_TEST_RESULTS_XML` | `true` | JUnit XML |
| `SHIFTLEFT_TEST_RESULTS_XML_PATH` | `shiftleft-test-pack-results.xml` | |
| `SHIFTLEFT_WORKING_DIRECTORY` | auto | Where artifacts are written. Detected from the CI's own workspace variable when unset |

## Outputs

Printed as `[ShiftLeft] Output <name>=<value>`, and set as step outputs by the host integrations:

`execution_id`, `trigger_execution_id`, `decision`, `success_rate`, `json_summary_path`,
`test_results_xml_path`, `passed`, `task_completion`.

### Gate decisions

`PASSED`, `GATE_FAIL_THRESHOLD`, `GATE_FAIL_ERROR_TESTS`, `FAILED`, `COMPLETED_WITH_ISSUES`, `OK`,
`TIMEOUT`, `TRIGGER_ONLY`.

## Use it as a library

```js
const { runShiftLeft, runShiftLeftFromEnv } = require('@totalshiftleft/ci');

// Any host that can supply { getInput, setOutput, setFailed, info, warning, debug }
await runShiftLeft(core, { userAgent: 'my-integration/1.0.0' });
```

## Why it waits for a *new* execution

`GET /status` reports a pack's most recent execution, and `POST /run` returns before the scheduler
has claimed the pack. A client that polls immediately can therefore read the **previous** run's
`COMPLETED` and pass a build whose new run has not started. This package records the executionId
before triggering and treats a run as complete only once that id changes. Do not remove that check;
it cannot be fixed server-side, because `/status` keeps reporting the old execution until the new
run writes its first summary.

## Development

```bash
npm test --workspace=ci-client
```
