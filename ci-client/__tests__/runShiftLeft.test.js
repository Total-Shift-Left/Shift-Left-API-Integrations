'use strict';

const fs = require('fs');
const os = require('os');
const path = require('path');
const {
  runShiftLeftFromEnv,
  mapGateFailureInputToSeverity,
  createCoreFromProcessEnv,
  INPUT_TO_ENV,
} = require('../src/envCore');

function createMockCore(inputs) {
  const outputs = {};
  const failures = [];
  return {
    outputs,
    failures,
    getInput(name, opts) {
      const v = inputs[name];
      if (opts && opts.required && (v === undefined || v === null || String(v) === '')) {
        throw new Error(`Input required and not supplied: ${name}`);
      }
      return v === undefined || v === null ? '' : String(v);
    },
    setOutput(k, v) {
      outputs[k] = v;
    },
    setFailed(m) {
      failures.push(String(m));
      throw new Error(String(m));
    },
    info: jest.fn(),
    warning: jest.fn(),
    debug: jest.fn(),
  };
}

describe('mapGateFailureInputToSeverity', () => {
  test('maps hyphenated input', () => {
    expect(mapGateFailureInputToSeverity('succeeded-with-issues')).toBe('UNSTABLE');
  });
  test('maps camelCase', () => {
    expect(mapGateFailureInputToSeverity('succeededWithIssues')).toBe('UNSTABLE');
  });
  test('default failed', () => {
    expect(mapGateFailureInputToSeverity('failed')).toBe('FAILURE');
  });
});

describe('createCoreFromProcessEnv', () => {
  test('maps SHIFTLEFT_SERVER_URL to server-url', () => {
    const core = createCoreFromProcessEnv({
      SHIFTLEFT_SERVER_URL: 'https://x',
      SHIFTLEFT_API_EMAIL: 'e',
      SHIFTLEFT_API_PASSWORD: 'p',
      SHIFTLEFT_PACK_ID: 'pack1',
    });
    expect(core.getInput('server-url', { required: true })).toBe('https://x');
    expect(INPUT_TO_ENV['server-url']).toBe('SHIFTLEFT_SERVER_URL');
  });

  test('throws when required env missing', () => {
    const core = createCoreFromProcessEnv({});
    expect(() => core.getInput('server-url', { required: true })).toThrow(
      /SHIFTLEFT_SERVER_URL/
    );
  });
});

