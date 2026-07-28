/*
 * 35-today.js — DutyLog Next Today Dashboard
 *
 * Read-only composition layer over the existing calendar, shift occurrence,
 * overtime, task and important-date stores. Business mutations keep using the
 * established feature services and editors.
 */

Object.assign(I18N_EN, {
  "Сегодня":"Today",
  "Текущий день":"Current day",
  "Открыть календарь":"Open calendar",
  "Ближайшие дни":"Nearby days",
  "Новая задача":"New task",
  "на сегодня":"for today",
  "Новая заметка":"New note",
  "в выбранный день":"for the selected day",
  "Переработка":"Overtime",
  "начислить часы":"add hours",
  "Быстро добавить":"Quick add",
  "все действия":"all actions",
  "Смена":"Shift",
  "Смена не назначена":"No shift assigned",
  "нет смены":"no shift",
  "День свободен для планирования":"The day is free for planning",
  "Открыть день":"Open day",
  "Учёт времени":"Time accounting",
  "Переработки":"Overtime",
  "Журнал":"Ledger",
  "Доступно":"Available",
  "Начислено":"Earned",
  "Использовано":"Used",
  "План дня":"Day plan",
  "Задачи":"Tasks",
  "Показать все задачи →":"Show all tasks →",
  "Не пропустить":"Don't miss",
  "Ближайшие даты":"Upcoming dates",
  "Все даты":"All dates",
  "идёт":"in progress",
  "до начала":"until start",
  "завершена":"completed",
  "выходной":"day off",
  "следующая":"next",
  "До начала":"Starts in",
  "До конца":"Ends in",
  "Смена завершена":"Shift completed",
  "Рабочее время":"Working time",
  "Обед":"Break",
  "мин":"min",
  "Задач на сегодня нет":"No tasks for today",
  "Добавь задачу — она сразу появится здесь.":"Add a task and it will appear here immediately.",
  "Важных дат впереди не найдено":"No upcoming important dates",
  "Добавь день рождения, годовщину или другое событие.":"Add a birthday, anniversary or another event.",
  "Модуль выключен":"Module disabled",
  "Включить можно в настройках модулей.":"Enable it in module settings.",
  "сегодня":"today",
  "завтра":"tomorrow",
  "дн.":"days",
  "ч":"h",
  "Свободный день":"Free day"
});
Object.assign(I18N_RU, Object.fromEntries(Object.entries(I18N_EN).map(([ru,en]) => [en, ru])));

function todayDashboardDateParts(key){
  const [year, month, day] = String(key || "").split("-").map(Number);
  return { year, month, day };
}

function todayDashboardAddDays(key, amount){
  const { year, month, day } = todayDashboardDateParts(key);
  const value = new Date(Date.UTC(year, month - 1, day + amount));
  return `${value.getUTCFullYear()}-${pad(value.getUTCMonth() + 1)}-${pad(value.getUTCDate())}`;
}

function todayDashboardDaysBetween(from, to){
  const a = todayDashboardDateParts(from);
  const b = todayDashboardDateParts(to);
  const aMs = Date.UTC(a.year, a.month - 1, a.day);
  const bMs = Date.UTC(b.year, b.month - 1, b.day);
  return Math.round((bMs - aMs) / 86400000);
}

function todayDashboardDateLabel(key){
  const { year, month, day } = todayDashboardDateParts(key);
  const date = new Date(Date.UTC(year, month - 1, day));
  const weekday = weekdayName((date.getUTCDay() + 6) % 7);
  if (state.language === "en") return `${weekday}, ${monthNameGen(month - 1)} ${day}`;
  return `${weekday}, ${day} ${monthNameGen(month - 1)}`;
}

function todayDashboardTimeRange(occurrence){
  const start = String(occurrence?.displayStart || "");
  const end = String(occurrence?.displayEnd || "");
  const startDate = localDatePart(start);
  const endDate = localDatePart(end);
  const startTime = localTimePart(start);
  const endTime = localTimePart(end);
  if (!startTime || !endTime) return "—";
  if (startDate === endDate) return `${startTime}–${endTime}`;
  return `${startTime} · ${startDate} → ${endTime} · ${endDate}`;
}

function todayDashboardCountdown(ms){
  const totalMinutes = Math.max(0, Math.ceil(ms / 60000));
  const days = Math.floor(totalMinutes / 1440);
  const hours = Math.floor((totalMinutes % 1440) / 60);
  const minutes = totalMinutes % 60;
  if (days > 0) return `${days} ${t("дн.")} ${hours} ${t("ч")}`;
  if (hours > 0) return `${hours} ${t("ч")} ${String(minutes).padStart(2, "0")} ${t("мин")}`;
  return `${minutes} ${t("мин")}`;
}

