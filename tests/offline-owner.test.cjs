const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('src/main/resources/static/js/20-data.js', 'utf8');
function runtime() {
  const storage = new Map(), records = new Map(), sent = [], events = [];
  let months = 0;
  const sandbox = {
    state: {y:2026,m:9,offline:{cacheReady:true,pending:0,failed:[]}},
    navigator:{onLine:true}, crypto:{randomUUID:()=>String(Math.random())},
    localStorage:{getItem:k=>storage.get(k)??null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)},
    sessionStorage:{getItem:()=>null,setItem:()=>{}},
    window:{location:{},addEventListener:()=>{},dispatchEvent:e=>events.push(e)},
    document:{body:{classList:{contains:()=>false}}}, CustomEvent:class {constructor(type,opts){this.type=type;this.detail=opts.detail;}},
    console, Date, uuid:()=>String(Math.random()), t:x=>x, updateOfflineStatus:()=>{}, sanitizeCalendarBundleForModules:x=>x,
    offlineOperationDisabledReason:()=>null, dayUpsertPayload:x=>x,
    acquireOfflineSyncLock:()=>({}),refreshOfflineSyncLock:()=>{},releaseOfflineSyncLock:()=>{},
    setSave:()=>{},applyCalendarBundle:()=>{},renderNotifications:()=>{},renderCalendar:()=>{},moduleEnabled:()=>false,
  };
  const context=vm.createContext(sandbox);
  const owners=source.slice(source.indexOf('const OFFLINE_DB_NAME'),source.indexOf('function isNetworkError'));
  const layer=source.slice(source.indexOf('const dataLayer ='),source.indexOf('function updateOfflineStatus'));
  vm.runInContext(owners+`\nconst offlineDb={db:null,open:async()=>{},get:async(store,key)=>records.get(offlineOwner+':'+store+':'+key),put:async(store,value)=>records.set(offlineOwner+':'+store+':'+(value.key||value.id),value),all:async(store)=>[...records.entries()].filter(([key])=>key.startsWith(offlineOwner+':'+store+':')).map(([,value])=>value),delete:async(store,key)=>records.delete(offlineOwner+':'+store+':'+key)};\n`+layer+`\nglobalThis.layer=dataLayer;globalThis.select=selectOfflineOwner;globalThis.assertOwner=assertOfflineOwner;globalThis.clear=clearOfflineOwner;`,Object.assign(context,{records}));
  sandbox.jfetch=async()=>({userId:storage.get('dutylog.offline.account.v2')});
  sandbox.api={month:async()=>{months++;return {days:[]}},upsertDay:async(date,day)=>sent.push({date,day,owner:storage.get('dutylog.offline.account.v2')})};
  const select=owner=>{sandbox.select(owner);sandbox.state.offline.cacheReady=true;};
  return {sandbox,context,storage,records,sent,events,select,layer:sandbox.layer,months:()=>months};
}
test('snapshots and queued edits stay with their account when users switch',async()=>{
  const r=runtime();r.select('Alice');
  await r.layer.writeSnapshot({days:[{note:'private'}]});
  await r.layer.enqueue('putDay',{date:'2026-10-07',day:{note:'private'}});
  r.select('Bob');assert.equal(await r.layer.readSnapshot(),null);assert.equal((await r.layer.getQueueItems()).length,0);
  await r.layer.syncQueue();assert.equal(r.sent.length,0);
  r.select('Alice');assert.equal((await r.layer.readSnapshot()).bundle.days[0].note,'private');
  await r.layer.syncQueue();assert.equal(r.sent.length,1);assert.equal(r.sent[0].owner,'Alice');assert.equal(r.months(),1);
});
test('empty reconnect queue performs no reads and publishes no invalidation',async()=>{
  const r=runtime();r.select('Alice');await r.layer.syncQueue();assert.equal(r.months(),0);assert.equal(r.events.length,0);
});
test('another tab changing account stops writes without assigning old queue to new owner',async()=>{
  const r=runtime();r.select('Alice');await r.layer.enqueue('putDay',{date:'2026-10-07',day:{}});
  r.storage.set('dutylog.offline.account.v2','Bob');assert.throws(()=>r.sandbox.assertOwner(),/Аккаунт/);
  r.select('Bob');assert.equal((await r.layer.getQueueItems()).length,0);
  r.select('Alice');assert.equal((await r.layer.getQueueItems()).length,1);
});
test('ownerless data is quarantined and cannot be replayed',async()=>{
  const r=runtime();r.select('Alice');r.records.set('Alice:queue:legacy',{id:'legacy',type:'putDay',payload:{},createdAt:'2026-10-01'});
  assert.equal((await r.layer.getQueueItems()).length,0);await r.layer.syncQueue();assert.equal(r.sent.length,0);
});

test('401 retains typed status and clears the offline fallback identity', async () => {
  const r=runtime();r.select('Alice');r.sandbox.fetch=async()=>({status:401});
  r.sandbox.csrfToken=()=>null;
  vm.runInContext(source.slice(source.indexOf('async function jfetch('),source.indexOf('function setSave(')),r.context);
  await assert.rejects(r.sandbox.jfetch('/api/auth/me'),error=>error.status===401);
  assert.equal(r.storage.get('dutylog.offline.account.v2'),undefined);
  assert.equal(r.sandbox.window.location.href,'/login.html');
});
test('the actual IndexedDB opener uses different database names for two owners',async()=>{
  const r=runtime(), names=[];
  const dbSource=source.slice(source.indexOf('const offlineDb ='),source.indexOf('const dataLayer ='));
  const storage = r.sandbox.localStorage;
  const context=vm.createContext({
    state:{offline:{}}, localStorage:storage, sessionStorage:r.sandbox.sessionStorage,
    crypto:r.sandbox.crypto, uuid:r.sandbox.uuid, t:x=>x,
    window:{indexedDB:{},addEventListener:()=>{}},
    indexedDB:{open:name=>{names.push(name);const request={result:{close:()=>{}}};queueMicrotask(()=>request.onsuccess());return request;}},
  });
  const owners=source.slice(source.indexOf('const OFFLINE_DB_NAME'),source.indexOf('function isNetworkError'));
  vm.runInContext(owners+dbSource+';globalThis.select=selectOfflineOwner;globalThis.db=offlineDb;',context);
  context.select('Alice');await context.db.open();context.select('Bob');await context.db.open();
  assert.deepEqual(names,['dutylog-offline:account:Alice','dutylog-offline:account:Bob']);
});
