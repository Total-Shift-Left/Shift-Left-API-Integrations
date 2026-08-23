# Contributing

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

## Developing

```bash
npm install     # links ci-client into the packages that depend on it
npm test        # ci-client, the Action contract, the Azure adapter
```

`github-actions/dist/` is committed on purpose: GitHub runs it directly with no install step.
Rebuild it with `npm run build:action` in the same change whenever `ci-client` or the Action's
source changes — CI fails if the committed bundle is stale.

Packaged builds are **not** committed. `.hpi` and `.vsix` files are gitignored: a binary sitting
next to the source it was built from goes stale silently, and a stale one has already shipped a
build-passes-on-the-previous-run defect. Build them locally when you need them, and attach them to
a release tag when you ship them.

```bash
mvn -B -ntp verify              # in jenkins/  → target/*.hpi
npm run build:azure-task        # from the root; the Azure task ships an ncc bundle, not node_modules
npx tfx-cli extension create    # in azure-devops/ → *.vsix
```

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

Attach the built `.hpi` and `.vsix` to their GitHub release so the install instructions in
[`jenkins/README.md`](jenkins/README.md) and [`azure-devops/README.md`](azure-devops/README.md) have
something to point at.
