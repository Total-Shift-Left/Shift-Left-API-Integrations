# Shift-Left API Automation Integration

Azure DevOps extension that runs **ShiftLeft** API **test packs** in **Azure Pipelines**: authenticate, trigger a pack, wait for completion, apply **quality gates**, and optionally write **XML** (for **Publish Test Results**) and a **JSON** summary.

**Publisher:** [totalshiftleft](https://marketplace.visualstudio.com/publishers/totalshiftleft)  
**Marketplace:** [Shift-Left API Automation Integration](https://marketplace.visualstudio.com/items?itemName=totalshiftleft.shift-left-api-automation-integration)  
**Support:** [support@totalshiftleft.com](mailto:support@totalshiftleft.com)

[![Install from Marketplace](https://img.shields.io/badge/Azure%20DevOps-Marketplace-blue)](https://marketplace.visualstudio.com/items?itemName=totalshiftleft.shift-left-api-automation-integration)

---

## Install

### From the Marketplace (recommended)

1. In Azure DevOps: **Organization settings** → **Extensions** → **Browse marketplace**.
2. Search for **Shift-Left API Automation Integration** and install.

Or open the listing directly: [Shift-Left API Automation Integration](https://marketplace.visualstudio.com/items?itemName=totalshiftleft.shift-left-api-automation-integration).

### From a downloaded `.vsix`

If you prefer not to use the Marketplace (or your org requires a packaged file):

1. Download the `.vsix` from this repository’s **Releases** page. Release assets follow this name pattern (semantic version **`major.minor.patch`**):

   ```text
   totalshiftleft.shift-left-api-automation-integration-<major>.<minor>.<patch>.vsix
   ```

   Example: `totalshiftleft.shift-left-api-automation-integration-1.0.0.vsix`.

2. In Azure DevOps: **Organization settings** → **Extensions** → **Shared** → **Upload** and select the file.

You need permission to manage extensions for the organization. Updates require uploading a newer `.vsix` when a new release is published.

---

## Using the task in YAML

Reference:

```text
totalshiftleft.shiftleft-api-integration-task@<major>
```

Use **`@1`** with a **1.x** extension version. Optional: `tenantId` and `X-Tenant-ID` for tenant-scoped ShiftLeft deployments.

**Prerequisites:** Node.js **20+** on the agent, network access to your ShiftLeft URL, and a user allowed to call the ShiftLeft **CI/CD public API**.

---

## Credentials

Do **not** commit passwords in your **application** repo or paste them into YAML.

Use a [variable group](https://learn.microsoft.com/en-us/azure/devops/pipelines/library/variable-groups) (e.g. `ShiftLeftEmail` + secret `ShiftLeftPassword`) or [secret pipeline variables](https://learn.microsoft.com/en-us/azure/devops/pipelines/process/variables#secret-variables), then reference `$(ShiftLeftEmail)` / `$(ShiftLeftPassword)` in the task.

---

## YAML example

```yaml
variables:
  - group: shiftleft-secrets   # ShiftLeftEmail, ShiftLeftPassword (secret)

steps:
  - task: totalshiftleft.shiftleft-api-integration-task@1
    name: ShiftLeft
    displayName: Run ShiftLeft test pack
    inputs:
      serverUrl: 'https://your-shiftleft-host'
      tenantId: ''                              # optional
      apiEmail: '$(ShiftLeftEmail)'
      apiPassword: '$(ShiftLeftPassword)'
      packId: 'your_pack_id'
      waitForCompletion: true
      pollIntervalSeconds: '10'
      timeoutMinutes: '60'
      passThresholdPercent: '100'
      failOnErrorTests: true
      gateFailureResult: 'failed'               # or succeededWithIssues
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

### Required inputs

| Input | Description |
|-------|-------------|
| `serverUrl` | ShiftLeft API base URL (no trailing slash). |
| `apiEmail` | Login email for `/api/v1/login`. |
| `apiPassword` | Login password (secret variable). |
| `packId` | Test pack id to run. |

Other options (poll interval, timeout, quality gate, output paths) have defaults or are visible in the pipeline task assistant.

### Output variables

With `name: ShiftLeft` on the task:

| Macro | Meaning |
|-------|---------|
| `$(ShiftLeft.shiftLeftExecutionId)` | Execution id from status/results. |
| `$(ShiftLeft.shiftLeftTriggerExecutionId)` | Id from the trigger response. |
| `$(ShiftLeft.shiftLeftDecision)` | Gate decision (e.g. `PASSED`, `GATE_FAIL_THRESHOLD`, `TIMEOUT`). |

### Quality gate vs pipeline result

| Azure DevOps result | When |
|---------------------|------|
| **Succeeded** | Gate passed as expected. |
| **Failed** | Gate/run failed and `gateFailureResult` is `failed`. |
| **Succeeded with issues** | `gateFailureResult` is `succeededWithIssues`, or completed-with-issues per task logic. |

---

## More help

- [Total Shift Left — publisher](https://marketplace.visualstudio.com/publishers/totalshiftleft)
- [Azure DevOps — Microsoft-hosted agents](https://learn.microsoft.com/en-us/azure/devops/pipelines/agents/hosted)

---

**Shift-Left API Automation Integration** is a commercial offering from **Total Shift Left**. For product and integration questions: **[support@totalshiftleft.com](mailto:support@totalshiftleft.com)**.
