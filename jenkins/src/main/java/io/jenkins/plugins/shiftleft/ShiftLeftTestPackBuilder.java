package io.jenkins.plugins.shiftleft;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import jenkins.tasks.SimpleBuildStep;
import jenkins.model.Jenkins;
import hudson.ExtensionList;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;

/**
 * Freestyle build step: trigger ShiftLeft test pack execution and gate the build.
 *
 * Backend contract used (from this repo):
 * - POST /api/v1/login
 * - POST /api/v1/test-packs/{packId}/run
 * - GET  /api/v1/test-packs/{packId}/status
 * - GET  /api/v1/test-packs/{packId}/results?executionId=...
 *
 * Multi-tenant header supported:
 * - X-Tenant-ID: <tenant/subdomain>
 */
public class ShiftLeftTestPackBuilder extends Builder implements SimpleBuildStep {

  private final String serverUrl;

  private String tenantId;
  private String credentialsId;
  private String packId;

  private boolean waitForCompletion = true;
  private int pollIntervalSeconds = 10;
  private int timeoutMinutes = 60;

  private double passThresholdPercent = 100.0;
  private boolean failOnErrorTests = true;
  private String gateFailureResult = Result.FAILURE.toString();

  private boolean writeJsonSummary = true;
  private String jsonSummaryPath = "shiftleft-test-pack-summary.json";
  private boolean writeJUnitXml = false;
  private String junitXmlPath = "shiftleft-test-pack-results.xml";

  @DataBoundConstructor
  public ShiftLeftTestPackBuilder(String serverUrl) {
    this.serverUrl = sanitizeServerUrl(serverUrl);
  }

  public String getServerUrl() {
    return serverUrl;
  }

  public @CheckForNull String getTenantId() {
    return tenantId;
  }

  @DataBoundSetter
  public void setTenantId(String tenantId) {
    this.tenantId = emptyToNull(tenantId);
  }

  public @CheckForNull String getCredentialsId() {
    return credentialsId;
  }

  @DataBoundSetter
  public void setCredentialsId(String credentialsId) {
    this.credentialsId = emptyToNull(credentialsId);
  }

  public @CheckForNull String getPackId() {
    return packId;
  }

  @DataBoundSetter
  public void setPackId(String packId) {
    this.packId = emptyToNull(packId);
  }

  public boolean isWaitForCompletion() {
    return waitForCompletion;
  }

  @DataBoundSetter
  public void setWaitForCompletion(boolean waitForCompletion) {
    this.waitForCompletion = waitForCompletion;
  }

  public int getPollIntervalSeconds() {
    return pollIntervalSeconds;
  }

  @DataBoundSetter
  public void setPollIntervalSeconds(int pollIntervalSeconds) {
    this.pollIntervalSeconds = pollIntervalSeconds;
  }

  public int getTimeoutMinutes() {
    return timeoutMinutes;
  }

  @DataBoundSetter
  public void setTimeoutMinutes(int timeoutMinutes) {
    this.timeoutMinutes = timeoutMinutes;
  }

  public double getPassThresholdPercent() {
    return passThresholdPercent;
  }

  @DataBoundSetter
  public void setPassThresholdPercent(double passThresholdPercent) {
    this.passThresholdPercent = passThresholdPercent;
  }

  public boolean isFailOnErrorTests() {
    return failOnErrorTests;
  }

  @DataBoundSetter
  public void setFailOnErrorTests(boolean failOnErrorTests) {
    this.failOnErrorTests = failOnErrorTests;
  }

  public String getGateFailureResult() {
    return gateFailureResult;
  }

  @DataBoundSetter
  public void setGateFailureResult(String gateFailureResult) {
    this.gateFailureResult = emptyToNull(gateFailureResult) == null ? Result.FAILURE.toString() : gateFailureResult.trim();
  }

  public boolean isWriteJsonSummary() {
    return writeJsonSummary;
  }

  @DataBoundSetter
  public void setWriteJsonSummary(boolean writeJsonSummary) {
    this.writeJsonSummary = writeJsonSummary;
  }

  public String getJsonSummaryPath() {
    return jsonSummaryPath;
  }

