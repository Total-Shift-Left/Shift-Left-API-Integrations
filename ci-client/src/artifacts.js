'use strict';

const fs = require('fs');
const path = require('path');

function nullToEmpty(s) {
  return s == null ? '' : String(s);
}

function truncate(s, max) {
  if (s.length <= max) return s;
  return s.slice(0, max) + '...';
}

function toSeconds(duration) {
  const d = Number(duration) || 0;
  if (d <= 0) return 0.0;
  if (d > 1000) return d / 1000.0;
  return d;
}

function xmlEscape(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

/**
 * @param {object} summary — plain object for JSON
 */
function writeJsonSummary(outDir, relativePath, summary) {
  const full = path.join(outDir, relativePath);
  fs.mkdirSync(path.dirname(full), { recursive: true });
  const forJson = { ...summary };
  delete forJson.jsonSummaryRelativePath;
  delete forJson.testResultsXmlRelativePath;
  fs.writeFileSync(full, JSON.stringify(forJson, null, 2), 'utf8');
  return full;
}

/**
 * XML test results (testsuite document) for CI reporting / upload-artifact.
 */
function writeXmlTestResults(outDir, relativePath, summary, results) {
  const suiteName = `ShiftLeft Test Pack ${nullToEmpty(summary.packId)}`;
  const timestamp = new Date().toISOString();

  const tests = Math.max(summary.total || 0, results.resultsCount || 0);
  const failures = Math.max(0, summary.failed || 0);
  const errors = Math.max(0, summary.error || 0);

  let xml = '<?xml version="1.0" encoding="UTF-8"?>\n';
  xml += `<testsuite name="${xmlEscape(suiteName)}" tests="${tests}" failures="${failures}" errors="${errors}" timestamp="${xmlEscape(timestamp)}">\n`;
  xml += '  <properties>\n';
  xml += `    <property name="serverUrl" value="${xmlEscape(nullToEmpty(summary.serverUrl))}"/>\n`;
  xml += `    <property name="tenantId" value="${xmlEscape(nullToEmpty(summary.tenantId))}"/>\n`;
  xml += `    <property name="packId" value="${xmlEscape(nullToEmpty(summary.packId))}"/>\n`;
  xml += `    <property name="executionId" value="${xmlEscape(nullToEmpty(summary.executionId))}"/>\n`;
  xml += `    <property name="resultsPackName" value="${xmlEscape(nullToEmpty(results.packName))}"/>\n`;
  xml += `    <property name="resultsPackId" value="${xmlEscape(nullToEmpty(results.packId))}"/>\n`;
  xml += `    <property name="resultsExecutionId" value="${xmlEscape(nullToEmpty(results.executionId))}"/>\n`;
  xml += `    <property name="resultsStartTime" value="${xmlEscape(nullToEmpty(results.startTimeIso))}"/>\n`;
  xml += `    <property name="resultsEndTime" value="${xmlEscape(nullToEmpty(results.endTimeIso))}"/>\n`;
  xml += '  </properties>\n';

  for (const item of results.results) {
    const testName = item.testName != null ? item.testName : nullToEmpty(item.testId);
    const status = (item.status || '').trim().toUpperCase();
    const seconds = toSeconds(item.duration);

    xml += `  <testcase classname="ShiftLeft" name="${xmlEscape(testName)}" time="${seconds.toFixed(3)}">\n`;

    if (status === 'FAILED') {
      const err = nullToEmpty(item.error);
      xml += `    <failure message="${xmlEscape(truncate(err, 500))}">${xmlEscape(truncate(err, 2000))}</failure>\n`;
    } else if (status === 'ERROR') {
      const err = nullToEmpty(item.error);
      xml += `    <error message="${xmlEscape(truncate(err, 500))}">${xmlEscape(truncate(err, 2000))}</error>\n`;
    }

    const shiftleftMeta = `shiftleft.responseTimeMs=${item.responseTime} shiftleft.startTime=${nullToEmpty(item.startTimeIso)} shiftleft.endTime=${nullToEmpty(item.endTimeIso)}`;
    xml += `    <system-out>${xmlEscape(shiftleftMeta)}</system-out>\n`;
    xml += '  </testcase>\n';
  }

  xml += '</testsuite>\n';

  const full = path.join(outDir, relativePath);
  fs.mkdirSync(path.dirname(full), { recursive: true });
  fs.writeFileSync(full, xml, 'utf8');
  return full;
}

module.exports = { writeJsonSummary, writeXmlTestResults };
