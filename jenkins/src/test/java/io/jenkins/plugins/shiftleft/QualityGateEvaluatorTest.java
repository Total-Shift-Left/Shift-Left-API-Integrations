package io.jenkins.plugins.shiftleft;

import hudson.model.Result;
import org.junit.Test;

import static org.junit.Assert.*;

public class QualityGateEvaluatorTest {

  @Test
  public void failedStatusUsesConfiguredGateResult() {
    // When threshold is not met, it should return GATE_FAIL_THRESHOLD regardless of status
    QualityGateDecision d = QualityGateEvaluator.evaluate(
        "FAILED",
        10, 9, 1, 0,
        90.0,
        100.0,
        true,
        Result.UNSTABLE
    );
    assertEquals("GATE_FAIL_THRESHOLD", d.decision);
    assertEquals(Result.UNSTABLE, d.jenkinsResult);
  }

  @Test
  public void failedStatusPassesWhenThresholdMet() {
    // When threshold IS met, it should pass even if status is FAILED
    QualityGateDecision d = QualityGateEvaluator.evaluate(
        "FAILED",
        10, 4, 6, 0,
        40.0,
        30.0,  // Threshold is 30%, success rate is 40% -> should pass
        false,
        Result.FAILURE
    );
    assertEquals("PASSED", d.decision);
    assertEquals(Result.SUCCESS, d.jenkinsResult);
  }

  @Test
  public void failedStatusWhenThresholdDisabled() {
    // When threshold is disabled (0), status-based evaluation should apply
    QualityGateDecision d = QualityGateEvaluator.evaluate(
        "FAILED",
        10, 9, 1, 0,
        90.0,
        0.0,  // Threshold disabled
        false,
        Result.UNSTABLE
    );
    assertEquals("FAILED", d.decision);
    assertEquals(Result.UNSTABLE, d.jenkinsResult);
  }

  @Test
  public void errorTestsFailWhenEnabled() {
    QualityGateDecision d = QualityGateEvaluator.evaluate(
        "COMPLETED",
        10, 9, 0, 1,
        90.0,
        90.0,
        true,
        Result.FAILURE
    );
    assertEquals("GATE_FAIL_ERROR_TESTS", d.decision);
    assertEquals(Result.FAILURE, d.jenkinsResult);
  }

  @Test
  public void thresholdFailUsesConfiguredGateResult() {
    QualityGateDecision d = QualityGateEvaluator.evaluate(
        "COMPLETED",
        10, 8, 2, 0,
        80.0,
        90.0,
        false,
        Result.UNSTABLE
    );
    assertEquals("GATE_FAIL_THRESHOLD", d.decision);
    assertEquals(Result.UNSTABLE, d.jenkinsResult);
  }

  @Test
  public void passesWhenThresholdMet() {
    QualityGateDecision d = QualityGateEvaluator.evaluate(
        "COMPLETED",
        10, 10, 0, 0,
        100.0,
        95.0,
        true,
        Result.FAILURE
    );
    assertEquals("PASSED", d.decision);
    assertEquals(Result.SUCCESS, d.jenkinsResult);
  }

  @Test
  public void computeSuccessRateIsRoundedTo2dp() {
    assertEquals(33.33, QualityGateEvaluator.computeSuccessRate(3, 1), 0.0001);
  }
}