  @DataBoundSetter
  public void setJsonSummaryPath(String jsonSummaryPath) {
    this.jsonSummaryPath = emptyToNull(jsonSummaryPath) == null ? "shiftleft-test-pack-summary.json" : jsonSummaryPath;
  }

  public boolean isWriteJUnitXml() {
    return writeJUnitXml;
  }

  @DataBoundSetter
  public void setWriteJUnitXml(boolean writeJUnitXml) {
    this.writeJUnitXml = writeJUnitXml;
  }

  public String getJunitXmlPath() {
    return junitXmlPath;
  }

  @DataBoundSetter
  public void setJunitXmlPath(String junitXmlPath) {
    this.junitXmlPath = emptyToNull(junitXmlPath) == null ? "shiftleft-test-pack-results.xml" : junitXmlPath;
  }

  @Override
  public DescriptorImpl getDescriptor() {
    return (DescriptorImpl) ExtensionList.lookupSingleton(DescriptorImpl.class);
  }

  @Override
  public void perform(@NonNull Run<?, ?> run, @NonNull FilePath workspace, @NonNull Launcher launcher, @NonNull TaskListener listener)
      throws InterruptedException, IOException {

    if (emptyToNull(serverUrl) == null) {
      throw new AbortException("ShiftLeft: Server URL must be configured");
    }
    final String effectivePackId = emptyToNull(packId);
    if (effectivePackId == null) {
      throw new AbortException("ShiftLeft: Pack ID must be configured");
    }
    final String effectiveCredentialsId = emptyToNull(credentialsId);
    if (effectiveCredentialsId == null) {
      throw new AbortException("ShiftLeft: Credentials must be configured");
    }

    listener.getLogger().println("[ShiftLeft] Server: " + serverUrl);
    listener.getLogger().println("[ShiftLeft] Tenant: " + (tenantId == null ? "(default)" : tenantId));
    listener.getLogger().println("[ShiftLeft] Pack ID: " + effectivePackId);

    // Look up credentials - try multiple scopes: Run, Job, and Global
    StandardUsernamePasswordCredentials creds = null;
    Item job = run.getParent();
    
    // Try 1: Standard approach - findCredentialById with Run context
    creds = CredentialsProvider.findCredentialById(
        effectiveCredentialsId,
        StandardUsernamePasswordCredentials.class,
        run
    );
    
    // Try 2: Global scope with SYSTEM authentication (for global credentials)
    if (creds == null) {
      List<StandardUsernamePasswordCredentials> allCreds = CredentialsProvider.lookupCredentials(
          StandardUsernamePasswordCredentials.class,
          Jenkins.get(),  // Global scope
          Jenkins.getAuthentication(),
          java.util.Collections.emptyList()
      );
      for (StandardUsernamePasswordCredentials c : allCreds) {
        if (effectiveCredentialsId.equals(c.getId())) {
          creds = c;
          break;
        }
      }
    }
    
    // Try 3: Job scope with SYSTEM authentication
    if (creds == null) {
      List<StandardUsernamePasswordCredentials> allCreds = CredentialsProvider.lookupCredentials(
          StandardUsernamePasswordCredentials.class,
          job,
          Jenkins.getAuthentication(),
          java.util.Collections.emptyList()
      );
      for (StandardUsernamePasswordCredentials c : allCreds) {
        if (effectiveCredentialsId.equals(c.getId())) {
          creds = c;
          break;
        }
      }
    }
    
    // Try 4: Global scope with user authentication (user-scoped global credentials)
    if (creds == null) {
      hudson.model.Cause.UserIdCause userIdCause = run.getCause(hudson.model.Cause.UserIdCause.class);
      if (userIdCause != null) {
        hudson.model.User user = hudson.model.User.get(userIdCause.getUserId(), false, java.util.Collections.emptyMap());
        if (user != null) {
          org.acegisecurity.Authentication auth = user.impersonate();
          List<StandardUsernamePasswordCredentials> allCreds = CredentialsProvider.lookupCredentials(
              StandardUsernamePasswordCredentials.class,
              Jenkins.get(),  // Global scope
              auth,
              java.util.Collections.emptyList()
          );
          for (StandardUsernamePasswordCredentials c : allCreds) {
            if (effectiveCredentialsId.equals(c.getId())) {
              creds = c;
              break;
            }
          }
        }
      }
    }
    
    // Try 5: Job scope with user authentication (user-scoped job credentials)
    if (creds == null) {
      hudson.model.Cause.UserIdCause userIdCause = run.getCause(hudson.model.Cause.UserIdCause.class);
      if (userIdCause != null) {
        hudson.model.User user = hudson.model.User.get(userIdCause.getUserId(), false, java.util.Collections.emptyMap());
        if (user != null) {
          org.acegisecurity.Authentication auth = user.impersonate();
          List<StandardUsernamePasswordCredentials> allCreds = CredentialsProvider.lookupCredentials(
              StandardUsernamePasswordCredentials.class,
              job,
              auth,
              java.util.Collections.emptyList()
          );
          for (StandardUsernamePasswordCredentials c : allCreds) {
            if (effectiveCredentialsId.equals(c.getId())) {
              creds = c;
              break;
            }
          }
        }
      }
    }
    
    if (creds == null) {
      throw new AbortException("ShiftLeft: Credentials not found: " + effectiveCredentialsId);
    }

    ShiftLeftApiClient client = new ShiftLeftApiClient(serverUrl, tenantId, listener);

    String token = client.login(creds.getUsername(), creds.getPassword().getPlainText());
    client.setBearerToken(token);

    // F7: remember the execution reported before we trigger, so the poll loop below can tell our
    // run apart from the previous one. A failure to read it must not block the run; an interrupt
    // still propagates.
    String priorExecutionId = null;
    try {
      ShiftLeftApiClient.StatusResponse priorStatus = client.getStatus(effectivePackId);
      priorExecutionId = (priorStatus != null && priorStatus.executionId != null
          && !priorStatus.executionId.trim().isEmpty()) ? priorStatus.executionId : null;
    } catch (IOException e) {
      listener.getLogger().println("[ShiftLeft] Could not read prior status ("
          + e.getMessage() + "); continuing.");
    }
    listener.getLogger().println("[ShiftLeft] Previous executionId: "
        + (priorExecutionId == null ? "(none)" : priorExecutionId));

    ShiftLeftApiClient.TriggerResponse trigger = client.triggerTestPack(effectivePackId);
    listener.getLogger().println("[ShiftLeft] Triggered pack. triggerExecutionId=" + trigger.executionId
        + " status=" + trigger.status
        + " message=" + (trigger.message != null ? trigger.message : "(none)")
        + " responsePackId=" + (trigger.packId != null ? trigger.packId : "(none)"));

    ShiftLeftRunSummary summary = new ShiftLeftRunSummary();
    summary.setServerUrl(serverUrl);
    summary.setTenantId(tenantId);
    summary.setPackId(effectivePackId);
    summary.setTriggerExecutionId(trigger.executionId);
    summary.setTriggerStatus(trigger.status);
    summary.setTriggerStartTimeIso(trigger.startTimeIso);

    Result configuredGateFailureResult =
        Objects.requireNonNullElse(Result.fromString(gateFailureResult), Result.FAILURE);

    if (!waitForCompletion) {
      attachAndOptionallyWriteArtifacts(run, workspace, listener, summary, null);
      return;
    }

    long deadlineNanos = System.nanoTime() + TimeUnit.MINUTES.toNanos(Math.max(1, timeoutMinutes));
    ShiftLeftApiClient.StatusResponse lastStatus = null;

    while (System.nanoTime() < deadlineNanos) {
      lastStatus = client.getStatus(effectivePackId);
      summary.setLatestStatus(lastStatus.status);
      summary.setExecutionId(lastStatus.executionId); // NOTE: may differ from triggerExecutionId in backend
      summary.setStartTimeIso(lastStatus.startTimeIso);
      summary.setEndTimeIso(lastStatus.endTimeIso);
      summary.setTotal(lastStatus.summaryTotal);
      summary.setPassed(lastStatus.summaryPassed);
      summary.setFailed(lastStatus.summaryFailed);
      summary.setError(lastStatus.summaryError);
      summary.setSuccessRate(lastStatus.summarySuccessRate);

      listener.getLogger().println("[ShiftLeft] Status: " + lastStatus.status
          + " packName=" + (lastStatus.name != null ? lastStatus.name : "(n/a)")
          + " apiPackId=" + (lastStatus.packId != null ? lastStatus.packId : "(n/a)")
          + " executionId=" + (lastStatus.executionId == null ? "(none)" : lastStatus.executionId)
          + " total=" + lastStatus.summaryTotal
          + " passed=" + lastStatus.summaryPassed
          + " failed=" + lastStatus.summaryFailed
          + " error=" + lastStatus.summaryError
          + " successRate=" + lastStatus.summarySuccessRate);

      if (isTerminalStatus(lastStatus.status) && isNewExecution(lastStatus, priorExecutionId)) {
        break;
      }

      if (isTerminalStatus(lastStatus.status)) {
        listener.getLogger().println(
            "[ShiftLeft] Terminal status still refers to the previous execution;"
                + " waiting for this run to start.");
      }

      TimeUnit.SECONDS.sleep(Math.max(1, pollIntervalSeconds));
    }

    if (lastStatus == null || !isTerminalStatus(lastStatus.status)
        || !isNewExecution(lastStatus, priorExecutionId)) {
      summary.setDecision("TIMEOUT");
      listener.getLogger().println("[ShiftLeft] Timed out waiting for a new execution to complete after "
          + timeoutMinutes + " minutes.");
      setBuildResult(run, configuredGateFailureResult);
      attachAndOptionallyWriteArtifacts(run, workspace, listener, summary, null);
      return;
    }

    ShiftLeftApiClient.ResultsResponse results = client.getResults(effectivePackId, summary.getExecutionId());
    summary.setResultsCount(results.resultsCount);
    listener.getLogger().println("[ShiftLeft] Fetched results: packName="
        + (results.packName != null ? results.packName : "(n/a)")
        + " packId=" + (results.packId != null ? results.packId : "(n/a)")
        + " executionId=" + (results.executionId != null ? results.executionId : "(n/a)")
        + " start=" + (results.startTimeIso != null ? results.startTimeIso : "(n/a)")
        + " end=" + (results.endTimeIso != null ? results.endTimeIso : "(n/a)")
        + " rows=" + results.resultsCount);

    // Prefer results summary if it exists; fall back to status summary.
    if (results.summaryTotal != null) {
      summary.setTotal(results.summaryTotal);
      summary.setPassed(Objects.requireNonNullElse(results.summaryPassed, 0));
      summary.setFailed(Objects.requireNonNullElse(results.summaryFailed, 0));
      summary.setError(Objects.requireNonNullElse(results.summaryError, 0));
      summary.setSuccessRate(Objects.requireNonNullElse(results.summarySuccessRate, 0.0));
    }

    QualityGateDecision decision = QualityGateEvaluator.evaluate(
        summary.getLatestStatus(),
        summary.getTotal(),
        summary.getPassed(),
        summary.getFailed(),
        summary.getError(),
        summary.getSuccessRate(),
        passThresholdPercent,
        failOnErrorTests,
        configuredGateFailureResult
    );

    summary.setDecision(decision.decision);
    summary.setGatePassThresholdPercent(passThresholdPercent);
    summary.setGateFailOnErrorTests(failOnErrorTests);

    listener.getLogger().println("[ShiftLeft] Decision: " + decision.decision + " (result=" + decision.jenkinsResult + ")");
    setBuildResult(run, decision.jenkinsResult);

    attachAndOptionallyWriteArtifacts(run, workspace, listener, summary, results);
  }

