'use strict';

// One implementation of "run a Shift-Left test pack from CI", shared by every integration:
// the GitHub Action, the Azure DevOps task, the CircleCI orb, the Bitbucket pipe, the GitLab
// component and the standalone `shiftleft-ci` CLI / totalshiftleft/ci-runner image.
//
// Platforms differ only in how inputs arrive and how a result is reported, so they supply a
// small `core` adapter ({ getInput, setOutput, setFailed, info, warning, debug }) and this
// package does the rest. Before this existed the same ~600 lines were copy-pasted into five
// plugin folders and had already drifted apart.

const { createApiClient } = require('./apiClient');
const { evaluate, computeSuccessRate } = require('./qualityGate');
const { writeJsonSummary, writeXmlTestResults } = require('./artifacts');
const { runShiftLeft, mapGateFailureInputToSeverity } = require('./runShiftLeft');
const { runShiftLeftFromEnv, createCoreFromProcessEnv, INPUT_TO_ENV } = require('./envCore');

module.exports = {
  // Core entrypoints
  runShiftLeft,
  runShiftLeftFromEnv,
  // Adapters
  createCoreFromProcessEnv,
  INPUT_TO_ENV,
  // Building blocks
  createApiClient,
  evaluate,
  computeSuccessRate,
  writeJsonSummary,
  writeXmlTestResults,
  mapGateFailureInputToSeverity,
};
