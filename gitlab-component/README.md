# Shift-Left Studio GitLab CI/CD component

Run [Shift-Left Studio](https://totalshiftleft.ai) API test packs from GitLab CI/CD.

The component wraps [`@totalshiftleft/ci`](../ci-client) via the
`totalshiftleft/ci-runner` image — the single implementation behind every Total Shift Left CI
integration — so it behaves identically to the GitHub Action, the Azure DevOps task, the CircleCI
orb and the Bitbucket pipe. Nothing is reimplemented here.

> **Not published yet.** The component is not in the GitLab CI/CD Catalog. See
> [Publishing](#publishing) for the exact steps.

## Usage

```yaml
stages:
  - test

include:
  - component: gitlab.com/totalshiftleft/shiftleft-ci/run-test-pack@1.0.0
    inputs:
      server-url: https://tenant.totalshiftleft.ai
      pack-id: pack_123
      pass-threshold-percent: 95
```

The component generates one job (`shiftleft-test-pack` by default) in the `test` stage. Both are
inputs, so rename or restage it as needed:

```yaml
    inputs:
      job-name: api-contract-tests
      stage: verify
```

### Credentials

Define these as **masked** (and ideally protected) CI/CD variables under
Settings → CI/CD → Variables:

| Variable | Contents |
|---|---|
| `SHIFTLEFT_API_EMAIL` | Email of the API user. Its role must be allowed on the public API. |
| `SHIFTLEFT_API_PASSWORD` | Password for that user. |

The component reads them **by name** — `api-email-variable` / `api-password-variable` default to
those names — so no secret is written into `.gitlab-ci.yml` or into the component. The runner
never prints the password.

## Inputs

Each maps to one `SHIFTLEFT_*` environment variable of the runner contract.

| Input | Type | Default | Meaning |
|---|---|---|---|
| `server-url` | string | *required* | Base URL of your deployment, no trailing slash |
| `pack-id` | string | *required* | Test run pack to execute |
| `api-email-variable` | string | `SHIFTLEFT_API_EMAIL` | Name of the CI/CD variable holding the API user email |
| `api-password-variable` | string | `SHIFTLEFT_API_PASSWORD` | Name of the CI/CD variable holding the API user password |
| `tenant-id` | string | `""` | Multi-tenant installs only (`X-Tenant-ID`) |
| `wait-for-completion` | string | `true` | `false` triggers and returns immediately |
| `poll-interval-seconds` | number | `10` | |
| `timeout-minutes` | number | `60` | |
| `pass-threshold-percent` | number | `100` | Minimum pass rate; `0` disables the threshold |
| `fail-on-error-tests` | string | `true` | Any test in `ERROR` fails the gate |
| `gate-failure-result` | string | `failed` | Or `succeeded-with-issues` to warn instead of fail |
| `write-json-summary` | string | `true` | |
| `json-summary-path` | string | `shiftleft-test-pack-summary.json` | |
| `write-test-results-xml` | string | `true` | JUnit XML |
| `test-results-xml-path` | string | `shiftleft-test-pack-results.xml` | |
| `working-directory` | string | `""` | Where artifacts are written; empty means `$CI_PROJECT_DIR` |
| `job-name` | string | `shiftleft-test-pack` | Name of the generated job |
| `stage` | string | `test` | Stage the job runs in; it must exist in your pipeline |
| `image` | string | `totalshiftleft/ci-runner:1` | Job image |
| `allow-failure` | boolean | `false` | Let the pipeline continue when the gate fails |

The `true`/`false` inputs are typed as strings with `options: ["true", "false"]` on purpose:
`variables:` values must be strings, and this guarantees the runner receives the literal token its
contract expects.

## Artifacts and test results

The job publishes the JSON summary and the JUnit XML as job artifacts (`when: always`) and feeds
the XML to `artifacts:reports:junit`, so results appear in the merge request widget and the
pipeline's **Tests** tab.

## Outputs

Printed to the job log as `[ShiftLeft] Output <name>=<value>`:

`execution_id`, `trigger_execution_id`, `decision`, `success_rate`, `json_summary_path`,
`test_results_xml_path`, `passed`, `task_completion`.

The same values are in the JSON summary artifact.

### Gate decisions

`PASSED`, `GATE_FAIL_THRESHOLD`, `GATE_FAIL_ERROR_TESTS`, `FAILED`, `COMPLETED_WITH_ISSUES`, `OK`,
`TIMEOUT`, `TRIGGER_ONLY`.

The job exits `0` when the gate passes and `1` when it fails, so it gates the pipeline.

## Troubleshooting

**Permission denied writing artifacts.** `totalshiftleft/ci-runner` drops to the unprivileged
`node` user. If your runner configuration leaves `$CI_PROJECT_DIR` unwritable by that user,
override the image — the script falls back to `npx`:

```yaml
    inputs:
      image: node:20-alpine
```

## Publishing

Pending manual steps — none of this has been run. GitLab's CI/CD Catalog only publishes from a
**GitLab-hosted project** whose repository root holds `templates/` plus a `README.md`, so the
component must first be mirrored into its own project (this repository lives on Azure DevOps).

```bash
# 1. Create a GitLab project, e.g. gitlab.com/totalshiftleft/shiftleft-ci, and push this
#    directory as its repository root (templates/ + README.md at the top level).

# 2. In that project: Settings > General > Visibility > mark it as a CI/CD Catalog project.

# 3. Tag a release. The tag must be semantic-version-like.
git tag 1.0.0
git push origin 1.0.0
```

Then add a release job to the mirror's own `.gitlab-ci.yml` — the catalog entry is created by a
release, not by the tag alone:

```yaml
create-release:
  stage: deploy
  image: registry.gitlab.com/gitlab-org/release-cli:latest
  rules:
    - if: $CI_COMMIT_TAG
  script: echo "Releasing $CI_COMMIT_TAG"
  release:
    tag_name: $CI_COMMIT_TAG
    description: Shift-Left Studio CI/CD component $CI_COMMIT_TAG
```

The published component is then referenced as
`gitlab.com/totalshiftleft/shiftleft-ci/run-test-pack@1.0.0`.