  private void attachAndOptionallyWriteArtifacts(
      Run<?, ?> run,
      FilePath workspace,
      TaskListener listener,
      ShiftLeftRunSummary summary,
      @CheckForNull ShiftLeftApiClient.ResultsResponse results
  ) throws IOException, InterruptedException {
    summary.setJsonSummaryRelativePath(writeJsonSummary ? jsonSummaryPath : null);
    summary.setJunitXmlRelativePath(writeJUnitXml && results != null ? junitXmlPath : null);

    run.addAction(new ShiftLeftTestPackAction(summary));

    if (writeJsonSummary) {
      ShiftLeftArtifactWriter.writeJsonSummary(workspace, jsonSummaryPath, summary, listener);
    }
    if (writeJUnitXml && results != null) {
      ShiftLeftArtifactWriter.writeJUnitXml(workspace, junitXmlPath, summary, results, listener);
    }
  }

  private static boolean isTerminalStatus(@CheckForNull String status) {
    if (status == null) return false;
    String s = status.trim().toUpperCase();
    return "COMPLETED".equals(s) || "FAILED".equals(s);
  }

  /**
   * F7: {@code /status} reports the pack's MOST RECENT execution, and {@code POST /run} is
   * fire-and-forget — the server does not reach {@code RUNNING} the instant it responds. A poll
   * issued right after triggering can therefore read the PREVIOUS run's terminal status and grade
   * the build on stale results. A run only counts as ours once the reported executionId differs
   * from the one observed before triggering; with no prior execution there is nothing stale to
   * confuse us.
   */
  private static boolean isNewExecution(@CheckForNull ShiftLeftApiClient.StatusResponse status,
      @CheckForNull String priorExecutionId) {
    if (priorExecutionId == null) return true;
    if (status == null || status.executionId == null) return false;
    String exec = status.executionId.trim();
    return !exec.isEmpty() && !exec.equals(priorExecutionId.trim());
  }

