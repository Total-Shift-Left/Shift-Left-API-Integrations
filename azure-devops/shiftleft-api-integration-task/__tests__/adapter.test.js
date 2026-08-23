'use strict';

// The task's job is translation: task.json input names <-> runner input names, runner outputs ->
// pipeline variables, runner outcome -> TaskResult. Nothing checks those mappings at runtime, so a
// rename on either side silently produces an input that is never read. This test is that check.

const fs = require('fs');
const path = require('path');

jest.mock(
  'azure-pipelines-task-lib/task',
  () => ({
    getInput: jest.fn(),
    setVariable: jest.fn(),
    setResult: jest.fn(),
    warning: jest.fn(),
    debug: jest.fn(),
    TaskResult: { Succeeded: 0, SucceededWithIssues: 1, Failed: 2 },
  }),
  { virtual: true }
);

const tl = require('azure-pipelines-task-lib/task');
const { INPUT_TO_ENV } = require('@totalshiftleft/ci');
const { createCoreFromTaskLib, finalResult, INPUT_MAP, OUTPUT_MAP } = require('../index');

const taskJson = JSON.parse(
  fs.readFileSync(path.join(__dirname, '..', 'task.json'), 'utf8')
);

describe('input mapping', () => {
  test('covers every input the runner reads', () => {
    const missing = Object.keys(INPUT_TO_ENV).filter((name) => !INPUT_MAP[name]);
    expect(missing).toEqual([]);
  });

  test('every mapped name exists in task.json', () => {
    const declared = new Set((taskJson.inputs || []).map((i) => i.name));
    const missing = Object.values(INPUT_MAP).filter((name) => !declared.has(name));
    expect(missing).toEqual([]);
  });

  test('reads through to task-lib and marks required inputs required', () => {
    tl.getInput.mockReturnValue('value');
    const core = createCoreFromTaskLib();

    expect(core.getInput('server-url', { required: true })).toBe('value');
    expect(tl.getInput).toHaveBeenCalledWith('serverUrl', true);

    core.getInput('tenant-id');
    expect(tl.getInput).toHaveBeenCalledWith('tenantId', false);
  });

  test('unknown input names yield empty string rather than throwing', () => {
    const core = createCoreFromTaskLib();
    expect(core.getInput('not-an-input')).toBe('');
  });
});

describe('output mapping', () => {
  test('publishes the documented pipeline variables', () => {
    const core = createCoreFromTaskLib();
    core.setOutput('execution_id', 'e1');
    core.setOutput('trigger_execution_id', 'trig');
    core.setOutput('decision', 'PASSED');

    expect(tl.setVariable).toHaveBeenCalledWith('shiftLeftExecutionId', 'e1', false, true);
    expect(tl.setVariable).toHaveBeenCalledWith('shiftLeftTriggerExecutionId', 'trig', false, true);
    expect(tl.setVariable).toHaveBeenCalledWith('shiftLeftDecision', 'PASSED', false, true);
  });

  test('outputs with no pipeline variable are still recorded', () => {
    const core = createCoreFromTaskLib();
    core.setOutput('task_completion', 'succeeded');
    expect(core.state.outputs.task_completion).toBe('succeeded');
    expect(OUTPUT_MAP.task_completion).toBeUndefined();
  });
});

describe('result mapping', () => {
  test('a gate failure fails the task', () => {
    const core = createCoreFromTaskLib();
    core.setFailed('Quality gate: GATE_FAIL_THRESHOLD');
    expect(finalResult(core.state)).toEqual({
      result: tl.TaskResult.Failed,
      message: 'Quality gate: GATE_FAIL_THRESHOLD',
    });
  });

  test('a non-blocking gate failure is succeeded-with-issues, not success', () => {
    const core = createCoreFromTaskLib();
    core.setOutput('task_completion', 'succeededWithIssues');
    core.setOutput('decision', 'GATE_FAIL_THRESHOLD');
    expect(finalResult(core.state).result).toBe(tl.TaskResult.SucceededWithIssues);
  });

  test('a clean run succeeds', () => {
    const core = createCoreFromTaskLib();
    core.setOutput('task_completion', 'succeeded');
    expect(finalResult(core.state).result).toBe(tl.TaskResult.Succeeded);
  });
});