function todayDashboardShiftType(occurrence){
  return state.shiftTypes.find(item => Number(item.id) === Number(occurrence?.shiftTypeId)) || null;
}

function todayDashboardShiftModel(key = todayKey(), now = Date.now()){
  const occurrences = (state.shiftOccurrences || [])
    .filter(item => Number.isFinite(Date.parse(item.startInstant)) && Number.isFinite(Date.parse(item.endInstant)))
    .sort((a,b) => Date.parse(a.startInstant) - Date.parse(b.startInstant));
  const active = occurrences.find(item => Date.parse(item.startInstant) <= now && now < Date.parse(item.endInstant));
  const todayOccurrences = [...new Map(shiftSegmentsOf(key).map(segment => [segment.occurrence.dayEntryId || segment.occurrence.startInstant, segment.occurrence])).values()];
  const next = occurrences.find(item => Date.parse(item.startInstant) > now) || null;
  const mostRelevant = active || todayOccurrences.find(item => Date.parse(item.endInstant) > now) || todayOccurrences[todayOccurrences.length - 1] || null;

  if (mostRelevant) {
    const shift = todayDashboardShiftType(mostRelevant);
    const startMs = Date.parse(mostRelevant.startInstant);
    const endMs = Date.parse(mostRelevant.endInstant);
    const duration = Math.max(1, endMs - startMs);
    const isActive = startMs <= now && now < endMs;
    const isFuture = now < startMs;
    const progress = isFuture ? 0 : Math.max(0, Math.min(100, Math.round((now - startMs) * 100 / duration)));
    return {
      kind:isActive ? "active" : (isFuture ? "future" : "finished"),
      occurrence:mostRelevant,
      title:shift ? shiftDisplayName(shift) : t("Смена"),
      status:t(isActive ? "идёт" : (isFuture ? "до начала" : "завершена")),
      time:todayDashboardTimeRange(mostRelevant),
      timezone:mostRelevant.displayTimezone || state.timeSettings?.displayTimezone || state.timeSettings?.workTimezone || browserTimeZone(),
      meta:`${t("Рабочее время")}: ${fmtHours(numOr0(mostRelevant.netMinutes) / 60)} ${t("ч")} · ${t("Обед")}: ${Number(mostRelevant.breakMinutes || 0)} ${t("мин")}`,
      countdown:isActive ? `${t("До конца")}: ${todayDashboardCountdown(endMs - now)}` : (isFuture ? `${t("До начала")}: ${todayDashboardCountdown(startMs - now)}` : t("Смена завершена")),
      progress,
      date:localDatePart(mostRelevant.displayStart) || key
    };
  }

  const floating = stOf(key);
  if (floating) {
    return {
      kind:"off",
      occurrence:null,
      title:shiftDisplayName(floating) || t("Свободный день"),
      status:t("выходной"),
      time:t("Свободный день"),
      timezone:state.timeSettings?.displayTimezone || state.timeSettings?.workTimezone || browserTimeZone(),
      meta:"",
      countdown:t("День свободен для планирования"),
      progress:0,
      date:key
    };
  }

  if (next && Date.parse(next.startInstant) - now <= 36 * 60 * 60 * 1000) {
    const shift = todayDashboardShiftType(next);
    return {
      kind:"next",
      occurrence:next,
      title:`${t("следующая")}: ${shift ? shiftDisplayName(shift) : t("Смена")}`,
      status:t("до начала"),
      time:todayDashboardTimeRange(next),
      timezone:next.displayTimezone || state.timeSettings?.displayTimezone || browserTimeZone(),
      meta:`${t("Рабочее время")}: ${fmtHours(numOr0(next.netMinutes) / 60)} ${t("ч")} · ${t("Обед")}: ${Number(next.breakMinutes || 0)} ${t("мин")}`,
      countdown:`${t("До начала")}: ${todayDashboardCountdown(Date.parse(next.startInstant) - now)}`,
      progress:0,
      date:localDatePart(next.displayStart) || key
    };
  }

  return {
    kind:"empty",
    occurrence:null,
    title:t("Смена не назначена"),
    status:t("нет смены"),
    time:"—",
    timezone:state.timeSettings?.displayTimezone || state.timeSettings?.workTimezone || browserTimeZone(),
    meta:"",
    countdown:t("День свободен для планирования"),
    progress:0,
    date:key
  };
}