  private static void setBuildResult(Run<?, ?> run, Result desired) {
    if (desired == null) return;
    Result current = run.getResult();
    if (current == null) {
      run.setResult(desired);
      return;
    }
    // Only worsen the result, never improve.
    if (desired.isWorseThan(current)) {
      run.setResult(desired);
    }
  }

  private static @CheckForNull String emptyToNull(@CheckForNull String s) {
    if (s == null) return null;
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }

  private static String sanitizeServerUrl(@CheckForNull String serverUrl) {
    String s = emptyToNull(serverUrl);
    if (s == null) return "";
    // remove trailing slash for consistent URL joining
    while (s.endsWith("/")) {
      s = s.substring(0, s.length() - 1);
    }
    return s;
  }

  @Extension
  public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
    @Override
    public boolean isApplicable(Class<? extends hudson.model.AbstractProject> jobType) {
      return true;
    }

    @Override
    public String getDisplayName() {
      return "Shift-Left API Automation Integration: Run Test Pack";
    }

    public ListBoxModel doFillCredentialsIdItems(
        @AncestorInPath Item item,
        @QueryParameter String serverUrl) {
      Jenkins.get().checkPermission(Item.CONFIGURE);
      ListBoxModel m = new StandardListBoxModel().includeEmptyValue();
      String base = sanitizeServerUrl(serverUrl);
      List<StandardUsernamePasswordCredentials> all = lookupUsernamePasswordCredentials(item, base);
      for (StandardUsernamePasswordCredentials c : all) {
        String label = c.getId();
        if (c.getDescription() != null && !c.getDescription().trim().isEmpty()) {
          label = label + " — " + c.getDescription();
        }
        m.add(label, c.getId());
      }
      return m;
    }

