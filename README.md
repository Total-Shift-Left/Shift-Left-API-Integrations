# Shift-Left API Integrations

Run [Shift-Left Studio](https://totalshiftleft.ai) API test packs from your CI pipeline: trigger a
test run pack, wait for it, gate the build on its pass rate, and publish JSON + JUnit results.

## Pick your platform

| Platform | Directory | Install |
|---|---|---|
| GitHub Actions | [`github-actions/`](github-actions) | `uses: Total-Shift-Left/Shift-Left-API-Integrations/github-actions@v1` |
| Jenkins | [`jenkins/`](jenkins) | Upload the `.hpi` in Manage Jenkins → Plugins → Advanced |
| Azure DevOps | [`azure-devops/`](azure-devops) | Install the extension, then add the task to a pipeline |
| CircleCI | [`circleci-orb/`](circleci-orb) | `orbs: shiftleft: totalshiftleft/shiftleft@1` |
| Bitbucket Pipelines | [`bitbucket-pipe/`](bitbucket-pipe) | `- pipe: totalshiftleft/shiftleft-test-pack:1.0.0` |
| GitLab CI | [`gitlab-component/`](gitlab-component) | `include: component:` |
| Anything else | [`ci-client/`](ci-client) | `npx -y @totalshiftleft/ci` or `totalshiftleft/ci-runner` |

Bamboo, TeamCity, AWS CodeBuild, Buildkite, Drone, Harness, Tekton and Argo are all covered by the
last row — any CI that can run Node or a container can run the test pack.

## One implementation

Everything here is a thin adapter over [`ci-client/`](ci-client) (`@totalshiftleft/ci`), which owns
the API client, the polling loop, the quality gate and the artifact writers. **The full contract —
every input, default, output and gate decision code — is documented once, in
[`ci-client/README.md`](ci-client/README.md).** Platform directories only document what is specific
to that platform.

A platform adapter supplies `{ getInput, setOutput, setFailed, info, warning, debug }` and nothing
else. Adding a CI system means writing that adapter — never reimplementing polling or the gate.
Jenkins is the exception: it is Java, and implements the same contract independently, so changes to
polling or gate semantics must be made twice.

## Status

| Artifact | Published |
|---|---|
| `@totalshiftleft/ci` (npm) | ❌ not yet |
| `totalshiftleft/ci-runner` (Docker Hub) | ❌ not yet |
| GitHub Action `@v1` | ❌ not tagged yet |
| Jenkins `.hpi` | ⚠️ a build is committed under `jenkins/`, but it is **outdated** — see below |
| Azure DevOps `.vsix` | ⚠️ a build is committed under `azure-devops/`, but it is **outdated** — see below |
| CircleCI orb | ❌ not yet |
| Bitbucket pipe | ❌ not yet |
| GitLab component | ❌ not yet |

### ⚠️ The committed binaries are out of date

`jenkins/*.hpi` and `azure-devops/*.vsix` were built before a correctness fix landed. They contain a
defect where the plugin can read the **previous** test run's results and pass a build whose new run
has not finished — a pipeline goes green on stale data. The source in this repository is fixed; the
binaries are not. Rebuild from source (`mvn -B -ntp verify` in `jenkins/`) or wait for the first
tagged release rather than shipping these into a pipeline you rely on.

## Developing

```bash
npm install     # links ci-client into the packages that depend on it
npm test        # ci-client, the Action contract, the Azure adapter
```

`github-actions/dist/` is committed on purpose: GitHub runs it directly with no install step.
Rebuild it with `npm run build:action` in the same change whenever `ci-client` or the Action's
source changes — CI fails if the committed bundle is stale.

## Releasing

This repository holds several products, so tags are prefixed — except the Action, where `uses:`
convention expects a floating major tag:

| Product | Tag |
|---|---|
| GitHub Action | `v1` (floating) and `action/v1.0.0` |
| Jenkins plugin | `jenkins/v1.0.0` |
| Azure DevOps extension | `azure-devops/v1.0.0` |
| `@totalshiftleft/ci` | `ci-client/v1.0.0` |

Publishing targets — npm, Docker Hub, the Visual Studio Marketplace, the CircleCI orb registry and
the Jenkins Update Center — each need their own credentials and are done deliberately, not on every
push. Publish `@totalshiftleft/ci` to npm **first**: the orb, the pipe, the component and several
documentation pages all instruct users to fetch it from there.

## License

MIT — see [LICENSE](LICENSE).