function renderTodayDateStrip(key){
  const box = $("todayDateStrip");
  if (!box) return;
  box.innerHTML = "";
  for (let offset = -2; offset <= 4; offset++) {
    const dateKey = todayDashboardAddDays(key, offset);
    const { year, month, day } = todayDashboardDateParts(dateKey);
    const date = new Date(Date.UTC(year, month - 1, day));
    const button = document.createElement("button");
    button.type = "button";
    button.className = "todayDateChip" + (offset === 0 ? " isToday" : "");
    button.dataset.date = dateKey;
    button.setAttribute("aria-label", todayDashboardDateLabel(dateKey));
    button.innerHTML = `<small>${esc(weekdayName((date.getUTCDay() + 6) % 7))}</small><b>${day}</b><span>${esc(monthNameGen(month - 1))}</span>`;
    button.addEventListener("click", () => openTodayCalendarDate(dateKey));
    box.appendChild(button);
  }
}

function renderTodayShift(key){
  const model = todayDashboardShiftModel(key);
  const card = $("todayShiftCard");
  if (!card) return;
  card.dataset.shiftState = model.kind;
  $("todayShiftTitle").textContent = model.title;
  $("todayShiftStatus").textContent = model.status;
  $("todayShiftTime").textContent = model.time;
  $("todayShiftMeta").textContent = [model.timezone, model.meta].filter(Boolean).join(" · ");
  $("todayShiftCountdown").textContent = model.countdown;
  $("todayShiftProgress").style.width = `${model.progress}%`;
  $("todayShiftProgressWrap").setAttribute("aria-valuenow", String(model.progress));
  $("todayShiftProgressWrap").hidden = ["empty", "off"].includes(model.kind);
  $("todayOpenShiftDay").dataset.date = model.date || key;
}

function renderTodayOvertime(){
  const enabled = moduleEnabled("overtime");
  const account = enabled ? (state.overtimeAccount || {}) : {};
  const earned = numOr0(account.totalEarnedHours);
  const used = numOr0(account.totalUsedHours);
  const balance = numOr0(account.balanceHours);
  const percent = earned > 0 ? Math.max(0, Math.min(100, Math.round(balance * 100 / earned))) : 0;
  $("todayOvertimeBalance").textContent = enabled ? `${balance > 0 ? "+" : ""}${fmtHours(balance)} ${t("ч")}` : "—";
  $("todayOvertimeBalance").classList.toggle("negative", balance < 0);
  $("todayOvertimeEarned").textContent = enabled ? `${fmtHours(earned)} ${t("ч")}` : "—";
  $("todayOvertimeUsed").textContent = enabled ? `${fmtHours(used)} ${t("ч")}` : "—";
  $("todayOvertimeProgress").style.width = `${percent}%`;
  $("todayOpenOvertime").disabled = !enabled;
}

function todayTaskMeta(task){
  const parts = [];
  if (task.dueTime) parts.push(task.dueTime);
  if (task.category) parts.push(task.category);
  if (task.priority === "URGENT") parts.push(t("срочные"));
  else if (task.priority === "HIGH") parts.push(t("важные"));
  return parts.join(" · ");
}

function renderTodayTasks(key){
  const box = $("todayTaskList");
  if (!box) return;
  const enabled = moduleEnabled("tasks");
  const all = enabled ? sortedTasksOpenFirst(activeTasksOf(key)) : [];
  $("todayTaskCount").textContent = String(all.length);
  $("todayOpenTasks").disabled = !enabled;
  box.innerHTML = "";
  if (!enabled) {
    box.innerHTML = `<div class="todayEmpty"><b>${esc(t("Модуль выключен"))}</b><span>${esc(t("Включить можно в настройках модулей."))}</span></div>`;
    return;
  }
  if (!all.length) {
    box.innerHTML = `<div class="todayEmpty"><b>${esc(t("Задач на сегодня нет"))}</b><span>${esc(t("Добавь задачу — она сразу появится здесь."))}</span></div>`;
    return;
  }
  for (const task of all.slice(0, 4)) {
    const row = document.createElement("div");
    row.className = `todayTaskRow priority-${String(task.priority || "normal").toLowerCase()}`;
    row.innerHTML = `
      <button class="todayTaskCheck" type="button" aria-label="${esc(t("Выполнить"))}" data-complete-task="${task.id}"></button>
      <button class="todayTaskMain" type="button" data-open-task="${task.id}"><b>${esc(task.text || "")}</b><small>${esc(todayTaskMeta(task) || t("Открытая задача"))}</small></button>
      <span class="todayTaskChevron" aria-hidden="true">›</span>`;
    box.appendChild(row);
  }
  box.querySelectorAll("[data-open-task]").forEach(button => button.addEventListener("click", () => openTaskDetails(Number(button.dataset.openTask))));
  box.querySelectorAll("[data-complete-task]").forEach(button => button.addEventListener("click", async () => {
    button.disabled = true;
    try { await toggleTask(Number(button.dataset.completeTask), true); }
    finally { renderTodayDashboard(); }
  }));
}

