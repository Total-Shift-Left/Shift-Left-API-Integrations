# Shift-Left Studio Bitbucket pipe

Run [Shift-Left Studio](https://totalshiftleft.ai) API test packs from Bitbucket Pipelines.

The pipe wraps [`@totalshiftleft/ci`](../ci-client) via the `totalshiftleft/ci-runner`
image — the single implementation behind every Total Shift Left CI integration — so it behaves
identically to the GitHub Action, the Azure DevOps task, the CircleCI orb and the GitLab
component. Nothing is reimplemented here.

> **Not published yet.** Neither the pipe image nor a Bitbucket pipes-catalog entry exists. See
> [Publishing](#publishing) for the exact commands.

## Usage

```yaml
pipelines:
  default:
    - step:
        name: API tests
        script:
          - pipe: totalshiftleft/shiftleft-test-pack:1.0.0
            variables:
              SHIFTLEFT_SERVER_URL: 'https://tenant.totalshiftleft.ai'
              SHIFTLEFT_API_EMAIL: $SHIFTLEFT_API_EMAIL
              SHIFTLEFT_API_PASSWORD: $SHIFTLEFT_API_PASSWORD
              SHIFTLEFT_PACK_ID: 'pack_123'
              SHIFTLEFT_PASS_THRESHOLD_PERCENT: '95'
        artifacts:
          - shiftleft-test-pack-summary.json
          - shiftleft-test-pack-results.xml
```

Until the pipe image is published, the same thing works by referencing the runner image directly:

```yaml
          - pipe: docker://totalshiftleft/ci-runner:1
            variables:
              SHIFTLEFT_SERVER_URL: 'https://tenant.totalshiftleft.ai'
              ...
```

Bitbucket picks up JUnit XML automatically from `**/test-results/**` and a few other well-known
locations. To get the run into the **Tests** tab, point the XML at one of them:

```yaml
              SHIFTLEFT_TEST_RESULTS_XML_PATH: 'test-results/shiftleft.xml'
```

### Credentials

Define `SHIFTLEFT_API_EMAIL` and `SHIFTLEFT_API_PASSWORD` as **secured** repository, deployment
or workspace variables (Repository settings → Repository variables → *Secured*). Secured
variables are masked in the log, and the runner never prints the password.

## Variables

Every variable is the runner's own `SHIFTLEFT_*` environment contract — the pipe passes them
straight through.

| Variable | Default | Meaning |
|---|---|---|
| `SHIFTLEFT_SERVER_URL` | *required* | Base URL of your deployment, no trailing slash |
| `SHIFTLEFT_API_EMAIL` | *required* | API user — needs a role allowed on the public API |
| `SHIFTLEFT_API_PASSWORD` | *required* | Store as a secured variable |
| `SHIFTLEFT_PACK_ID` | *required* | Test run pack to execute |
| `SHIFTLEFT_TENANT_ID` | `""` | Multi-tenant installs only (`X-Tenant-ID`) |
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
| `SHIFTLEFT_WORKING_DIRECTORY` | auto | Where artifacts are written. Defaults to `$BITBUCKET_CLONE_DIR` |

## Outputs

Printed to the step log as `[ShiftLeft] Output <name>=<value>`:

`execution_id`, `trigger_execution_id`, `decision`, `success_rate`, `json_summary_path`,
`test_results_xml_path`, `passed`, `task_completion`.

The same values are in the JSON summary artifact.

### Gate decisions

`PASSED`, `GATE_FAIL_THRESHOLD`, `GATE_FAIL_ERROR_TESTS`, `FAILED`, `COMPLETED_WITH_ISSUES`, `OK`,
`TIMEOUT`, `TRIGGER_ONLY`.

The pipe exits `0` when the gate passes and `1` when it fails, so it gates the build.

## Publishing

Pending manual steps — none of this has been run.

The wrapper is `FROM totalshiftleft/ci-runner:1`, so publish the runner image first (from
`ci-client/`), then this one:

```bash
# 1. Base runner image, if it is not on Docker Hub yet
docker build -t totalshiftleft/ci-runner:1 ../ci-client
docker push totalshiftleft/ci-runner:1

# 2. The pipe image
docker build -t totalshiftleft/shiftleft-test-pack:1.0.0 \
             -t totalshiftleft/shiftleft-test-pack:1 \
             -t totalshiftleft/shiftleft-test-pack:latest .
docker login
docker push totalshiftleft/shiftleft-test-pack:1.0.0
docker push totalshiftleft/shiftleft-test-pack:1
docker push totalshiftleft/shiftleft-test-pack:latest
```

Then update `image:` in `pipe.yml` to `totalshiftleft/shiftleft-test-pack:1.0.0`, since Bitbucket
resolves a `pipe:` reference to the Docker image of the same coordinates.

Listing in the Bitbucket pipes catalog is a separate, manual submission to Atlassian; the pipe
works from Docker Hub without it.
