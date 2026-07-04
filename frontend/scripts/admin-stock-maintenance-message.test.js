const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');

const screenPath = path.join(__dirname, '..', 'screens', 'admin', 'admin-stock-maintenance-screen.tsx');
const source = fs.readFileSync(screenPath, 'utf8');
const start = source.indexOf('function formatBackfillMessage');
const end = source.indexOf('function ResultMessages');

assert.notEqual(start, -1, 'formatBackfillMessage was not found');
assert.notEqual(end, -1, 'ResultMessages was not found');

const formatterSource = source.slice(start, end);
const { outputText } = ts.transpileModule(formatterSource, {
  compilerOptions: {
    module: ts.ModuleKind.CommonJS,
    target: ts.ScriptTarget.ES2020,
  },
});

const sandbox = { module: { exports: {} } };
vm.runInNewContext(`${outputText}\nmodule.exports = { formatBackfillMessage };`, sandbox, {
  filename: screenPath,
});

const { formatBackfillMessage } = sandbox.module.exports;
const result = { deletedRows: 0, savedRows: 5, skippedRows: 1 };
const noRowsResult = { deletedRows: 0, savedRows: 0, skippedRows: 0 };

assert.equal(
  formatBackfillMessage('Daily range backfill completed', result),
  'Daily range backfill completed',
);
assert.equal(
  formatBackfillMessage('Intraday backfill completed in max 2-symbol batches', noRowsResult),
  'Twelve Data free-tier only allows data requests for up to 8 stocks per minute. Wait one minute, then try again.',
);
