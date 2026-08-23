'use strict';

const { runShiftLeft, mapGateFailureInputToSeverity } = require('./runShiftLeft');

const INPUT_TO_ENV = {
  'server-url': 'SHIFTLEFT_SERVER_URL',
  'tenant-id': 'SHIFTLEFT_TENANT_ID',
  'api-email': 'SHIFTLEFT_API_EMAIL',
  'api-password': 'SHIFTLEFT_API_PASSWORD',
  'pack-id': 'SHIFTLEFT_PACK_ID',
  'wait-for-completion': 'SHIFTLEFT_WAIT_FOR_COMPLETION',
  'poll-interval-seconds': 'SHIFTLEFT_POLL_INTERVAL_SECONDS',
  'timeout-minutes': 'SHIFTLEFT_TIMEOUT_MINUTES',
  'pass-threshold-percent': 'SHIFTLEFT_PASS_THRESHOLD_PERCENT',
  'fail-on-error-tests': 'SHIFTLEFT_FAIL_ON_ERROR_TESTS',
  'gate-failure-result': 'SHIFTLEFT_GATE_FAILURE_RESULT',
  'write-json-summary': 'SHIFTLEFT_WRITE_JSON_SUMMARY',
  'json-summary-path': 'SHIFTLEFT_JSON_SUMMARY_PATH',
  'write-test-results-xml': 'SHIFTLEFT_WRITE_TEST_RESULTS_XML',
  'test-results-xml-path': 'SHIFTLEFT_TEST_RESULTS_XML_PATH',
  'working-directory': 'SHIFTLEFT_WORKING_DIRECTORY',
};

function createCoreFromProcessEnv(env = process.env, logger = console) {
  const outputs = {};
  const info = logger.info || ((m) => console.log(m));
  const warn = logger.warn || ((m) => console.warn(m));
  const debug = logger.debug || (() => {});

  return {
    outputs,
    getInput(name, opts) {
      const envKey = INPUT_TO_ENV[name];
      if (!envKey) return '';
      const v = env[envKey];
      if (opts && opts.required && (v === undefined || v === null || String(v).trim() === '')) {
        throw new Error(`Required environment variable ${envKey} is not set`);
      }
      return v === undefined || v === null ? '' : String(v);
    },
    setOutput(k, v) {
      outputs[k] = String(v);
    },
    setFailed(m) {
      const msg = String(m);
      const err = new Error(msg);
      err.shiftLeftFailed = true;
      throw err;
    },
    warning: (m) => warn(String(m)),
    info: (m) => info(String(m)),
    debug: (m) => debug(String(m)),
  };
}

async function runShiftLeftFromEnv(options = {}) {
  const { env = process.env, fetchFn, logger, workingDirEnvVars, userAgent } = options;
  const core = options.core || createCoreFromProcessEnv(env, logger || console);
  return runShiftLeft(core, { fetchFn, workingDirEnvVars, userAgent });
}

module.exports = {
  runShiftLeftFromEnv,
  createCoreFromProcessEnv,
  mapGateFailureInputToSeverity,
  INPUT_TO_ENV,
};