    public ListBoxModel doFillGateFailureResultItems() {
      ListBoxModel m = new ListBoxModel();
      m.add("FAILURE", Result.FAILURE.toString());
      m.add("UNSTABLE", Result.UNSTABLE.toString());
      return m;
    }

    public ListBoxModel doFillPackIdItems(
        @AncestorInPath Item item,
        @QueryParameter String serverUrl,
        @QueryParameter String tenantId,
        @QueryParameter String credentialsId
    ) {
      Jenkins.get().checkPermission(Item.CONFIGURE);
      ListBoxModel m = new ListBoxModel();

      String base = sanitizeServerUrl(serverUrl);
      if (emptyToNull(base) == null || emptyToNull(credentialsId) == null) {
        m.add("(configure Server URL and Credentials first)", "");
        return m;
      }

      StandardUsernamePasswordCredentials creds = findUsernamePasswordCredentials(item, base, credentialsId);
      if (creds == null) {
        m.add("(credentials not found)", "");
        return m;
      }

      try {
        ShiftLeftApiClient client = new ShiftLeftApiClient(base, emptyToNull(tenantId), TaskListener.NULL);
        String token = client.login(creds.getUsername(), creds.getPassword().getPlainText());
        client.setBearerToken(token);
        List<ShiftLeftApiClient.TestPackItem> packs = client.listTestPacks();
        
        if (packs == null || packs.isEmpty()) {
          m.add("(no test packs available)", "");
          return m;
        }
        
        for (ShiftLeftApiClient.TestPackItem p : packs) {
          if (p.packId == null || p.packId.trim().isEmpty()) continue;
          m.add(formatTestPackListLabel(p), p.packId);
        }
      } catch (Exception e) {
        // Return error message option if connection fails
        m.add("(error loading packs: " + e.getMessage() + ")", "");
      }

      return m;
    }

