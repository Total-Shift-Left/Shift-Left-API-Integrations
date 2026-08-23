'use strict';

const { version } = require('../package.json');

const DEFAULT_USER_AGENT = `totalshiftleft-ci/${version}`;

function stripTrailingSlash(s) {
  let out = (s || '').trim();
  while (out.endsWith('/')) out = out.slice(0, -1);
  return out;
}

function urlEncodePathSegment(s) {
  return encodeURIComponent(s).replace(/\+/g, '%20');
}

function urlEncodeQuery(s) {
  return encodeURIComponent(s);
}

/**
 * @param {object} opts
 * @param {string} opts.baseUrl
 * @param {string|null} [opts.tenantId]
 * @param {(msg: string) => void} [opts.log]
 * @param {typeof fetch} [opts.fetchFn]
 */
function createApiClient(opts) {
  const USER_AGENT = opts.userAgent || DEFAULT_USER_AGENT;
  const baseUrl = stripTrailingSlash(opts.baseUrl);
  const tenantId =
    opts.tenantId == null || String(opts.tenantId).trim() === ''
      ? null
      : String(opts.tenantId).trim();
  const log = opts.log || (() => {});
  const fetchFn = opts.fetchFn || globalThis.fetch;

  if (typeof fetchFn !== 'function') {
    throw new Error('fetch is not available; pass fetchFn (Node 18+)');
  }

  let bearerToken = null;

  function baseHeaders() {
    const h = {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'User-Agent': USER_AGENT,
    };
    if (tenantId) h['X-Tenant-ID'] = tenantId;
    return h;
  }

  function authedHeaders() {
    if (!bearerToken || !String(bearerToken).trim()) {
      throw new Error('No bearer token set; call login() first');
    }
    return {
      ...baseHeaders(),
      Authorization: `Bearer ${bearerToken}`,
    };
  }

  async function sendJson(method, path, { body, auth = true } = {}) {
    const url = baseUrl + path;
    const headers = auth ? authedHeaders() : baseHeaders();
    log(`[ShiftLeft] Request: ${method} ${url}`);
    const init = { method, headers, redirect: 'follow' };
    if (body !== undefined) init.body = body;
    const resp = await fetchFn(url, init);
    const text = await resp.text();
    log(`[ShiftLeft] Response: HTTP ${resp.status}`);
    if (!resp.ok) {
      const snippet = text && text.length > 2000 ? text.slice(0, 2000) + '...' : text || '';
      let msg = `HTTP ${resp.status} calling ${url}`;
      if (resp.status === 502) {
        msg +=
          '. Bad Gateway: proxy/server may not reach the backend. Check backend health and URL.';
      }
      log(`[ShiftLeft] ${msg}`);
      if (snippet) log(`[ShiftLeft] Response body: ${snippet}`);
      throw new Error(msg);
    }
    try {
      return text ? JSON.parse(text) : {};
    } catch (e) {
      log(`[ShiftLeft] Invalid JSON from ${url}`);
      throw new Error(`Invalid JSON from ${url}: ${e.message}`);
    }
  }

  async function login(email, password) {
    const json = await sendJson('POST', '/api/v1/login', {
      body: JSON.stringify({ email, password }),
      auth: false,
    });
    const token = json.token;
    if (token == null || String(token).trim() === '') {
      throw new Error('Login succeeded but token was missing in response');
    }
    return String(token);
  }

  function setBearerToken(token) {
    bearerToken = token;
  }

  async function triggerTestPack(packId) {
    const enc = urlEncodePathSegment(packId);
    const json = await sendJson('POST', `/api/v1/test-packs/${enc}/run`, {
      body: undefined,
      auth: true,
    });
    return {
      message: json.message ?? null,
      executionId: json.executionId ?? null,
      packId: json.packId ?? null,
      status: json.status ?? null,
      startTimeIso: json.startTime ?? null,
    };
  }

  async function getStatus(packId) {
    const enc = urlEncodePathSegment(packId);
    const json = await sendJson('GET', `/api/v1/test-packs/${enc}/status`, { auth: true });
    const summary = json.summary || {};
    return {
      packId: json.packId ?? null,
      name: json.name ?? null,
      status: json.status ?? null,
      executionId: json.executionId ?? null,
      startTimeIso: json.startTime ?? null,
      endTimeIso: json.endTime ?? null,
      summaryTotal: summary.total != null ? Number(summary.total) : 0,
      summaryPassed: summary.passed != null ? Number(summary.passed) : 0,
      summaryFailed: summary.failed != null ? Number(summary.failed) : 0,
      summaryError: summary.error != null ? Number(summary.error) : 0,
      summarySuccessRate:
        summary.successRate != null ? Number(summary.successRate) : 0.0,
    };
  }

  async function getResults(packId, executionId) {
    const enc = urlEncodePathSegment(packId);
    let path = `/api/v1/test-packs/${enc}/results`;
    if (executionId != null && String(executionId).trim() !== '') {
      path += `?executionId=${urlEncodeQuery(String(executionId).trim())}`;
    }
    const json = await sendJson('GET', path, { auth: true });
    const summary = json.summary;
    const r = {
      packId: json.packId ?? null,
      packName: json.packName ?? null,
      executionId: json.executionId ?? null,
      startTimeIso: json.startTime ?? null,
      endTimeIso: json.endTime ?? null,
      summaryTotal: null,
      summaryPassed: null,
      summaryFailed: null,
      summaryError: null,
      summarySuccessRate: null,
      results: [],
    };
    if (summary && typeof summary === 'object') {
      r.summaryTotal = summary.total != null ? Number(summary.total) : null;
      r.summaryPassed = summary.passed != null ? Number(summary.passed) : null;
      r.summaryFailed = summary.failed != null ? Number(summary.failed) : null;
      r.summaryError = summary.error != null ? Number(summary.error) : null;
      r.summarySuccessRate =
        summary.successRate != null ? Number(summary.successRate) : null;
    }
    const results = Array.isArray(json.results) ? json.results : [];
    for (const node of results) {
      r.results.push({
        testId: node.testId ?? null,
        testName: node.testName ?? null,
        status: node.status ?? null,
        duration: node.duration != null ? Number(node.duration) : 0,
        responseTime: node.responseTime != null ? Number(node.responseTime) : 0,
        error: node.error ?? null,
        startTimeIso: node.startTime ?? null,
        endTimeIso: node.endTime ?? null,
      });
    }
    r.resultsCount = r.results.length;
    return r;
  }

  async function listTestPacks() {
    const json = await sendJson('GET', '/api/v1/test-packs', { auth: true });
    const items = [];
    if (Array.isArray(json)) {
      for (const n of json) {
        items.push({
          packId: n.packId ?? null,
          name: n.name ?? null,
          projectName: n.projectName ?? null,
          environment: n.environment ?? null,
          lastStatus: n.lastStatus ?? null,
        });
      }
    }
    return items;
  }

  return {
    login,
    setBearerToken,
    triggerTestPack,
    getStatus,
    getResults,
    listTestPacks,
  };
}

module.exports = { createApiClient, stripTrailingSlash };
