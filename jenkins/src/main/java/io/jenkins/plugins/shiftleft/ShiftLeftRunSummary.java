package io.jenkins.plugins.shiftleft;

import com.fasterxml.jackson.annotation.JsonIgnore;
import edu.umd.cs.findbugs.annotations.CheckForNull;

/**
 * Serialized to JSON as a workspace artifact and shown on build page.
 * Keep fields simple for compatibility.
 */
public class ShiftLeftRunSummary {
  private @CheckForNull String serverUrl;
  private @CheckForNull String tenantId;
  private @CheckForNull String packId;

  private @CheckForNull String triggerExecutionId;
  private @CheckForNull String triggerStatus;
  private @CheckForNull String triggerStartTimeIso;

  private @CheckForNull String latestStatus;
  private @CheckForNull String executionId;
  private @CheckForNull String startTimeIso;
  private @CheckForNull String endTimeIso;

  private int total;
  private int passed;
  private int failed;
  private int error;
  private double successRate;

  private double gatePassThresholdPercent;
  private boolean gateFailOnErrorTests;

  private @CheckForNull String decision;

  private int resultsCount;

  /** Workspace-relative path for UI links; omitted from JSON artifact via @JsonIgnore. */
  private @CheckForNull String jsonSummaryRelativePath;
  private @CheckForNull String junitXmlRelativePath;

  public @CheckForNull String getServerUrl() {
    return serverUrl;
  }

  public void setServerUrl(@CheckForNull String serverUrl) {
    this.serverUrl = serverUrl;
  }

  public @CheckForNull String getTenantId() {
    return tenantId;
  }

  public void setTenantId(@CheckForNull String tenantId) {
    this.tenantId = tenantId;
  }

  public @CheckForNull String getPackId() {
    return packId;
  }

  public void setPackId(@CheckForNull String packId) {
    this.packId = packId;
  }

  public @CheckForNull String getTriggerExecutionId() {
    return triggerExecutionId;
  }

  public void setTriggerExecutionId(@CheckForNull String triggerExecutionId) {
    this.triggerExecutionId = triggerExecutionId;
  }

  public @CheckForNull String getTriggerStatus() {
    return triggerStatus;
  }

  public void setTriggerStatus(@CheckForNull String triggerStatus) {
    this.triggerStatus = triggerStatus;
  }

  public @CheckForNull String getTriggerStartTimeIso() {
    return triggerStartTimeIso;
  }

  public void setTriggerStartTimeIso(@CheckForNull String triggerStartTimeIso) {
    this.triggerStartTimeIso = triggerStartTimeIso;
  }

  public @CheckForNull String getLatestStatus() {
    return latestStatus;
  }

  public void setLatestStatus(@CheckForNull String latestStatus) {
    this.latestStatus = latestStatus;
  }

  public @CheckForNull String getExecutionId() {
    return executionId;
  }

  public void setExecutionId(@CheckForNull String executionId) {
    this.executionId = executionId;
  }

  public @CheckForNull String getStartTimeIso() {
    return startTimeIso;
  }

  public void setStartTimeIso(@CheckForNull String startTimeIso) {
    this.startTimeIso = startTimeIso;
  }

  public @CheckForNull String getEndTimeIso() {
    return endTimeIso;
  }

  public void setEndTimeIso(@CheckForNull String endTimeIso) {
    this.endTimeIso = endTimeIso;
  }

  public int getTotal() {
    return total;
  }

  public void setTotal(int total) {
    this.total = total;
  }

  public int getPassed() {
    return passed;
  }

  public void setPassed(int passed) {
    this.passed = passed;
  }

  public int getFailed() {
    return failed;
  }

  public void setFailed(int failed) {
    this.failed = failed;
  }

  public int getError() {
    return error;
  }

  public void setError(int error) {
    this.error = error;
  }

  public double getSuccessRate() {
    return successRate;
  }

  public void setSuccessRate(double successRate) {
    this.successRate = successRate;
  }

  public double getGatePassThresholdPercent() {
    return gatePassThresholdPercent;
  }

  public void setGatePassThresholdPercent(double gatePassThresholdPercent) {
    this.gatePassThresholdPercent = gatePassThresholdPercent;
  }

  public boolean isGateFailOnErrorTests() {
    return gateFailOnErrorTests;
  }

  public void setGateFailOnErrorTests(boolean gateFailOnErrorTests) {
    this.gateFailOnErrorTests = gateFailOnErrorTests;
  }

  public @CheckForNull String getDecision() {
    return decision;
  }

  public void setDecision(@CheckForNull String decision) {
    this.decision = decision;
  }

  public int getResultsCount() {
    return resultsCount;
  }

  public void setResultsCount(int resultsCount) {
    this.resultsCount = resultsCount;
  }

  @JsonIgnore
  public @CheckForNull String getJsonSummaryRelativePath() {
    return jsonSummaryRelativePath;
  }

  public void setJsonSummaryRelativePath(@CheckForNull String jsonSummaryRelativePath) {
    this.jsonSummaryRelativePath = jsonSummaryRelativePath;
  }

  @JsonIgnore
  public @CheckForNull String getJunitXmlRelativePath() {
    return junitXmlRelativePath;
  }

  public void setJunitXmlRelativePath(@CheckForNull String junitXmlRelativePath) {
    this.junitXmlRelativePath = junitXmlRelativePath;
  }
}
