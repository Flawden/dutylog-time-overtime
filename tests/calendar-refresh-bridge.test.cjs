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
