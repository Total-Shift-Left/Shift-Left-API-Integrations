'use strict';

// action.yml is the only place a user can see what this action accepts, and the runner is the only
// place those names are read. Nothing links them at runtime, so an input renamed on one side
// silently becomes a no-op on the other. This test is that link.

const fs = require('fs');
const path = require('path');
const { INPUT_TO_ENV } = require('@totalshiftleft/ci');

const actionYml = fs.readFileSync(path.join(__dirname, '..', 'action.yml'), 'utf8');

function section(name) {
  // Top-level block: from "name:" to the next zero-indent key.
  const re = new RegExp(`^${name}:\\r?\\n([\\s\\S]*?)(?=^\\S)`, 'm');
  const m = actionYml.match(re);
  return m ? m[1] : '';
}

function keysOf(blockText) {
  return blockText
    .split(/\r?\n/)
    .map((line) => line.match(/^ {2}([a-z0-9_-]+):\s*$/i))
    .filter(Boolean)
    .map((m) => m[1]);
}

describe('action.yml contract', () => {
  const declaredInputs = keysOf(section('inputs'));
  const declaredOutputs = keysOf(section('outputs'));
  const runnerInputs = Object.keys(INPUT_TO_ENV);

  test('declares every input the runner reads', () => {
    expect(declaredInputs.length).toBeGreaterThan(0);
    const missing = runnerInputs.filter((name) => !declaredInputs.includes(name));
    expect(missing).toEqual([]);
  });

  test('declares no input the runner ignores', () => {
    const unread = declaredInputs.filter((name) => !runnerInputs.includes(name));
    expect(unread).toEqual([]);
  });

  test('required inputs are the four with no sensible default', () => {
    const required = declaredInputs.filter((name) => {
      const block = section('inputs').split(new RegExp(`^ {2}${name}:\\s*$`, 'm'))[1] || '';
      return /^\s+required:\s*true/m.test(block.split(/^ {2}\S/m)[0] || '');
    });
    expect(required.sort()).toEqual(['api-email', 'api-password', 'pack-id', 'server-url']);
  });

  test('declares the outputs the runner sets', () => {
    for (const name of ['execution_id', 'trigger_execution_id', 'decision', 'success_rate', 'passed', 'task_completion']) {
      expect(declaredOutputs).toContain(name);
    }
  });

  test('runs a node20 bundle', () => {
    expect(actionYml).toMatch(/using:\s*'node20'/);
    expect(actionYml).toMatch(/main:\s*'dist\/index\.js'/);
  });
});
