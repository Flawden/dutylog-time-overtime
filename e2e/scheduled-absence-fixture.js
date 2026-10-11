// Explicit schedule for tests whose subject is FIFO, approval or domain ownership.
async function seedScheduledAbsence(page, dates) {
  await page.evaluate(async dates => {
    const shift = await jfetch('/api/shift-types', { method:'POST', body:{
      name:'Absence fixture 09-17', hours:8, plannedHours:8,
      startTime:'09:00', endTime:'17:00', breakMinutes:0, color:'#123456'
    } });
    for (const date of dates) await jfetch(`/api/days/${date}`, {
      method:'PUT', body:{ shiftTypeId:shift.id }
    });
  }, dates);
}
module.exports = { seedScheduledAbsence };
