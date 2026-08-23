'use strict';

const path = require('path');
const { createApiClient } = require('./apiClient');
const { evaluate } = require('./qualityGate');
const { writeJsonSummary, writeXmlTestResults } = require('./artifacts');

// Checked in order when the working-directory input is empty. Each variable only exists on its
// own platform, so one generic list keeps the runner correct everywhere instead of forcing a
// per-platform build. Callers may override via options.workingDirEnvVars.
const DEFAULT_WORKING_DIR_ENV_VARS = [
  'GITHUB_WORKSPACE',        // GitHub Actions
  'CI_PROJECT_DIR',          // GitLab CI
  'CIRCLE_WORKING_DIRECTORY',// CircleCI
  'BITBUCKET_CLONE_DIR',     // Bitbucket Pipelines
  'BUILD_SOURCESDIRECTORY',  // Azure Pipelines
  'WORKSPACE',               // Jenkins
];

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function emptyToNull(s) {
  if (s == null) return null;
  const t = String(s).trim();
  return t === '' ? null : t;
}

function isTerminalStatus(status) {
  if (status == null) return false;
  const s = String(status).trim().toUpperCase();
  return s === 'COMPLETED' || s === 'FAILED';
}

// F7: /status reports the pack's MOST RECENT execution, and POST /run is fire-and-forget — the
// server does not reach 'RUNNING' the instant it responds. A poll issued right after triggering
// can therefore read the PREVIOUS run's terminal status and grade the build on stale results.
// A run only counts as ours once the reported executionId differs from the one we saw before
// triggering. When there was no prior execution there is nothing stale to confuse us.
function isNewExecution(status, priorExecutionId) {
  if (priorExecutionId == null) return true;
  const exec = status && status.executionId != null ? String(status.executionId).trim() : '';
  return exec !== '' && exec !== String(priorExecutionId).trim();
}

function mapGateFailureInputToSeverity(gateFailureResult) {
  return gateFailureResult === 'succeeded-with-issues' || gateFailureResult === 'succeededWithIssues'
    ? 'UNSTABLE'
    : 'FAILURE';
}

function buildSummary() {
  return {
    serverUrl: null,
    tenantId: null,
    packId: null,
    triggerExecutionId: null,
    triggerStatus: null,
    triggerStartTimeIso: null,
    latestStatus: null,
    executionId: null,
    startTimeIso: null,
    endTimeIso: null,
    total: 0,
    passed: 0,
    failed: 0,
    error: 0,
    successRate: 0,
    gatePassThresholdPercent: 0,
    gateFailOnErrorTests: true,
    decision: null,
    taskCompletion: null,
    resultsCount: 0,
    jsonSummaryRelativePath: null,
    testResultsXmlRelativePath: null,
  };
}

function parseBool(v, defaultValue) {
  if (v == null || String(v).trim() === '') return defaultValue;
  const s = String(v).trim().toLowerCase();
  if (s === 'true' || s === '1' || s === 'yes') return true;
  if (s === 'false' || s === '0' || s === 'no') return false;
  return defaultValue;
}

function setStepOutputs(core, summary, paths, passed) {
  core.setOutput('execution_id', summary.executionId || '');
  core.setOutput('trigger_execution_id', summary.triggerExecutionId || '');
  core.setOutput('decision', summary.decision || '');
  core.setOutput(
    'success_rate',
    summary.successRate != null && Number.isFinite(Number(summary.successRate))
      ? String(summary.successRate)
      : '0'
  );
  core.setOutput('json_summary_path', paths.jsonAbs || '');
  core.setOutput('test_results_xml_path', paths.xmlAbs || '');
  core.setOutput('passed', passed ? 'true' : 'false');
  core.setOutput('task_completion', summary.taskCompletion || (passed ? 'succeeded' : 'failed'));
}

/**
 * @param {import('@actions/core')} core
 * @param {{ fetchFn?: typeof fetch }} [options]
 */
