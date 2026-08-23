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

## The contract

Every input, default, output and gate decision code is documented once, in
[`ci-client/README.md`](ci-client/README.md). Platform directories document only what is specific to
that platform — the task syntax, where credentials live, how artifacts are published.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the architecture, the build, and how releases are cut.

## License

MIT — see [LICENSE](LICENSE).
