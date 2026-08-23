# Shift-Left Studio CircleCI orb

Run [Shift-Left Studio](https://totalshiftleft.ai) API test packs from CircleCI.

The orb wraps [`@totalshiftleft/ci`](../ci-client) — the single implementation behind
every Total Shift Left CI integration — so it behaves identically to the GitHub Action, the Azure
DevOps task, the Bitbucket pipe and the GitLab component. Nothing is reimplemented here.

> **Not published yet.** The orb has not been pushed to the CircleCI registry. See
> [Publishing](#publishing) for the exact commands.

## Layout

This directory is the orb source tree, in the layout `circleci orb pack` expects:

```
src/
  @orb.yml                  orb description + display info
  commands/run-test-pack.yml
  jobs/api-tests.yml
  examples/basic.yml
```

## Install

Once published:

```yaml
version: 2.1

orbs:
  shiftleft: totalshiftleft/shiftleft@1.0.0
```

## Credentials

Credentials are passed **by variable name**, never by value — this is the CircleCI convention and
it keeps secrets out of `.circleci/config.yml`. Add them as context or project variables:

| Variable | Contents |
|---|---|
| `SHIFTLEFT_API_EMAIL` | Email of the API user. Its role must be allowed on the public API. |
| `SHIFTLEFT_API_PASSWORD` | Password for that user. |

The `api-email` / `api-password` parameters are of CircleCI's `env_var_name` type and default to
those names, so you only override them if your variables are called something else. The runner
never prints the password.

## Usage

The ready-made job, which also publishes artifacts and test results:

```yaml
version: 2.1

orbs:
  shiftleft: totalshiftleft/shiftleft@1.0.0

workflows:
  api-tests:
    jobs:
      - shiftleft/api-tests:
          context: shiftleft
          server-url: https://tenant.totalshiftleft.ai
          pack-id: pack_123
          pass-threshold-percent: 95
```

Or the command, inside a job of your own:

```yaml
jobs:
  build:
    docker:
      - image: cimg/node:20.11
    steps:
      - checkout
      - shiftleft/run-test-pack:
          server-url: https://tenant.totalshiftleft.ai
          pack-id: pack_123
          tenant-id: acme
```

The executor must provide Node.js 20+ / `npx`; the command runs `npx -y @totalshiftleft/ci`.

## Parameters

Shared by `run-test-pack` (command) and `api-tests` (job). Each maps to one `SHIFTLEFT_*`
environment variable of the runner contract.

| Parameter | Type | Default | Meaning |
|---|---|---|---|
| `server-url` | string | *required* | Base URL of your deployment, no trailing slash |
| `pack-id` | string | *required* | Test run pack to execute |
| `api-email` | env_var_name | `SHIFTLEFT_API_EMAIL` | Name of the variable holding the API user email |
| `api-password` | env_var_name | `SHIFTLEFT_API_PASSWORD` | Name of the variable holding the API user password |
| `tenant-id` | string | `""` | Multi-tenant installs only (`X-Tenant-ID`) |
| `wait-for-completion` | boolean | `true` | `false` triggers and returns immediately |
| `poll-interval-seconds` | integer | `10` | |
| `timeout-minutes` | integer | `60` | |
| `pass-threshold-percent` | integer | `100` | Minimum pass rate; `0` disables the threshold |
| `fail-on-error-tests` | boolean | `true` | Any test in `ERROR` fails the gate |
| `gate-failure-result` | enum | `failed` | Or `succeeded-with-issues` to warn instead of fail |
| `write-json-summary` | boolean | `true` | |
| `json-summary-path` | string | `shiftleft-test-pack-summary.json` | |
| `write-test-results-xml` | boolean | `true` | JUnit XML |
| `test-results-xml-path` | string | `shiftleft-test-pack-results.xml` | |
| `working-directory` | string | `""` | Where artifacts are written; empty means the job's working directory |
| `step-name` | string | `Run Shift-Left Studio test pack` | Command only — step label in the UI |

The `api-tests` job adds `executor-image` (`cimg/base:current`), `resource-class` (`medium`) and
`checkout` (`false`).

## Outputs

CircleCI has no step-output mechanism, so the runner's outputs are printed to the job log as
`[ShiftLeft] Output <name>=<value>`:

`execution_id`, `trigger_execution_id`, `decision`, `success_rate`, `json_summary_path`,
`test_results_xml_path`, `passed`, `task_completion`.

The same values are in the JSON summary artifact.

### Gate decisions

`PASSED`, `GATE_FAIL_THRESHOLD`, `GATE_FAIL_ERROR_TESTS`, `FAILED`, `COMPLETED_WITH_ISSUES`, `OK`,
`TIMEOUT`, `TRIGGER_ONLY`.

The step exits `0` when the gate passes and `1` when it fails, so it gates the build.

## Publishing

Pending manual steps — none of this has been run. From this directory:

```bash
# 1. Validate the packed orb
circleci orb pack src > orb.yml
circleci orb validate orb.yml

# 2. One-time: create the namespace and the orb (skip whichever already exists)
circleci namespace create totalshiftleft <vcs-type> <org-name>
circleci orb create totalshiftleft/shiftleft

# 3. Publish a dev version to smoke-test in a real pipeline
circleci orb publish orb.yml totalshiftleft/shiftleft@dev:first

# 4. Promote to an immutable production version
circleci orb publish promote totalshiftleft/shiftleft@dev:first patch
#    or publish a version directly:
circleci orb publish orb.yml totalshiftleft/shiftleft@1.0.0
```

Before the first production publish, add a `display.source_url` to `src/@orb.yml` pointing at the
public source mirror — the registry expects one, and this repository is not public.