describe('runShiftLeftFromEnv', () => {
  let tmpDir;

  beforeEach(() => {
    tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'cc-shiftleft-'));
    process.env.CIRCLE_WORKING_DIRECTORY = tmpDir;
  });

  afterEach(() => {
    delete process.env.CIRCLE_WORKING_DIRECTORY;
    fs.rmSync(tmpDir, { recursive: true, force: true });
  });

  test('trigger-only writes summary and outputs', async () => {
    const bodies = [
      JSON.stringify({ token: 'T' }),
      // F7: the pre-trigger status probe. No prior execution in this scenario.
      JSON.stringify({ packId: 'p1', status: 'IDLE' }),
      JSON.stringify({ executionId: 'e0', packId: 'p1', status: 'RUNNING' }),
    ];
    let i = 0;
    const fetchFn = async () => ({
      ok: true,
      status: 200,
      text: async () => bodies[i++],
      headers: new Map(),
    });

    const core = createMockCore({
      'server-url': 'https://sl.test',
      'tenant-id': '',
      'api-email': 'u@test',
      'api-password': 'pw',
      'pack-id': 'p1',
      'wait-for-completion': 'false',
      'write-json-summary': 'true',
      'json-summary-path': 'artifacts/summary.json',
      'write-test-results-xml': 'false',
    });

    await runShiftLeftFromEnv({ core, fetchFn });

    expect(core.outputs.decision).toBe('TRIGGER_ONLY');
    expect(core.outputs.passed).toBe('true');
    expect(fs.existsSync(path.join(tmpDir, 'artifacts', 'summary.json'))).toBe(true);
  });

  test('completed run writes xml and sets passed when gate ok', async () => {
    const bodies = [
      JSON.stringify({ token: 'T' }),
      // F7: the pre-trigger status probe. No prior execution in this scenario.
      JSON.stringify({ packId: 'p1', status: 'IDLE' }),
      JSON.stringify({ executionId: 'e1', packId: 'p1', status: 'RUNNING' }),
      JSON.stringify({
        packId: 'p1',
        status: 'COMPLETED',
        executionId: 'e1',
        summary: { total: 2, passed: 2, failed: 0, error: 0, successRate: 100 },
      }),
      JSON.stringify({
        packId: 'p1',
        packName: 'PN',
        executionId: 'e1',
        results: [{ testId: 't1', testName: 'A', status: 'PASSED', duration: 100 }],
        summary: { total: 2, passed: 2, failed: 0, error: 0, successRate: 100 },
      }),
    ];
    let i = 0;
    const fetchFn = async () => ({
      ok: true,
      status: 200,
      text: async () => bodies[i++],
      headers: new Map(),
    });

    const core = createMockCore({
      'server-url': 'https://sl.test',
      'api-email': 'u@test',
      'api-password': 'pw',
      'pack-id': 'p1',
      'wait-for-completion': 'true',
      'poll-interval-seconds': '1',
      'timeout-minutes': '5',
      'pass-threshold-percent': '100',
      'fail-on-error-tests': 'true',
      'gate-failure-result': 'failed',
      'write-json-summary': 'true',
      'write-test-results-xml': 'true',
      'test-results-xml-path': 'test-results/results.xml',
    });

    await runShiftLeftFromEnv({ core, fetchFn });

    expect(core.outputs.decision).toBe('PASSED');
    expect(core.outputs.passed).toBe('true');
    expect(core.failures).toHaveLength(0);
    expect(fs.existsSync(path.join(tmpDir, 'test-results', 'results.xml'))).toBe(true);
  });

  test('gate threshold fail with succeeded-with-issues warns and does not setFailed', async () => {
    const bodies = [
      JSON.stringify({ token: 'T' }),
      // F7: the pre-trigger status probe. No prior execution in this scenario.
      JSON.stringify({ packId: 'p1', status: 'IDLE' }),
      JSON.stringify({ executionId: 'e1', packId: 'p1', status: 'RUNNING' }),
      JSON.stringify({
        packId: 'p1',
        status: 'COMPLETED',
        executionId: 'e1',
        summary: { total: 10, passed: 8, failed: 2, error: 0, successRate: 80 },
      }),
      JSON.stringify({
        packId: 'p1',
        packName: 'PN',
        executionId: 'e1',
        results: [],
        summary: { total: 10, passed: 8, failed: 2, error: 0, successRate: 80 },
      }),
    ];
    let i = 0;
    const fetchFn = async () => ({
      ok: true,
      status: 200,
      text: async () => bodies[i++],
      headers: new Map(),
    });

    const core = createMockCore({
      'server-url': 'https://sl.test',
      'api-email': 'u@test',
      'api-password': 'pw',
      'pack-id': 'p1',
      'wait-for-completion': 'true',
      'poll-interval-seconds': '1',
      'timeout-minutes': '5',
      'pass-threshold-percent': '100',
      'fail-on-error-tests': 'true',
      'gate-failure-result': 'succeeded-with-issues',
      'write-json-summary': 'true',
      'write-test-results-xml': 'false',
    });

    await runShiftLeftFromEnv({ core, fetchFn });

    expect(core.outputs.decision).toBe('GATE_FAIL_THRESHOLD');
    expect(core.outputs.passed).toBe('false');
    expect(core.failures).toHaveLength(0);
    expect(core.warning).toHaveBeenCalled();
  });
});

