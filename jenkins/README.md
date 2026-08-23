# Shift-Left API Automation Integration (Jenkins)

Jenkins plugin by [Total Shift Left](https://www.totalshiftleft.ai) that runs **Shift-Left API Automation** test packs from **Freestyle** jobs. It uses your backend’s public API (`/api/v1`): login, trigger a test pack, optionally wait and poll for completion, apply a quality gate, and optionally write JSON or JUnit XML to the workspace.

| | |
|--|--|
| **Plugin id** | `shift-left-api-automation-integration` |
| **Shown in Jenkins** | Shift-Left API Automation Integration |
| **Package file name** | `totalshiftleft.shift-left-api-automation-integration-{version}.hpi` |

In the file name, replace `{version}` with the release number (semantic version, e.g. `1.0.0`). Example: `totalshiftleft.shift-left-api-automation-integration-1.0.0.hpi`.

The plugin source lives in this directory (`pom.xml`, `src/`). Build the `.hpi` yourself, or take one from a tagged release, then use the steps below to install and configure it.

**Supported Jenkins:** 2.479.x LTS or newer (Java 17 on the controller is typical for current LTS).

---

## Install the plugin

1. **Get the `.hpi`.** Download it from the [release](https://github.com/Total-Shift-Left/Shift-Left-API-Integrations/releases) for the version you want, or build it from this directory with `mvn -B -ntp verify` — it lands in `target/`. Save the file where you can select it in the Jenkins upload dialog (typically the machine running your browser).
2. In Jenkins, go to **Manage Jenkins → Plugins**.
3. Open the **Advanced settings** tab (or **Advanced** on older versions).
4. Under **Deploy Plugin**, click **Choose File**, select the downloaded `.hpi`, then **Deploy**.
5. When Jenkins prompts you to **restart** to complete the installation, do so.
6. Confirm under **Installed plugins** that **Shift-Left API Automation Integration** is listed (plugin id: `shift-left-api-automation-integration`).

If Jenkins reports missing dependencies, install the suggested plugins (for example **Credentials** and related credential bindings) from the same **Plugins** page, then restart again if needed.

---

## Prerequisites

- Network path from the Jenkins controller to your **Shift-Left API** base URL (HTTPS recommended).
- A Jenkins credential of type **Username with password**: **username** = API login email, **password** = API password.
- Your Shift-Left deployment must expose the API this plugin expects (`POST /api/v1/login`, test pack run, status, results, and listing packs).
- **Multi-tenant** setups: you may need to set **Tenant ID** in the job so requests include `X-Tenant-ID`.

---

## Usage (Freestyle job)

1. Open an existing **Freestyle** project or create a new one.
2. Under **Build Steps**, click **Add build step** and choose **Shift-Left API Automation Integration: Run Test Pack**.
3. **Shift-Left API connection**
   - **Server URL**: base URL only (for example `https://app.totalshiftleft.ai`), no trailing slash.
   - **Tenant ID**: leave empty unless your environment requires it.
   - **Credentials**: pick the **Username with password** credential described above.
   - Click **Test Connection** to confirm login and reachability.
4. **Test Pack**
   - **Pack ID**: pick a pack from the list (filled from your server after connection works).
5. **Execution**
   - **Wait for completion**: leave enabled to poll until the run finishes or times out; disable if you only want to trigger and exit.
   - Adjust **Poll interval (seconds)** and **Overall timeout (minutes)** if needed.
6. **Quality Gate**
   - **Pass threshold (%)**: minimum pass rate for a successful gate (use `0` to turn off threshold checking).
   - **Fail build when any ERROR tests**: enable to fail on ERROR results regardless of threshold.
   - **Build result on gate failure**: choose how Jenkins should mark the build (for example **FAILURE** or **UNSTABLE**).
7. **Artifacts (optional)**
   - Enable **Write JSON summary** and set the workspace-relative path if you want a JSON report.
   - Enable **Write JUnit XML** and set its path if you want JUnit output; you can then add a **Publish JUnit test result report** post-build step pointing at that file.

After a build, open the build page and use the **Shift-Left API Automation Integration** link in the sidebar for a summary and links to workspace files when those options are enabled.

---

## Support

- **Publisher:** Total Shift Left — [totalshiftleft.ai](https://www.totalshiftleft.ai)  
- **Contact:** support@totalshiftleft.com
