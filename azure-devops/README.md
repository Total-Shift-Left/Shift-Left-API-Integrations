# Shift-Left API Automation Integration — Azure DevOps extension

Published on the **Visual Studio Marketplace** under **Total Shift Left** (publisher id: **`totalshiftleft`**, Commercial offering). Extension support: **support@totalshiftleft.com**.

Seller ID and Partner ID from Partner Center are account metadata only—they are **not** stored in `vss-extension.json`; sign in to [Partner Center](https://partner.microsoft.com/dashboard) with your publisher account to manage them.

Adds an Azure Pipelines task to trigger **test run packs** on your ShiftLeft instance using the ShiftLeft **public API** (`/api/v1`): login, run, status poll, and detailed results. Optional header `X-Tenant-ID` when `tenantId` is set.

**Note:** In pipeline YAML, reference the task as **`totalshiftleft.shiftleft-api-integration-task@1`** (or the major version you installed).

## Prerequisites

- Node.js **20+** on the agent (task handler `Node20_1`).
- Network access from the agent to your ShiftLeft server URL.
- A user with permission to call the ShiftLeft CI/CD public API.

## Credentials (v1)

Do **not** commit passwords. Recommended:

1. Create a **variable group** (Pipelines → Library) with:
   - `ShiftLeftEmail` — plain variable  
   - `ShiftLeftPassword` — **secret**  
2. Link the group to your pipeline and reference variables in the task inputs.

Alternatively, define **secret pipeline variables** in the UI or via YAML `variables`.

## YAML example

After installing the extension, reference the task as **`totalshiftleft.shiftleft-api-integration-task@Major`** (publisher id from [`vss-extension.json`](vss-extension.json), task `name` from [`shiftleft-api-integration-task/task.json`](shiftleft-api-integration-task/task.json)).

```yaml
variables:
  - group: shiftleft-secrets   # contains ShiftLeftEmail, ShiftLeftPassword

steps:
  - task: totalshiftleft.shiftleft-api-integration-task@1
    displayName: Run ShiftLeft pack
    inputs:
      serverUrl: 'https://your-shiftleft-host'
      tenantId: ''                    # optional
      apiEmail: '$(ShiftLeftEmail)'
      apiPassword: '$(ShiftLeftPassword)'
      packId: 'your_pack_id'
      waitForCompletion: true
      pollIntervalSeconds: '10'
      timeoutMinutes: '60'
      passThresholdPercent: '100'
      failOnErrorTests: true
      gateFailureResult: 'failed'     # or succeededWithIssues
      writeJsonSummary: true
      jsonSummaryPath: 'shiftleft-test-pack-summary.json'
      writeTestResultsXml: true
      testResultsXmlPath: 'shiftleft-test-pack-results.xml'
      workingDirectory: '$(Build.SourcesDirectory)'

  - task: PublishTestResults@2
    displayName: Publish ShiftLeft test results
    condition: succeededOrFailed()
    inputs:
      testResultsFormat: JUnit
      testResultsFiles: '**/shiftleft-test-pack-results.xml'
      searchFolder: '$(Build.SourcesDirectory)'
      mergeTestResults: true
      failTaskOnFailedTests: false

  - task: PublishPipelineArtifact@1
    displayName: Upload JSON summary
    condition: always()
    inputs:
      targetPath: '$(Build.SourcesDirectory)/shiftleft-test-pack-summary.json'
      artifact: 'shiftleft-summary'
```

### Output variables

Give the task a `name:` (e.g. `ShiftLeft`) and read:

- `$(ShiftLeft.shiftLeftExecutionId)` — execution id from status/results  
- `$(ShiftLeft.shiftLeftTriggerExecutionId)` — id returned from the trigger response  
- `$(ShiftLeft.shiftLeftDecision)` — gate code (`PASSED`, `GATE_FAIL_THRESHOLD`, `TIMEOUT`, etc.)

## Extension icon (Marketplace / organization extensions)

The manifest points to [`images/extension-icon.png`](images/extension-icon.png) (**128×128** PNG for Marketplace / extension details). The task folder also includes [`shiftleft-api-integration-task/icon.png`](shiftleft-api-integration-task/icon.png) (**32×32** for the pipeline task catalog). Both are generated from the Total Shift Left mark:

- **Source SVG (dev-only, not packaged):** [`icon-source/total-shift-left-icon.svg`](icon-source/total-shift-left-icon.svg) — same artwork as [`frontend/logo/Icon/SVG/Total Shift Left Icon.svg`](../frontend/logo/Icon/SVG/Total%20Shift%20Left%20Icon.svg). **Do not put SVG under `images/`:** Azure DevOps / Marketplace validation rejects SVG files inside the VSIX; only PNG (or JPG) belongs in the packaged `images/` folder.

Flow-line artwork stays in [`frontend/public/`](../frontend/public/) (`tsl_flow*.svg`); it is not copied into this extension package.

After editing the SVG, regenerate the PNG (requires dev dependencies):

```bash
cd shiftleft-api-integration-task
npm ci
npm run generate-extension-icon
```

Commit the updated `images/extension-icon.png` and `shiftleft-api-integration-task/icon.png` before packaging. The [`azure-pipelines.yml`](azure-pipelines.yml) CI job runs `generate-extension-icon` on Linux to verify the script (and Sharp) work on agents.

## Build the `.vsix`

Azure Pipelines agents do **not** run an install step for a task, so every dependency has to be
inside the VSIX. This is an npm workspaces repo — dependencies hoist to the repository-root
`node_modules` and `shiftleft-api-integration-task/node_modules` never exists — so the task is
bundled into a single self-contained file with [ncc](https://github.com/vercel/ncc) instead, the
same way the GitHub Action is. `task.json` runs `dist/index.js`; `index.js` is the source.

1. **Build the task bundle** (from the repository root):

   ```bash
   npm run build:azure-task
   ```

   This writes `shiftleft-api-integration-task/dist/`. It is gitignored — rebuild it before every
   package.

2. **Install** [TFX CLI](https://github.com/microsoft/tfs-cli): `npm install -g tfx-cli`

3. **Create the package** (run in this directory, the one that contains `vss-extension.json`):

   ```bash
   tfx extension create --manifest-globs vss-extension.json
   ```

   This produces a VSIX named like **`totalshiftleft.shift-left-api-automation-integration-{version}.vsix`** (from `publisher` + extension `id` in the manifest).

4. **Check what you built.** A VSIX missing a dependency packages cleanly and only fails on the
   agent, at `Cannot find module`. Unzip it and load the entry point for real:

   ```bash
   unzip -o totalshiftleft.*.vsix -d /tmp/vsix && cd /tmp/vsix/shiftleft-api-integration-task
   node -e "require('./dist/index.js')"
   ```

   CI does the same thing on every push. `__tests__/packaging.test.js` guards the manifest's file
   list against drifting away from `task.json`.

5. Upload the `.vsix` in Azure DevOps: **Organization settings → Extensions → Shared → Upload**.

## Publish to the Visual Studio Marketplace

Live listing: [`TotalShiftLeft.shift-left-api-automation-integration`](https://marketplace.visualstudio.com/items?itemName=totalshiftleft.shift-left-api-automation-integration). The publisher is verified and the extension is public, so a publish goes straight to Marketplace validation with no sharing step.

**A published version can never be replaced.** The Marketplace rejects a publish whose version already exists, so every release needs a bump first — see the checklist below.

```bash
npm run build:azure-task                      # from the repository root
cd azure-devops
tfx extension publish --manifest-globs vss-extension.json --token <PAT>
```

The PAT comes from **[dev.azure.com](https://dev.azure.com) → User settings → Personal access tokens**, and must be created with:

- **Organization:** *All accessible organizations* (Marketplace tokens are rejected if scoped to a single org)
- **Scopes:** *Marketplace → Manage*

Validation takes a few minutes after the upload returns; the listing shows the new version once it passes.

### Before each release

- Confirm `publisher` in [`vss-extension.json`](vss-extension.json) remains **`totalshiftleft`** (Total Shift Left).
- Bump the version in all three places — `vss-extension.json`, `shiftleft-api-integration-task/task.json` (`Major`/`Minor`/`Patch`) and `shiftleft-api-integration-task/package.json`. They must match: `__tests__/packaging.test.js` fails if they drift.

  Bumping `task.json` is not cosmetic. Agents cache task code by that version, so an unbumped task keeps running the previously cached bundle even after the extension updates.
- Check the version is not already on the Marketplace — the publish fails if it is:

  ```bash
  curl -sS -X POST https://marketplace.visualstudio.com/_apis/public/gallery/extensionquery \
    -H 'Accept: application/json;api-version=3.0-preview.1' -H 'Content-Type: application/json' \
    -d '{"filters":[{"criteria":[{"filterType":7,"value":"totalshiftleft.shift-left-api-automation-integration"}]}],"flags":914}'
  ```
- Attach the same `.vsix` to the `azure-devops/vX.Y.Z` GitHub release so the two stay in step.

## Develop & test

```bash
cd shiftleft-api-integration-task
npm ci
npm test
```

## CI pipeline for this folder

See [`azure-pipelines.yml`](azure-pipelines.yml) — run tests and produce a VSIX artifact (optional: wire this file as a pipeline in your project).

## Quality gate mapping

How the task sets the Azure Pipelines step outcome:

| Task result | Meaning |
|-------------|---------|
| **Succeeded** | Gate passed; run completed as expected. |
| **Failed** | Gate or run failed; use when **gate failure result** is `failed` (input `gateFailureResult`). |
| **Succeeded with issues** | Gate failed but you chose a non-blocking outcome; set **gate failure result** to `succeededWithIssues`. |

Input **`gateFailureResult`** (`failed` vs `succeededWithIssues`) controls whether threshold or error-test failures mark the step as failed or as succeeded with issues. Other outcomes (for example completed-with-issues from the pack) still map to succeeded with issues when applicable.
