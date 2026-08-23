#!/usr/bin/env node
'use strict';

const { runShiftLeftFromEnv, createCoreFromProcessEnv } = require('../src/envCore');

function logOutputs(core) {
  const o = core.outputs || {};
  const keys = [
    'execution_id',
    'trigger_execution_id',
    'decision',
    'success_rate',
    'json_summary_path',
    'test_results_xml_path',
    'passed',
    'task_completion',
  ];
  for (const k of keys) {
    if (o[k] !== undefined) console.log(`[ShiftLeft] Output ${k}=${o[k]}`);
  }
}

async function main() {
  const core = createCoreFromProcessEnv(process.env);
  try {
    await runShiftLeftFromEnv({ core, fetchFn: globalThis.fetch });
    logOutputs(core);
    process.exit(0);
  } catch (e) {
    logOutputs(core);
    const msg = e && e.message ? e.message : String(e);
    console.error(`[ShiftLeft] ${msg}`);
    process.exit(1);
  }
}

main();
