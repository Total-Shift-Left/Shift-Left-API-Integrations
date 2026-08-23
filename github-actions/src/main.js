'use strict';

// Thin host adapter. @actions/core already matches the shape @totalshiftleft/ci expects
// (getInput/setOutput/setFailed/info/warning/debug), so there is nothing to translate.
const core = require('@actions/core');
const { runShiftLeft } = require('@totalshiftleft/ci');
const { version } = require('../package.json');

runShiftLeft(core, { userAgent: `shiftleft-github-action/${version}` }).catch((err) => {
  core.setFailed(err && err.message ? err.message : String(err));
});