// F7 regression: /status reports the pack's most recent execution, so a poll issued right after
// triggering can still describe the PREVIOUS run. The client must not grade that one. Before the
// fix this graded e_old (successRate 0) and failed the build on a run that had already finished.
describe('F7 - does not grade the previous execution', () => {
  test('waits for a new executionId before applying the gate', async () => {
    const staleStatus = {
      packId: 'p1',
      status: 'COMPLETED',
      executionId: 'e_old',
      summary: { total: 2, passed: 0, failed: 2, error: 0, successRate: 0 },
    };
    const freshStatus = {
      packId: 'p1',
      status: 'COMPLETED',
      executionId: 'e_new',
      summary: { total: 2, passed: 2, failed: 0, error: 0, successRate: 100 },
    };

    const bodies = [
      JSON.stringify({ token: 'T' }),
      JSON.stringify(staleStatus),                                        // pre-trigger probe
      JSON.stringify({ executionId: 'trig', packId: 'p1', status: 'RUNNING' }),
      JSON.stringify(staleStatus),                                        // poll 1 - still the old run
      JSON.stringify(freshStatus),                                        // poll 2 - our run
      JSON.stringify({
        packId: 'p1',
        packName: 'PN',
        executionId: 'e_new',
        results: [{ testId: 't1', testName: 'A', status: 'PASSED', duration: 1 }],
        summary: { total: 2, passed: 2, failed: 0, error: 0, successRate: 100 },
      }),
    ];
    const urls = [];
    let i = 0;
    const fetchFn = async (url) => {
      urls.push(String(url));
      return { ok: true, status: 200, text: async () => bodies[i++], headers: new Map() };
    };

    const core = createMockCore({
      'server-url': 'https://sl.test',
      'api-email': 'u@test',
      'api-password': 'pw',
      'pack-id': 'p1',
      'poll-interval-seconds': '1',
      'pass-threshold-percent': '100',
      'write-json-summary': 'false',
      'write-test-results-xml': 'false',
    });

    await runShiftLeftFromEnv({ core, fetchFn });

    // Graded our run, not the stale one.
    expect(core.outputs.execution_id).toBe('e_new');
    expect(core.outputs.decision).toBe('PASSED');
    expect(core.outputs.passed).toBe('true');
    // Results were fetched for the new execution only.
    expect(urls.some((u) => u.includes('executionId=e_new'))).toBe(true);
    expect(urls.some((u) => u.includes('executionId=e_old'))).toBe(false);
    // All six calls consumed: login, probe, trigger, two polls, results.
    expect(i).toBe(6);
  }, 20000);
});

// The five plugin copies this package replaced disagreed on this default: CircleCI, Bitbucket and
// GitLab wrote JUnit XML unless told not to; GitHub and Azure did not write it unless told to. Every
// host's documentation promised the former, so that is what the shared runner does. Pin it — a
// silently-missing results file is the kind of thing nobody notices until a release.
describe('artifact defaults', () => {
  let defaultsTmpDir;

  beforeEach(() => {
    defaultsTmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'sl-defaults-'));
    process.env.CIRCLE_WORKING_DIRECTORY = defaultsTmpDir;
  });

  afterEach(() => {
    delete process.env.CIRCLE_WORKING_DIRECTORY;
    fs.rmSync(defaultsTmpDir, { recursive: true, force: true });
  });

  test('writes JUnit XML when the input is not supplied', async () => {
    const bodies = [
      JSON.stringify({ token: 'T' }),
      JSON.stringify({ packId: 'p1', status: 'IDLE' }),
      JSON.stringify({ executionId: 'trig', packId: 'p1', status: 'RUNNING' }),
      JSON.stringify({
        packId: 'p1',
        status: 'COMPLETED',
        executionId: 'e1',
        summary: { total: 1, passed: 1, failed: 0, error: 0, successRate: 100 },
      }),
      JSON.stringify({
        packId: 'p1',
        packName: 'PN',
        executionId: 'e1',
        results: [{ testId: 't1', testName: 'A', status: 'PASSED', duration: 1 }],
        summary: { total: 1, passed: 1, failed: 0, error: 0, successRate: 100 },
      }),
    ];
    let i = 0;
    const fetchFn = async () => ({
      ok: true,
      status: 200,
      text: async () => bodies[i++],
      headers: new Map(),
    });

    const core = createMockCore({
      'server-url': 'https://sl.test',
      'api-email': 'u@test',
      'api-password': 'pw',
      'pack-id': 'p1',
      'poll-interval-seconds': '1',
      // write-json-summary and write-test-results-xml deliberately not supplied.
    });

    await runShiftLeftFromEnv({ core, fetchFn });

    expect(fs.existsSync(path.join(defaultsTmpDir, 'shiftleft-test-pack-results.xml'))).toBe(true);
    expect(fs.existsSync(path.join(defaultsTmpDir, 'shiftleft-test-pack-summary.json'))).toBe(true);
  }, 20000);
});
