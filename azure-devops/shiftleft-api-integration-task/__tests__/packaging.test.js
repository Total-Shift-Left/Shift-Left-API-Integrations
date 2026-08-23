'use strict';

// What ships in the .vsix is decided by vss-extension.json's "files" list, and nothing in the
// build fails when that list and task.json disagree - the extension packages cleanly and only
// breaks on the agent, at "Cannot find module". That is how a .vsix went out containing the task
// source and no dependencies at all. These tests are that missing check.
//
// Agents never run an install step for a task, so the execution target has to be the self-contained
// ncc bundle (npm run build), and the packaged file list has to reach it.

const fs = require('fs');
const path = require('path');

const TASK_DIR = 'shiftleft-api-integration-task';
const extensionRoot = path.join(__dirname, '..', '..');

const vssExtension = JSON.parse(
  fs.readFileSync(path.join(extensionRoot, 'vss-extension.json'), 'utf8')
);
const taskJson = JSON.parse(
  fs.readFileSync(path.join(__dirname, '..', 'task.json'), 'utf8')
);

const packagedPaths = (vssExtension.files || []).map((f) => f.path.replace(/\\/g, '/'));

// tfx packages a "files" entry whole: a file entry contributes itself, a directory entry
// contributes everything beneath it.
function isPackaged(relPath) {
  return packagedPaths.some((entry) => relPath === entry || relPath.startsWith(`${entry}/`));
}

const executionTarget = Object.values(taskJson.execution || {})[0].target;

describe('vsix contents', () => {
  test('the execution target is the bundle, not the unbundled source', () => {
    // index.js requires azure-pipelines-task-lib and @totalshiftleft/ci, and this is a workspaces
    // repo: those live in the ROOT node_modules and are never anywhere near the packaged task.
    expect(executionTarget).toBe('dist/index.js');
  });

  test('the execution target is packaged', () => {
    expect(isPackaged(`${TASK_DIR}/${executionTarget}`)).toBe(true);
  });

  test('task.json and the task icon are packaged', () => {
    expect(isPackaged(`${TASK_DIR}/task.json`)).toBe(true);
    expect(isPackaged(`${TASK_DIR}/icon.png`)).toBe(true);
  });

  test('the whole task directory is never packaged wholesale', () => {
    // A bare directory entry sweeps in __tests__, scripts and any future dev-only folder.
    expect(packagedPaths).not.toContain(TASK_DIR);
  });

  test.each([
    ['tests', `${TASK_DIR}/__tests__/adapter.test.js`],
    ['build scripts', `${TASK_DIR}/scripts/generate-extension-icon.js`],
  ])('%s are excluded', (_label, relPath) => {
    expect(isPackaged(relPath)).toBe(false);
  });

  test('the task contribution points at the packaged task directory', () => {
    const task = (vssExtension.contributions || []).find(
      (c) => c.type === 'ms.vss-distributed-task.task'
    );
    expect(task.properties.name).toBe(TASK_DIR);
  });

  test('every packaged path that is not build output exists', () => {
    // dist/ is produced by `npm run build` and is deliberately not committed, so it is the one
    // entry that may legitimately be absent here.
    const missing = packagedPaths
      .filter((p) => p !== `${TASK_DIR}/dist`)
      .filter((p) => !fs.existsSync(path.join(extensionRoot, p)));
    expect(missing).toEqual([]);
  });
});