async function runShiftLeft(core, options = {}) {
  const fetchFn = options.fetchFn || globalThis.fetch;

  const serverUrl = emptyToNull(core.getInput('server-url', { required: true }));
  const tenantId = emptyToNull(core.getInput('tenant-id') || '');
  const apiEmail = core.getInput('api-email', { required: true });
  const apiPassword = core.getInput('api-password', { required: true });
  const packId = emptyToNull(core.getInput('pack-id', { required: true }));

  const waitForCompletion = parseBool(core.getInput('wait-for-completion'), true);
  const pollIntervalSeconds = Math.max(
    1,
    parseInt(core.getInput('poll-interval-seconds') || '10', 10) || 10
  );
  const timeoutMinutes = Math.max(
    1,
    parseInt(core.getInput('timeout-minutes') || '60', 10) || 60
  );

  const passThresholdPercent = parseFloat(core.getInput('pass-threshold-percent') || '100');
  const failOnErrorTests = parseBool(core.getInput('fail-on-error-tests'), true);
  const gateFailureInput = (core.getInput('gate-failure-result') || 'failed').trim();
  const gateFailureSeverity = mapGateFailureInputToSeverity(gateFailureInput);

  const writeJson = parseBool(core.getInput('write-json-summary'), true);
  let jsonSummaryPath =
    emptyToNull(core.getInput('json-summary-path')) || 'shiftleft-test-pack-summary.json';
  // Defaults to true: JUnit XML is how CI surfaces per-test results, and every host's own
  // documentation promises it. The five copies this package replaced disagreed here (CircleCI,
  // Bitbucket and GitLab defaulted true; GitHub and Azure defaulted false), which is exactly the
  // kind of silent drift that motivated the consolidation.
  const writeTestResultsXml = parseBool(core.getInput('write-test-results-xml'), true);
  let testResultsXmlPath =
    emptyToNull(core.getInput('test-results-xml-path')) || 'shiftleft-test-pack-results.xml';

  const workingDirInput = emptyToNull(core.getInput('working-directory'));
  const workingDirEnvVars = Array.isArray(options.workingDirEnvVars) && options.workingDirEnvVars.length
    ? options.workingDirEnvVars
    : DEFAULT_WORKING_DIR_ENV_VARS;
  const workingDirFromEnv = workingDirEnvVars
    .map((name) => process.env[name])
    .find((v) => v != null && String(v).trim() !== '');
  const workingDir = workingDirInput || workingDirFromEnv || process.cwd();

  const logConsole = (msg) => core.info(msg);

  logConsole(`[ShiftLeft] Server: ${serverUrl}`);
  logConsole(`[ShiftLeft] Tenant: ${tenantId || '(default)'}`);
  logConsole(`[ShiftLeft] Pack ID: ${packId}`);

  const client = createApiClient({
    baseUrl: serverUrl,
    tenantId,
    log: (m) => {
      core.debug(m);
      logConsole(m);
    },
    fetchFn,
    userAgent: options.userAgent,
  });

  const token = await client.login(apiEmail, apiPassword);
  client.setBearerToken(token);

  // F7: remember the execution reported before we trigger, so the poll loop below can tell
  // our run apart from the previous one. A failure here must not block the run.
  let priorExecutionId = null;
  try {
    const priorStatus = await client.getStatus(packId);
    priorExecutionId = priorStatus && priorStatus.executionId ? priorStatus.executionId : null;
  } catch (err) {
    logConsole(`[ShiftLeft] Could not read prior status (${err && err.message ? err.message : err}); continuing.`);
  }
  logConsole(`[ShiftLeft] Previous executionId: ${priorExecutionId || '(none)'}`);

  const trigger = await client.triggerTestPack(packId);
  logConsole(
    `[ShiftLeft] Triggered pack. triggerExecutionId=${trigger.executionId || '(none)'} status=${trigger.status || '(none)'} message=${trigger.message || '(none)'} responsePackId=${trigger.packId || '(none)'}`
  );

  const summary = buildSummary();
  summary.serverUrl = serverUrl;
  summary.tenantId = tenantId;
  summary.packId = packId;
  summary.triggerExecutionId = trigger.executionId;
  summary.triggerStatus = trigger.status;
  summary.triggerStartTimeIso = trigger.startTimeIso;

  const paths = { jsonAbs: '', xmlAbs: '' };

  if (!waitForCompletion) {
    summary.decision = 'TRIGGER_ONLY';
    summary.taskCompletion = 'succeeded';
    await writeOutputs(
      workingDir,
      summary,
      null,
      writeJson,
      jsonSummaryPath,
      writeTestResultsXml,
      testResultsXmlPath,
      logConsole,
      paths
    );
    setStepOutputs(core, summary, paths, true);
    return;
  }

  const deadline = Date.now() + timeoutMinutes * 60 * 1000;
  let lastStatus = null;

  while (Date.now() < deadline) {
    lastStatus = await client.getStatus(packId);
    summary.latestStatus = lastStatus.status;
    summary.executionId = lastStatus.executionId;
    summary.startTimeIso = lastStatus.startTimeIso;
    summary.endTimeIso = lastStatus.endTimeIso;
    summary.total = lastStatus.summaryTotal;
    summary.passed = lastStatus.summaryPassed;
    summary.failed = lastStatus.summaryFailed;
    summary.error = lastStatus.summaryError;
    summary.successRate = lastStatus.summarySuccessRate;

    logConsole(
      `[ShiftLeft] Status: ${lastStatus.status} packName=${lastStatus.name || '(n/a)'} apiPackId=${lastStatus.packId || '(n/a)'} executionId=${lastStatus.executionId || '(none)'} total=${lastStatus.summaryTotal} passed=${lastStatus.summaryPassed} failed=${lastStatus.summaryFailed} error=${lastStatus.summaryError} successRate=${lastStatus.summarySuccessRate}`
    );

    if (isTerminalStatus(lastStatus.status) && isNewExecution(lastStatus, priorExecutionId)) break;
    if (isTerminalStatus(lastStatus.status)) {
      logConsole('[ShiftLeft] Terminal status still refers to the previous execution; waiting for this run to start.');
    }
    await sleep(pollIntervalSeconds * 1000);
  }

  if (!lastStatus || !isTerminalStatus(lastStatus.status) || !isNewExecution(lastStatus, priorExecutionId)) {
    summary.decision = 'TIMEOUT';
    summary.taskCompletion = gateFailureSeverity === 'UNSTABLE' ? 'succeededWithIssues' : 'failed';
    logConsole(`[ShiftLeft] Timed out waiting for completion after ${timeoutMinutes} minutes.`);
    await writeOutputs(
      workingDir,
      summary,
      null,
      writeJson,
      jsonSummaryPath,
      writeTestResultsXml,
      testResultsXmlPath,
      logConsole,
      paths
    );
    const msg = 'ShiftLeft test pack did not complete a new execution in time.';
    if (gateFailureSeverity === 'UNSTABLE') {
      core.warning(msg);
      setStepOutputs(core, summary, paths, false);
    } else {
      core.setFailed(msg);
      setStepOutputs(core, summary, paths, false);
    }
    return;
  }

  const results = await client.getResults(packId, summary.executionId);
  summary.resultsCount = results.resultsCount;
  logConsole(
    `[ShiftLeft] Fetched results: packName=${results.packName || '(n/a)'} packId=${results.packId || '(n/a)'} executionId=${results.executionId || '(n/a)'} start=${results.startTimeIso || '(n/a)'} end=${results.endTimeIso || '(n/a)'} rows=${results.resultsCount}`
  );

  if (results.summaryTotal != null) {
    summary.total = results.summaryTotal;
    summary.passed = results.summaryPassed != null ? results.summaryPassed : 0;
    summary.failed = results.summaryFailed != null ? results.summaryFailed : 0;
    summary.error = results.summaryError != null ? results.summaryError : 0;
    summary.successRate =
      results.summarySuccessRate != null ? results.summarySuccessRate : 0;
  }

  const decision = evaluate(
    summary.latestStatus,
    summary.total,
    summary.passed,
    summary.failed,
    summary.error,
    summary.successRate,
    passThresholdPercent,
    failOnErrorTests,
    gateFailureSeverity
  );

  summary.decision = decision.decision;
  summary.taskCompletion = decision.taskCompletion;
  summary.gatePassThresholdPercent = passThresholdPercent;
  summary.gateFailOnErrorTests = failOnErrorTests;

  logConsole(`[ShiftLeft] Decision: ${decision.decision} (completion=${decision.taskCompletion})`);

  await writeOutputs(
    workingDir,
    summary,
    results,
    writeJson,
    jsonSummaryPath,
    writeTestResultsXml,
    testResultsXmlPath,
    logConsole,
    paths
  );

  if (decision.taskCompletion === 'failed') {
    core.setFailed(
      decision.decision === 'FAILED' || decision.decision.startsWith('GATE_')
        ? `Quality gate: ${decision.decision}`
        : `ShiftLeft: ${decision.decision}`
    );
    setStepOutputs(core, summary, paths, false);
    return;
  }

  if (decision.taskCompletion === 'succeededWithIssues') {
    core.warning(`Quality gate: ${decision.decision} (succeeded with issues)`);
    setStepOutputs(core, summary, paths, false);
    return;
  }

  setStepOutputs(core, summary, paths, true);
}

async function writeOutputs(
  workingDir,
  summary,
  results,
  writeJson,
  jsonSummaryPath,
  doWriteTestResultsXml,
  testResultsXmlPath,
  logConsole,
  paths
) {
  summary.jsonSummaryRelativePath = writeJson ? jsonSummaryPath : null;
  summary.testResultsXmlRelativePath = doWriteTestResultsXml && results ? testResultsXmlPath : null;

  if (writeJson) {
    const p = writeJsonSummary(workingDir, jsonSummaryPath, summary);
    logConsole(`[ShiftLeft] Wrote JSON summary: ${p}`);
    paths.jsonAbs = path.resolve(p);
  }
  if (doWriteTestResultsXml && results) {
    const p = writeXmlTestResults(workingDir, testResultsXmlPath, summary, results);
    logConsole(`[ShiftLeft] Wrote XML test results: ${p}`);
    paths.xmlAbs = path.resolve(p);
  }
}

module.exports = {
  runShiftLeft,
  mapGateFailureInputToSeverity,
  DEFAULT_WORKING_DIR_ENV_VARS,
};
