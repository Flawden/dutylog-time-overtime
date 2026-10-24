const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(require('node:path').join(__dirname, '../src/main/resources/static/js/10-core.js'), 'utf8');
function runtime(refresh) {
  const handlers = {};
  const errors = [];
  const context = vm.createContext({
    state: { ui: {} }, document: { documentElement: { dataset: { vueCalendarTimeline: 'ready' } } },
    window: { addEventListener: (name, fn) => handlers[name] = fn, DutyLogVueDomains: { calendarTimeline: { refresh } } },
    console: { error: (...args) => errors.push(args) },
  });
  const start = source.indexOf('const CALENDAR_TIMELINE_PROJECTION_EVENT');
  const end = source.indexOf('\nfunction cloneForVue', start);
  vm.runInContext(source.slice(start, end), context);
  return { context, errors, request: () => vm.runInContext('requestVueCalendarTimelineRefresh()', context),
    project: () => handlers['dutylog:calendar-timeline-projection']({ detail: {} }) };
}
const tick = () => new Promise(resolve => setImmediate(resolve));
test('coalesces synchronous render requests into one read', async () => {
  let calls = 0; const r = runtime(async () => { calls++; });
  r.request(); r.request(); r.request(); await tick(); assert.equal(calls, 1);
});
test('finishes a read before one trailing refresh after intervening changes', async () => {
  let release; let calls = 0;
  const r = runtime(() => { calls++; return calls === 1 ? new Promise(resolve => release = resolve) : Promise.resolve(); });
  r.request(); await tick(); r.request(); r.request(); await tick(); assert.equal(calls, 1);
  release(); await tick(); assert.equal(calls, 2);
});
test('loading indicator renders do not start data reads', async () => {
  let calls = 0; const r = runtime(async () => { calls++; });
  r.context.state.ui.loadingCalendar = true; assert.equal(r.request(), true);
  await tick(); assert.equal(calls, 0);
  r.context.state.ui.loadingCalendar = false; r.request(); await tick(); assert.equal(calls, 1);
});
test('publishing a projection cannot feed back into another read', async () => {
  let calls = 0; const r = runtime(async () => { calls++; });
  vm.runInContext('synchronizeLegacyCalendarTimelineProjection = () => requestVueCalendarTimelineRefresh()', r.context);
  r.project(); await tick(); assert.equal(calls, 0);
  r.request(); await tick(); assert.equal(calls, 1);
});
test('a failed read does not wedge subsequent refreshes', async () => {
  let calls = 0; const r = runtime(async () => { if (++calls === 1) throw Error('network'); });
  r.request(); await tick(); assert.equal(r.errors.length, 1);
  r.request(); await tick(); assert.equal(calls, 2);
});
test('bootstrap renders do not invalidate an already mounting Vue calendar', async () => {
  let calls = 0; const r = runtime(async () => { calls++; });
  r.context.state.ui.booting = true;
  r.request(); await tick(); assert.equal(calls, 0);
  r.context.state.ui.booting = false;
  r.request(); await tick(); assert.equal(calls, 1);
});
function initialCalendarRuntime(domain) {
  const boot = fs.readFileSync(require('node:path').join(__dirname, '../src/main/resources/static/js/70-user-boot.js'), 'utf8');
  let legacyReads = 0;
  const context = vm.createContext({ window: { DutyLogVueDomains: { calendarTimeline: domain } }, loadMonth: async () => { legacyReads++; } });
  vm.runInContext(boot.slice(boot.indexOf('async function loadInitialCalendar(){'), boot.indexOf('async function init(){')), context);
  return { run: () => vm.runInContext('loadInitialCalendar()', context), legacyReads: () => legacyReads };
}
test('native startup waits for the canonical read without fetching the legacy month', async () => {
  let release; let calls = 0;
  const domain = { refresh: () => { calls++; return new Promise(resolve => release = resolve); } };
  const r = initialCalendarRuntime(domain); let finished = false;
  const pending = r.run().then(value => { finished = true; assert.equal(value, domain); });
  await tick(); assert.equal(finished, false); assert.equal(calls, 1); assert.equal(r.legacyReads(), 0);
  release(); await pending;
});
test('legacy startup still loads the month when Vue is absent', async () => {
  const r = initialCalendarRuntime(undefined); await r.run(); assert.equal(r.legacyReads(), 1);
});
test('native startup failure is not hidden by an unrelated legacy read', async () => {
  const r = initialCalendarRuntime({ refresh: async () => { throw Error('unavailable'); } });
  await assert.rejects(r.run(), /unavailable/); assert.equal(r.legacyReads(), 0);
});
