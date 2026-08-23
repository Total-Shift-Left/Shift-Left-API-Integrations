# Total Shift Left API Tests — GitHub Action

Run a [Shift-Left Studio](https://totalshiftleft.ai) test pack from a workflow, gate the build on
its pass rate, and publish JSON + JUnit results.

All behaviour lives in [`@totalshiftleft/ci`](../ci-client/README.md); this package is the
GitHub host adapter plus the bundled `dist/`.

> **Status: not published yet.** This action cannot be referenced as
> `Total-Shift-Left/...@v1` until the bundle is mirrored to a GitHub repository and tagged — this
> repo lives on Azure DevOps, and a GitHub Action must be served from GitHub. Until then, use the
> REST fallback documented in the [Shift-Left Studio API docs](https://totalshiftleft.ai/integrations/github-actions).

## Usage

```yaml
- uses: Total-Shift-Left/Shift-Left-API-Integrations/github-actions@v1
  id: api-tests
  with:
    server-url: ${{ secrets.SHIFTLEFT_URL }}
    api-email: ${{ secrets.SHIFTLEFT_EMAIL }}
    api-password: ${{ secrets.SHIFTLEFT_PASSWORD }}
    pack-id: ${{ vars.SHIFTLEFT_TEST_PACK_ID }}
    pass-threshold-percent: '95'

- name: Publish results
  if: always()
  uses: actions/upload-artifact@v4
  with:
    name: shiftleft-results
    path: shiftleft-test-pack-results.xml
```

Run several packs in parallel with a matrix:

```yaml
strategy:
  matrix:
    pack: [pack_checkout, pack_accounts]
steps:
  - uses: Total-Shift-Left/Shift-Left-API-Integrations/github-actions@v1
    with:
      pack-id: ${{ matrix.pack }}
      # ...
```

## Inputs and outputs

See [`action.yml`](action.yml) — it is the source of truth, and
`__tests__/actionContract.test.js` fails the build if it drifts from what the runner actually
reads. Input semantics and the gate decision codes are documented once in
[`@totalshiftleft/ci`](../ci-client/README.md).

Outputs: `execution_id`, `trigger_execution_id`, `decision`, `success_rate`, `passed`,
`task_completion`, `json_summary_path`, `test_results_xml_path`.

## Developing

```bash
npm test --workspace=github-actions      # contract tests
npm run build --workspace=github-actions # rebuild dist/ with ncc
```

`dist/` is committed on purpose — GitHub runs it directly with no install step. Rebuild and commit
it in the same change whenever `src/` or `@totalshiftleft/ci` changes; `npm run check:dist` fails
if you forget.
