'use strict';

/**
 * Quality gate evaluation aligned with ShiftLeft platform rules for pack runs.
 * @param {'FAILURE'|'UNSTABLE'} gateFailureSeverity when the gate fails, map to failed vs succeeded-with-issues
 * @returns {{ decision: string, taskCompletion: 'succeeded'|'failed'|'succeededWithIssues' }}
 */
function taskCompletionFromInternal(internal) {
  if (internal === 'SUCCESS') return 'succeeded';
  if (internal === 'UNSTABLE') return 'succeededWithIssues';
  return 'failed';
}

function evaluate(
  status,
  total,
  passed,
  failed,
  error,
  successRate,
  passThresholdPercent,
  failOnErrorTests,
  gateFailureSeverity
) {
  const normalized = (status || '').trim().toUpperCase();
  const gateFail = gateFailureSeverity === 'UNSTABLE' ? 'UNSTABLE' : 'FAILURE';

  if (failOnErrorTests && error > 0) {
    return { decision: 'GATE_FAIL_ERROR_TESTS', taskCompletion: taskCompletionFromInternal(gateFail) };
  }

  const sr = Number.isFinite(successRate) ? successRate : computeSuccessRate(total, passed);
  const threshold = Number.isFinite(passThresholdPercent) ? passThresholdPercent : 100.0;

  if (threshold > 0) {
    if (sr + 1e-9 < threshold) {
      return { decision: 'GATE_FAIL_THRESHOLD', taskCompletion: taskCompletionFromInternal(gateFail) };
    }
    if (normalized === 'COMPLETED' || normalized === 'FAILED') {
      return { decision: 'PASSED', taskCompletion: taskCompletionFromInternal('SUCCESS') };
    }
    return { decision: 'PASSED', taskCompletion: taskCompletionFromInternal('SUCCESS') };
  }

  if (normalized === 'FAILED') {
    return { decision: 'FAILED', taskCompletion: taskCompletionFromInternal(gateFail) };
  }

  if (normalized === 'COMPLETED') {
    return { decision: 'PASSED', taskCompletion: taskCompletionFromInternal('SUCCESS') };
  }

  if (failed > 0 || error > 0) {
    return { decision: 'COMPLETED_WITH_ISSUES', taskCompletion: taskCompletionFromInternal('UNSTABLE') };
  }

  return { decision: 'OK', taskCompletion: taskCompletionFromInternal('SUCCESS') };
}

function computeSuccessRate(total, passed) {
  if (total <= 0) return 0.0;
  return Math.round((passed / total) * 10000) / 100.0;
}

module.exports = { evaluate, computeSuccessRate };
