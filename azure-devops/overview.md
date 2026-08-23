# Shift-Left API Automation Integration

![Shift-Left API Automation Integration](images/extension-icon.png)

**Shift-Left API Automation Integration** brings API test run packs from the Total Shift Left platform directly into **Azure Pipelines**. Teams can trigger governed test executions, wait for completion, enforce quality gates, and surface results in standard DevOps workflows—without leaving Azure DevOps.

## Capabilities

- **Orchestrated execution** — Authenticate securely, start a test run pack, and poll until completion with configurable timeouts.
- **Quality gates** — Apply pass-rate thresholds and error handling policies to align pipeline outcomes with your release standards.
- **Results for Azure DevOps** — Emit structured XML test results for Azure Pipelines **Publish Test Results**, plus optional JSON summaries for auditing and downstream tooling.
- **Enterprise-ready** — Supports multi-tenant deployments via tenant-scoped configuration where applicable.

## Integration

The task uses the ShiftLeft public API (`/api/v1`): login, run pack execution, status, and detailed results—so pipelines stay the single place operators trigger and observe API validation tied to your environments.

## Using the task

1. Store API credentials in a **variable group** or **secret pipeline variables** (never commit passwords).
2. Add the task to your YAML using publisher **`totalshiftleft`** and task id **`shiftleft-api-integration-task`** (major version **1** after installing the extension).

```yaml
variables:
  - group: shiftleft-secrets   # e.g. ShiftLeftEmail, ShiftLeftPassword (secret)

steps:
  - task: totalshiftleft.shiftleft-api-integration-task@1
    displayName: Run ShiftLeft test pack
    inputs:
      serverUrl: 'https://your-shiftleft-host'
      apiEmail: '$(ShiftLeftEmail)'
      apiPassword: '$(ShiftLeftPassword)'
      packId: 'your_pack_id'
      writeTestResultsXml: true
      testResultsXmlPath: 'shiftleft-test-pack-results.xml'

  - task: PublishTestResults@2
    displayName: Publish results
    condition: succeededOrFailed()
    inputs:
      testResultsFormat: JUnit
      testResultsFiles: '**/shiftleft-test-pack-results.xml'
      searchFolder: '$(Build.SourcesDirectory)'
```

In Azure DevOps, add the task from the catalog to see every input, default, and output variable in the designer.

---

**Total Shift Left** · [Marketplace publisher](https://marketplace.visualstudio.com/publishers/totalshiftleft) · Commercial offering · **Support:** [support@totalshiftleft.com](mailto:support@totalshiftleft.com)