function importantCountdownLabel(date, today){
  const diff = todayDashboardDaysBetween(today, date);
  if (diff === 0) return t("сегодня");
  if (diff === 1) return t("завтра");
  return `${diff} ${t("дн.")}`;
}

function renderTodayUpcoming(key){
  const box = $("todayUpcomingList");
  if (!box) return;
  const enabled = moduleEnabled("important_dates");
  $("todayOpenImportant").disabled = !enabled;
  box.innerHTML = "";
  if (!enabled) {
    box.innerHTML = `<div class="todayEmpty"><b>${esc(t("Модуль выключен"))}</b><span>${esc(t("Включить можно в настройках модулей."))}</span></div>`;
    return;
  }
  const items = (state.importantDays || [])
    .map(item => ({ ...item, nextOccurrence:typeof importantNextOccurrence === "function" ? importantNextOccurrence(item, key) : item.date }))
    .filter(item => item.nextOccurrence && item.nextOccurrence >= key)
    .sort((a,b) => String(a.nextOccurrence).localeCompare(String(b.nextOccurrence)) || String(a.title || "").localeCompare(String(b.title || ""), currentLocale()))
    .slice(0, 4);
  if (!items.length) {
    box.innerHTML = `<div class="todayEmpty"><b>${esc(t("Важных дат впереди не найдено"))}</b><span>${esc(t("Добавь день рождения, годовщину или другое событие."))}</span></div>`;
    return;
  }
  for (const item of items) {
    const row = document.createElement("button");
    row.type = "button";
    row.className = "todayUpcomingRow";
    row.innerHTML = `<span class="todayUpcomingDot" style="--event-color:${esc(item.color || "#F5B841")}"></span><span><b>${esc(item.title || "")}</b><small>${esc(formatDateHuman(item.nextOccurrence))} · ${esc(repeatLabel(item.repeatMode))}</small></span><strong>${esc(importantCountdownLabel(item.nextOccurrence, key))}</strong>`;
    row.addEventListener("click", () => openTodayCalendarDate(item.nextOccurrence));
    box.appendChild(row);
  }
}

function renderTodayDashboard(){
  if (!$("view-today")) return;
  const key = todayKey();
  const { year } = todayDashboardDateParts(key);
  $("todayDateTitle").textContent = todayDashboardDateLabel(key);
  $("todayDateSubtitle").textContent = `${year} · ${state.timeSettings?.displayTimezone || state.timeSettings?.workTimezone || browserTimeZone()}`;
  renderTodayDateStrip(key);
  renderTodayShift(key);
  renderTodayOvertime();
  renderTodayTasks(key);
  renderTodayUpcoming(key);

  $("todayQuickTask").hidden = !moduleEnabled("tasks");
  $("todayQuickNote").hidden = !moduleEnabled("notes");
  $("todayQuickCredit").hidden = !moduleEnabled("overtime");

  if (document.body.dataset.view === "today") {
    $("monthName").textContent = t("Сегодня");
    $("yearName").textContent = state.language === "en" ? todayDashboardDateLabel(key) : todayDashboardDateLabel(key).replace(/^.*?,\s*/, "");
  }
}

async function openTodayCalendarDate(date, mode = "day"){
  if (!date) return;
  if (typeof calendarExperienceOpen === "function" && mode !== "panel") {
    await calendarExperienceOpen(date, mode);
    return;
  }
  const { year, month } = todayDashboardDateParts(date);
  await goto(year, month - 1);
  location.hash = "#calendar";
  selectDay(date);
}

$("todayOpenCalendar")?.addEventListener("click", () => openTodayCalendarDate(todayKey(), "day"));
$("todayOpenShiftDay")?.addEventListener("click", event => openTodayCalendarDate(event.currentTarget.dataset.date || todayKey(), "day"));
$("todayOpenOvertime")?.addEventListener("click", () => { location.hash = "#overtime"; });
$("todayOpenTasks")?.addEventListener("click", () => { location.hash = "#tasks"; });
$("todayOpenImportant")?.addEventListener("click", () => { location.hash = "#important"; });
$("todayQuickTask")?.addEventListener("click", () => openTaskCreate({ date:todayKey() }));
$("todayQuickCredit")?.addEventListener("click", () => openOvertimeCreditModal(todayKey()));
$("todayQuickMore")?.addEventListener("click", () => openQuickActions());
$("todayQuickNote")?.addEventListener("click", async () => {
  const date = todayKey();
  await openTodayCalendarDate(date, "panel");
  $("accNote").open = true;
  await createDayNote("");
});

setInterval(() => {
  if (document.body.dataset.view === "today" && !document.hidden) renderTodayDashboard();
}, 30000);
