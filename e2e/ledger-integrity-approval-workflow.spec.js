const { test, expect } = require('./fixtures');
const { registerAndOnboard, openView } = require('./helpers');

test('approval workflow reserves posts reverses and locks a closed accounting period', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 960 });
  await registerAndOnboard(page, { preset:'full', prefix:'ledgerapproval' });

  const result = await page.evaluate(async () => {
    const token = decodeURIComponent((document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/) || [])[1] || '');
    const headers = { 'Content-Type':'application/json', 'X-XSRF-TOKEN':token };
    const call = async (url, options = {}, expectedStatus = null) => {
      const expectedHeader = expectedStatus == null ? {} : { 'X-DutyLog-E2E-Expected-Status':String(expectedStatus) };
      const response = await fetch(url, { ...options, headers:{ ...headers, ...expectedHeader, ...(options.headers || {}) } });
      const text = await response.text();
      let body = null;
      try { body = text ? JSON.parse(text) : null; } catch { body = text; }
      return { status:response.status, body };
    };
    const now = new Date();
    const pad = value => String(value).padStart(2, '0');
    const month = `${now.getFullYear()}-${pad(now.getMonth() + 1)}`;
    const day = `${month}-10`;
    const actualDay = `${month}-11`;

    const planner = await call(`/api/vacation-planner?referenceDate=${day}`);
    const timeOff = planner.body.types.find(type => type.systemCode === 'TIME_OFF');
    await call('/api/overtime/credits', { method:'POST', body:JSON.stringify({ date:`${month}-01`, hours:8, reason:'Approval workflow source' }) });

    const created = await call('/api/vacation-planner/absences', { method:'POST', body:JSON.stringify({
      typeId:timeOff.id, title:'Workflow отгул', startDate:day, endDate:day, status:'DRAFT',
      coverage:'PARTIAL', startTime:'09:00', endTime:'11:00', compensationPolicy:'OVERTIME_BANK'
    }) });
    const id = created.body.id;
    const submitted = await call(`/api/vacation-planner/absences/${id}`, { method:'PATCH', body:JSON.stringify({ status:'SUBMITTED' }) });
    const accountReserved = await call('/api/overtime/account');
    const approved = await call(`/api/vacation-planner/absences/${id}`, { method:'PATCH', body:JSON.stringify({ status:'APPROVED' }) });
    const accountPosted = await call('/api/overtime/account');
    const cancelled = await call(`/api/vacation-planner/absences/${id}`, { method:'PATCH', body:JSON.stringify({ status:'CANCELLED' }) });
    const accountReleased = await call('/api/overtime/account');
    const integrity = await call(`/api/ledger-integrity?from=${month}-01&to=${month}-28`);

    const closed = await call(`/api/ledger-integrity/periods/${month}/close`, { method:'POST' });
    const blocked = await call('/api/actual-work', { method:'POST', body:JSON.stringify({ workDate:actualDay, startTime:'08:00', endTime:'12:00', note:'Blocked while closed' }) }, 409);
    await call(`/api/ledger-integrity/periods/${month}/reopen`, { method:'POST' });
    const actual = await call('/api/actual-work', { method:'POST', body:JSON.stringify({ workDate:actualDay, startTime:'08:00', endTime:'12:00', note:'Фактическая работа' }) });
    const summary = await call(`/api/time-compensation?from=${month}-01&to=${month}-28`);

    return {
      month, actualDay,
      draftLinked:created.body.linkedOvertimeUsageId,
      submittedState:accountReserved.body.usages.find(item => item.id === submitted.body.linkedOvertimeUsageId)?.postingState,
      postedState:accountPosted.body.usages.find(item => item.id === approved.body.linkedOvertimeUsageId)?.postingState,
      cancelledLinked:cancelled.body.linkedOvertimeUsageId,
      releasedUsages:accountReleased.body.usages.length,
      integrityHealthy:integrity.body.healthy,
      entryKinds:integrity.body.entries.map(item => item.entryKind),
      closedStatus:closed.body.status,
      blockedStatus:blocked.status,
      blockedCode:blocked.body?.code,
      actualStatus:actual.status,
      actualMinutes:actual.body?.workedMinutes,
      actualSource:summary.body.days.find(item => item.date === actualDay)?.actualSource
    };
  });

  expect(result.draftLinked).toBeNull();
  expect(result.submittedState).toBe('RESERVED');
  expect(result.postedState).toBe('POSTED');
  expect(result.cancelledLinked).toBeNull();
  expect(result.releasedUsages).toBe(0);
  expect(result.integrityHealthy).toBe(true);
  expect(result.entryKinds).toContain('ABSENCE_RESERVATION');
  expect(result.entryKinds).toContain('ABSENCE_POSTING');
  expect(result.closedStatus).toBe('CLOSED');
  expect(result.blockedStatus).toBe(409);
  expect(result.blockedCode).toBe('PERIOD_CLOSED');
  expect(result.actualStatus).toBe(201);
  expect(result.actualMinutes).toBe(240);
  expect(result.actualSource).toBe('EXPLICIT');

  await openView(page, 'overtime');
  await expect(page.locator('#ledgerIntegrityCard')).toBeVisible();
  await expect(page.locator('#ledgerIntegrityStatus')).toContainText(/согласован|healthy/i);
  await expect(page.locator('#actualWorkList')).toContainText('Фактическая работа');
});
