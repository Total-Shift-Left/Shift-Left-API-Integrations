'use strict';

// Azure Pipelines host adapter.
//
// All the behaviour lives in @totalshiftleft/ci so this task, the GitHub Action, the CircleCI orb,
// the Bitbucket pipe and the CLI stay in step. This file only translates between Azure's
// conventions and the runner's: camelCase task inputs -> kebab-case runner inputs, runner outputs
// -> pipeline variables, and the runner's outcome -> a TaskResult.

const tl = require('azure-pipelines-task-lib/task');
const { runShiftLeft } = require('@totalshiftleft/ci');
const { version } = require('./package.json');

// Runner input name -> task.json input name.
const INPUT_MAP = {
  'server-url': 'serverUrl',
  'tenant-id': 'tenantId',
  'api-email': 'apiEmail',
  'api-password': 'apiPassword',
  'pack-id': 'packId',
  'wait-for-completion': 'waitForCompletion',
  'poll-interval-seconds': 'pollIntervalSeconds',
  'timeout-minutes': 'timeoutMinutes',
  'pass-threshold-percent': 'passThresholdPercent',
  'fail-on-error-tests': 'failOnErrorTests',
  'gate-failure-result': 'gateFailureResult',
  'write-json-summary': 'writeJsonSummary',
  'json-summary-path': 'jsonSummaryPath',
  'write-test-results-xml': 'writeTestResultsXml',
  'test-results-xml-path': 'testResultsXmlPath',
  'working-directory': 'workingDirectory',
};

// Runner output name -> pipeline variable name. These are the documented task outputs; keep the
// names stable, pipelines reference them as $(<step-name>.shiftLeftExecutionId).
const OUTPUT_MAP = {
  execution_id: 'shiftLeftExecutionId',
  trigger_execution_id: 'shiftLeftTriggerExecutionId',
  decision: 'shiftLeftDecision',
};

function createCoreFromTaskLib() {
  const state = { outputs: {}, failureMessage: null, warned: false };

  return {
    state,
    getInput(name, opts) {
      const taskInput = INPUT_MAP[name];
      if (!taskInput) return '';
      const required = Boolean(opts && opts.required);
      const value = tl.getInput(taskInput, required);
      return value == null ? '' : String(value);
    },
    setOutput(name, value) {
      const str = value == null ? '' : String(value);
      state.outputs[name] = str;
      const variable = OUTPUT_MAP[name];
      // isSecret=false, isOutput=true — output variables are addressed via the step's name.
      if (variable) tl.setVariable(variable, str, false, true);
    },
    setFailed(message) {
      state.failureMessage = String(message);
    },
    info(message) {
      console.log(String(message));
    },
    warning(message) {
      state.warned = true;
      tl.warning(String(message));
    },
    debug(message) {
      tl.debug(String(message));
    },
  };
}

function finalResult(state) {
  if (state.failureMessage) {
    return { result: tl.TaskResult.Failed, message: state.failureMessage };
  }
  // The runner reports its own platform-neutral outcome; prefer it over guessing from warnings.
  if (state.outputs.task_completion === 'succeededWithIssues') {
    return {
      result: tl.TaskResult.SucceededWithIssues,
      message: `ShiftLeft quality gate: ${state.outputs.decision || 'issues found'}`,
    };
  }
  return { result: tl.TaskResult.Succeeded, message: '' };
}

async function run() {
  const core = createCoreFromTaskLib();
  try {
    await runShiftLeft(core, {
      userAgent: `shiftleft-api-integration-task/${version}`,
      // Azure exposes the repo root here; the runner falls back to cwd if it is unset.
      workingDirEnvVars: ['BUILD_SOURCESDIRECTORY'],
    });
    const { result, message } = finalResult(core.state);
    tl.setResult(result, message);
  } catch (err) {
    tl.setResult(tl.TaskResult.Failed, err && err.message ? err.message : String(err));
  }
}

// Azure runs this file directly; the guard keeps the adapter unit-testable.
if (require.main === module) {
  run();
}

module.exports = { run, createCoreFromTaskLib, finalResult, INPUT_MAP, OUTPUT_MAP };
