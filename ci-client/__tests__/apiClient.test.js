'use strict';

const { createApiClient } = require('../src/apiClient');

function mockFetch(responses) {
  let i = 0;
  return async (url, init) => {
    const entry = responses[i++];
    if (!entry) throw new Error(`Unexpected fetch call ${i} ${url}`);
    if (entry.expectMethod && init.method !== entry.expectMethod) {
      throw new Error(`Expected ${entry.expectMethod} got ${init.method}`);
    }
    return {
      ok: entry.status >= 200 && entry.status < 300,
      status: entry.status,
      text: async () => entry.body,
      headers: new Map(),
    };
  };
}

describe('createApiClient', () => {
  test('login returns token', async () => {
    const fetchFn = mockFetch([
      {
        expectMethod: 'POST',
        status: 200,
        body: JSON.stringify({ token: 'abc', user: { email: 'a@b.c' } }),
      },
    ]);
    const client = createApiClient({
      baseUrl: 'https://example.com',
      tenantId: null,
      log: () => {},
      fetchFn,
    });
    const token = await client.login('a@b.c', 'secret');
    expect(token).toBe('abc');
  });

  test('login throws when token missing', async () => {
    const fetchFn = mockFetch([{ expectMethod: 'POST', status: 200, body: '{}' }]);
    const client = createApiClient({
      baseUrl: 'https://example.com',
      fetchFn,
    });
    await expect(client.login('a', 'b')).rejects.toThrow(/token was missing/);
  });

  test('HTTP error includes status', async () => {
    const fetchFn = mockFetch([
      { expectMethod: 'POST', status: 401, body: JSON.stringify({ error: 'Unauthorized' }) },
    ]);
    const client = createApiClient({
      baseUrl: 'https://example.com',
      fetchFn,
    });
    await expect(client.login('a', 'b')).rejects.toThrow(/HTTP 401/);
  });

  test('full flow with mock fetch sequence', async () => {
    const bodies = [
      JSON.stringify({ token: 'TOK' }),
      JSON.stringify({
        message: 'started',
        executionId: 'e1',
        packId: 'pack_x',
        status: 'RUNNING',
        startTime: 's',
      }),
      JSON.stringify({
        packId: 'pack_x',
        status: 'COMPLETED',
        executionId: 'e1',
        summary: { total: 2, passed: 2, failed: 0, error: 0, successRate: 100 },
      }),
      JSON.stringify({
        packId: 'pack_x',
        packName: 'PN',
        executionId: 'e1',
        results: [
          {
            testId: 't1',
            testName: 'T1',
            status: 'PASSED',
            duration: 100,
            responseTime: 50,
          },
        ],
        summary: { total: 2, passed: 2, failed: 0, error: 0, successRate: 100 },
      }),
    ];
    let idx = 0;
    const fetchFn = async (url, init) => {
      const body = bodies[idx++];
      return { ok: true, status: 200, text: async () => body, headers: new Map() };
    };
    const client = createApiClient({ baseUrl: 'https://api.test', tenantId: null, fetchFn });
    const tok = await client.login('u', 'p');
    client.setBearerToken(tok);
    const tr = await client.triggerTestPack('pack_x');
    expect(tr.executionId).toBe('e1');
    const st = await client.getStatus('pack_x');
    expect(st.status).toBe('COMPLETED');
    const res = await client.getResults('pack_x', 'e1');
    expect(res.resultsCount).toBe(1);
    expect(res.results[0].testName).toBe('T1');
  });
});
