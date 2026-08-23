'use strict';

const { evaluate, computeSuccessRate } = require('../src/qualityGate');

describe('computeSuccessRate', () => {
  test('zero total', () => {
    expect(computeSuccessRate(0, 0)).toBe(0);
  });
  test('half', () => {
    expect(computeSuccessRate(4, 2)).toBe(50);
  });
});

describe('evaluate', () => {
  const gateFail = 'FAILURE';

  test('fail on error tests', () => {
    const r = evaluate('COMPLETED', 5, 4, 0, 1, 80, 100, true, gateFail);
    expect(r.decision).toBe('GATE_FAIL_ERROR_TESTS');
    expect(r.taskCompletion).toBe('failed');
  });

  test('threshold not met', () => {
    const r = evaluate('COMPLETED', 10, 8, 2, 0, 80, 100, false, gateFail);
    expect(r.decision).toBe('GATE_FAIL_THRESHOLD');
    expect(r.taskCompletion).toBe('failed');
  });

  test('threshold met with FAILED status still passes', () => {
    const r = evaluate('FAILED', 10, 10, 0, 0, 100, 100, false, gateFail);
    expect(r.decision).toBe('PASSED');
    expect(r.taskCompletion).toBe('succeeded');
  });

  test('threshold disabled: FAILED fails', () => {
    const r = evaluate('FAILED', 10, 9, 1, 0, 90, 0, false, gateFail);
    expect(r.decision).toBe('FAILED');
    expect(r.taskCompletion).toBe('failed');
  });

  test('threshold disabled: COMPLETED passes', () => {
    const r = evaluate('COMPLETED', 5, 5, 0, 0, 100, 0, false, gateFail);
    expect(r.decision).toBe('PASSED');
    expect(r.taskCompletion).toBe('succeeded');
  });

  test('completed with issues -> succeededWithIssues', () => {
    const r = evaluate('WEIRD', 5, 5, 1, 0, 100, 0, false, gateFail);
    expect(r.decision).toBe('COMPLETED_WITH_ISSUES');
    expect(r.taskCompletion).toBe('succeededWithIssues');
  });

  test('gate failure severity UNSTABLE', () => {
    const r = evaluate('COMPLETED', 10, 8, 2, 0, 80, 100, false, 'UNSTABLE');
    expect(r.decision).toBe('GATE_FAIL_THRESHOLD');
    expect(r.taskCompletion).toBe('succeededWithIssues');
  });
});