    @RequirePOST
    public FormValidation doTestConnection(
        @AncestorInPath Item item,
        @QueryParameter String serverUrl,
        @QueryParameter String tenantId,
        @QueryParameter String credentialsId
    ) throws IOException, InterruptedException {
      Jenkins.get().checkPermission(Item.CONFIGURE);
      String base = sanitizeServerUrl(serverUrl);
      if (emptyToNull(base) == null) {
        return FormValidation.error("Server URL is required");
      }
      if (emptyToNull(credentialsId) == null) {
        return FormValidation.error("Credentials are required");
      }

      StandardUsernamePasswordCredentials creds = findUsernamePasswordCredentials(item, base, credentialsId);
      if (creds == null) {
        return FormValidation.error("Credentials not found: " + credentialsId);
      }

      ShiftLeftApiClient client = new ShiftLeftApiClient(base, emptyToNull(tenantId), TaskListener.NULL);
      String token = client.login(creds.getUsername(), creds.getPassword().getPlainText());
      if (emptyToNull(token) == null) {
        return FormValidation.error("Login did not return a token");
      }
      return FormValidation.ok("Login OK");
    }

    private static List<DomainRequirement> domainRequirementsForServerUrl(String sanitizedBaseUrl) {
      if (emptyToNull(sanitizedBaseUrl) == null) {
        return Collections.emptyList();
      }
      return URIRequirementBuilder.fromUri(sanitizedBaseUrl).build();
    }

    /**
     * Resolves username/password credentials visible for this item (job/folder) or Jenkins root,
     * optionally filtered by server URL host when the URL is set.
     */
    private static List<StandardUsernamePasswordCredentials> lookupUsernamePasswordCredentials(
        @CheckForNull Item item,
        String sanitizedBaseUrl) {
      List<DomainRequirement> reqs = domainRequirementsForServerUrl(sanitizedBaseUrl);
      if (item != null) {
        return CredentialsProvider.lookupCredentials(
            StandardUsernamePasswordCredentials.class,
            item,
            Jenkins.getAuthentication(),
            reqs);
      }
      return CredentialsProvider.lookupCredentials(
          StandardUsernamePasswordCredentials.class,
          Jenkins.get(),
          Jenkins.getAuthentication(),
          reqs);
    }

    private static String formatTestPackListLabel(ShiftLeftApiClient.TestPackItem p) {
      StringBuilder sb = new StringBuilder();
      boolean hasDisplayName = p.name != null && !p.name.trim().isEmpty();
      if (hasDisplayName) {
        sb.append(p.name.trim());
      } else {
        sb.append(p.packId);
      }
      if (p.projectName != null && !p.projectName.trim().isEmpty()) {
        sb.append(" — ").append(p.projectName.trim());
      }
      if (p.environment != null && !p.environment.trim().isEmpty()) {
        sb.append(" — ").append(p.environment.trim());
      }
      if (p.lastStatus != null && !p.lastStatus.trim().isEmpty()) {
        sb.append(" — last: ").append(p.lastStatus.trim());
      }
      if (hasDisplayName && p.packId != null && !p.packId.trim().isEmpty()) {
        sb.append(" (").append(p.packId.trim()).append(")");
      }
      return sb.toString();
    }

    private static StandardUsernamePasswordCredentials findUsernamePasswordCredentials(
        @CheckForNull Item item,
        String baseUrl,
        String credentialsId) {
      List<StandardUsernamePasswordCredentials> all = lookupUsernamePasswordCredentials(item, baseUrl);
      for (StandardUsernamePasswordCredentials c : all) {
        if (credentialsId.equals(c.getId())) {
          return c;
        }
      }
      return null;
    }
  }
}

