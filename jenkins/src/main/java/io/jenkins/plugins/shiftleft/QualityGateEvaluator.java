package io.jenkins.plugins.shiftleft;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.Result;

/**
 * Pure decision logic, unit-testable.
 */
final class QualityGateEvaluator {

  private QualityGateEvaluator() {}

  static QualityGateDecision evaluate(
      @CheckForNull String status,
      int total,
      int passed,
      int failed,
      int error,
      double successRate,
      double passThresholdPercent,
      boolean failOnErrorTests,
      Result gateFailureResult
  ) {
    String normalized = status == null ? "" : status.trim().toUpperCase();
    Result gateFail = gateFailureResult == null ? Result.FAILURE : gateFailureResult;

    // If there are errors and the user wants to fail on errors, fail (highest priority).
    if (failOnErrorTests && error > 0) {
      return new QualityGateDecision("GATE_FAIL_ERROR_TESTS", gateFail);
    }

    // Guard against NaN.
    double sr = Double.isFinite(successRate) ? successRate : computeSuccessRate(total, passed);
    double threshold = Double.isFinite(passThresholdPercent) ? passThresholdPercent : 100.0;

    // Check threshold if it's enabled (threshold > 0).
    // If threshold is met, allow pass even if status is FAILED (user-defined threshold takes precedence).
    if (threshold > 0) {
      if (sr + 1e-9 < threshold) {
        // Threshold not met - fail regardless of status.
        return new QualityGateDecision("GATE_FAIL_THRESHOLD", gateFail);
      }
      // Threshold is met - pass (even if status is FAILED, the threshold requirement is satisfied).
      if ("COMPLETED".equals(normalized) || "FAILED".equals(normalized)) {
        return new QualityGateDecision("PASSED", Result.SUCCESS);
      }
      // Other terminal states with threshold met.
      return new QualityGateDecision("PASSED", Result.SUCCESS);
    }

    // Threshold is disabled (0) - fall back to status-based evaluation.
    if ("FAILED".equals(normalized)) {
      return new QualityGateDecision("FAILED", gateFail);
    }

    // If status is COMPLETED, OK.
    if ("COMPLETED".equals(normalized)) {
      return new QualityGateDecision("PASSED", Result.SUCCESS);
    }

    // Unknown/other terminal states: be conservative if failures/errors present.
    if (failed > 0 || error > 0) {
      return new QualityGateDecision("COMPLETED_WITH_ISSUES", Result.UNSTABLE);
    }

    return new QualityGateDecision("OK", Result.SUCCESS);
  }

  static double computeSuccessRate(int total, int passed) {
    if (total <= 0) return 0.0;
    return Math.round(((double) passed / (double) total) * 100.0 * 100.0) / 100.0;
  }
}

