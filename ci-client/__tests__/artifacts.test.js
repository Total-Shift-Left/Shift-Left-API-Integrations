'use strict';

const fs = require('fs');
const os = require('os');
const path = require('path');
const { writeJsonSummary, writeXmlTestResults } = require('../src/artifacts');

describe('artifacts', () => {
  let tmp;
  beforeEach(() => {
    tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'sl-art-'));
  });
  afterEach(() => {
    fs.rmSync(tmp, { recursive: true, force: true });
  });

  test('writeJsonSummary', () => {
    const summary = {
      packId: 'p1',
      decision: 'PASSED',
      jsonSummaryRelativePath: 'x.json',
      testResultsXmlRelativePath: 'out.xml',
    };
    const f = writeJsonSummary(tmp, 'out/summary.json', summary);
    expect(fs.existsSync(f)).toBe(true);
    const j = JSON.parse(fs.readFileSync(f, 'utf8'));
    expect(j.packId).toBe('p1');
    expect(j.jsonSummaryRelativePath).toBeUndefined();
    expect(j.testResultsXmlRelativePath).toBeUndefined();
  });

  test('writeXmlTestResults contains testcase', () => {
    const summary = {
      serverUrl: 'https://a',
      tenantId: '',
      packId: 'p1',
      executionId: 'e1',
      total: 1,
      failed: 0,
      error: 0,
    };
    const results = {
      packName: 'P',
      packId: 'p1',
      executionId: 'e1',
      resultsCount: 1,
      results: [
        {
          testId: 't1',
          testName: 'My test',
          status: 'PASSED',
          duration: 500,
          responseTime: 10,
          error: null,
          startTimeIso: '',
          endTimeIso: '',
        },
      ],
    };
    const f = writeXmlTestResults(tmp, 'r.xml', summary, results);
    const xml = fs.readFileSync(f, 'utf8');
    expect(xml).toContain('My test');
    expect(xml).toContain('tests="1"');
  });
});
