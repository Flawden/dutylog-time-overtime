/*
 * 37-calendar-experience.js — v27.17.1 Calendar & Notes Quality Hotfix
 *
 * Adds Month / Week / Day scales on top of the existing authoritative
 * calendar model. Business logic and the legacy selected-day editor stay in
 * 30-calendar.js; this layer only composes navigation and projections.
 */

"use strict";

const CALENDAR_MODE_KEY = "dutylog.calendar.mode.v1";
const CALENDAR_FOCUS_KEY = "dutylog.calendar.focus.v1";
const CALENDAR_MODES = new Set(["month", "week", "day"]);

function calendarExperienceStored(key, fallback){
  try { return localStorage.getItem(key) || fallback; }
  catch (_) { return fallback; }
}
function calendarExperienceValidDateKey(value){
  return /^\d{4}-\d{2}-\d{2}$/.test(String(value || ""));
}
function calendarExperienceInitialMode(){
  const value = calendarExperienceStored(CALENDAR_MODE_KEY, "month");
  return CALENDAR_MODES.has(value) ? value : "month";
}
function calendarExperienceInitialFocus(){
  const value = calendarExperienceStored(CALENDAR_FOCUS_KEY, todayKey());
  return calendarExperienceValidDateKey(value) ? value : todayKey();
}

state.calendarExperience = {
  mode: calendarExperienceInitialMode(),
  focusDate: calendarExperienceInitialFocus(),
};

function calendarExperiencePersist(){
  try {
    localStorage.setItem(CALENDAR_MODE_KEY, state.calendarExperience.mode);
    localStorage.setItem(CALENDAR_FOCUS_KEY, state.calendarExperience.focusDate);
  } catch (_) { /* private mode / full storage: keep the in-memory state */ }
}
function calendarExperienceParts(key){
  const [year, month, day] = String(key || "").split("-").map(Number);
  return { year, month, day };
}
function calendarExperienceOffset(key, days){
  const { year, month, day } = calendarExperienceParts(key);
  const value = new Date(Date.UTC(year, month - 1, day + Number(days || 0)));
  return `${value.getUTCFullYear()}-${String(value.getUTCMonth() + 1).padStart(2, "0")}-${String(value.getUTCDate()).padStart(2, "0")}`;
}
function calendarExperienceWeekStart(key){
  const { year, month, day } = calendarExperienceParts(key);
  const value = new Date(Date.UTC(year, month - 1, day));
  const mondayOffset = (value.getUTCDay() + 6) % 7;
  return calendarExperienceOffset(key, -mondayOffset);
}
function calendarExperienceDate(key){
  const { year, month, day } = calendarExperienceParts(key);
  return new Date(Date.UTC(year, month - 1, day, 12));
}
function calendarExperienceLongLabel(key){
  const date = calendarExperienceDate(key);
  return new Intl.DateTimeFormat(currentLocale(), { weekday:"long", day:"numeric", month:"long", year:"numeric", timeZone:"UTC" }).format(date);
}
function calendarExperienceShortLabel(key){
  const date = calendarExperienceDate(key);
  return new Intl.DateTimeFormat(currentLocale(), { day:"numeric", month:"long", timeZone:"UTC" }).format(date);
}
function calendarExperienceWeekRangeLabel(start){
  const end = calendarExperienceOffset(start, 6);
  const a = calendarExperienceDate(start);
  const b = calendarExperienceDate(end);
  const sameMonth = a.getUTCMonth() === b.getUTCMonth();
  const left = new Intl.DateTimeFormat(currentLocale(), sameMonth ? { day:"numeric", timeZone:"UTC" } : { day:"numeric", month:"short", timeZone:"UTC" }).format(a);
  const right = new Intl.DateTimeFormat(currentLocale(), { day:"numeric", month:"short", year:"numeric", timeZone:"UTC" }).format(b);
  return `${left} — ${right}`;
}
function calendarExperienceFocusDate(){
  if (calendarExperienceValidDateKey(state.selected)) return state.selected;
  if (calendarExperienceValidDateKey(state.calendarExperience?.focusDate)) return state.calendarExperience.focusDate;
  return todayKey();
}
function calendarExperienceSetFocusMemory(key){
  if (!calendarExperienceValidDateKey(key)) return;
  state.calendarExperience.focusDate = key;
  calendarExperiencePersist();
}
function calendarExperienceLoadedMonthMatches(key){
  const { year, month } = calendarExperienceParts(key);
  return year === state.y && month - 1 === state.m;
}
async function calendarExperienceEnsureMonth(key){
  if (calendarExperienceLoadedMonthMatches(key)) return;
  const { year, month } = calendarExperienceParts(key);
  await goto(year, month - 1);
}
function calendarExperienceCloseLegacyPanel(){
  state.selected = null;
  $("layout")?.classList.remove("with-panel");
  document.body.classList.remove("panel-open");
  if ($("panel")) $("panel").hidden = true;
}
async function calendarExperienceOpen(key, mode = "day"){
  if (!calendarExperienceValidDateKey(key)) return;
  await calendarExperienceEnsureMonth(key);
  calendarExperienceSetFocusMemory(key);
  state.calendarExperience.mode = CALENDAR_MODES.has(mode) ? mode : "day";
  calendarExperiencePersist();
  calendarExperienceCloseLegacyPanel();
  if (location.hash !== "#calendar") location.hash = "#calendar";
  renderCalendar();
  requestAnimationFrame(() => {
    const target = state.calendarExperience.mode === "day" ? $("calendarDayExperience") : $("calendarWeekExperience");
    target?.scrollIntoView({ block:"start", behavior:"smooth" });
  });
}
async function calendarExperienceOpenLegacyDetails(key = calendarExperienceFocusDate()){
  await calendarExperienceEnsureMonth(key);
  state.calendarExperience.mode = "month";
  calendarExperienceSetFocusMemory(key);
  calendarExperiencePersist();
  renderCalendar();
  selectDay(key);
}
async function calendarExperienceSetMode(mode){
  if (!CALENDAR_MODES.has(mode)) return;
  const focus = calendarExperienceFocusDate();
  await calendarExperienceEnsureMonth(focus);
  state.calendarExperience.mode = mode;
  calendarExperienceSetFocusMemory(focus);
  if (mode !== "month") calendarExperienceCloseLegacyPanel();
  renderCalendar();
}

function calendarExperienceTimeMinutes(value){
  const match = String(value || "").match(/^(\d{1,2}):(\d{2})$/);
  if (!match) return null;
  const hour = Number(match[1]);
  const minute = Number(match[2]);
  if (hour === 24 && minute === 0) return 1440;
  if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return null;
  return hour * 60 + minute;
}
function calendarExperienceReminderDate(reminder){
  const value = String(reminder?.displayAt || reminder?.remindAt || "");
  const match = value.match(/^(\d{4}-\d{2}-\d{2})T/);
  return match ? match[1] : "";
}
function calendarExperienceReminderTime(reminder){
  const value = String(reminder?.displayAt || reminder?.remindAt || "");
  const match = value.match(/T(\d{2}:\d{2})/);
  return match ? match[1] : "";
}
function calendarExperienceRemindersForDate(key){
  return (state.reminders || []).filter(reminder => calendarExperienceReminderDate(reminder) === key);
}
function calendarExperienceTaskLabel(task){
  const category = String(task?.category || "").trim();
  const priority = task?.priority === "URGENT" ? t("срочные") : task?.priority === "HIGH" ? t("важные") : "";
  return [category, priority].filter(Boolean).join(" · ");
}
function calendarExperienceDayFacts(key){
  const shift = stOf(key);
  const segments = shiftSegmentsOf(key);
  const tasks = moduleEnabled("tasks") ? activeTasksOf(key) : [];
  const important = moduleEnabled("important_dates") ? importantOf(key) : [];
  const reminders = moduleEnabled("notifications") ? calendarExperienceRemindersForDate(key) : [];
  const notes = moduleEnabled("notes") ? notesOfDay(key) : [];
  const overtime = moduleEnabled("overtime") ? ledgerNetOf(key) : 0;
  return { shift, segments, tasks, important, reminders, notes, overtime };
}
function calendarExperienceSummaryText(key){
  const facts = calendarExperienceDayFacts(key);
  const pieces = [];
  if (facts.shift) pieces.push(shiftDisplayName(facts.shift));
  if (facts.tasks.length) pieces.push(`${facts.tasks.length} ${state.language === "en" ? "tasks" : "задач"}`);
  if (facts.important.length) pieces.push(`${facts.important.length} ★`);
  if (Math.abs(facts.overtime) > .001) pieces.push(`${facts.overtime > 0 ? "+" : ""}${fmtHours(facts.overtime)} ${state.language === "en" ? "h" : "ч"}`);
  return pieces.join(" · ") || t("Свободный день");
}

function calendarExperienceRenderWeek(){
  const strip = $("calendarWeekStrip");
  const agenda = $("calendarWeekAgenda");
  if (!strip || !agenda) return;
  const focus = calendarExperienceFocusDate();
  const start = calendarExperienceWeekStart(focus);
  strip.innerHTML = "";

  for (let index = 0; index < 7; index++) {
    const key = calendarExperienceOffset(start, index);
    const loaded = calendarExperienceLoadedMonthMatches(key);
    const facts = calendarExperienceDayFacts(key);
    const date = calendarExperienceDate(key);
    const button = document.createElement("button");
    button.type = "button";
    button.className = "calendarWeekDay";
    button.dataset.date = key;
    button.classList.toggle("isSelected", key === focus);
    button.classList.toggle("isToday", key === todayKey());
    if (facts.shift?.color) button.style.setProperty("--day-color", facts.shift.color);
    button.innerHTML = `
      <span>${esc(new Intl.DateTimeFormat(currentLocale(), { weekday:"short", timeZone:"UTC" }).format(date))}</span>
      <b>${date.getUTCDate()}</b>
      <small>${esc(loaded ? (facts.shift ? shiftDisplayName(facts.shift) : t("Свободно")) : new Intl.DateTimeFormat(currentLocale(), { month:"short", timeZone:"UTC" }).format(date))}</small>
      <i>${loaded ? `${facts.tasks.length ? `${facts.tasks.length}✓` : ""}${facts.important.length ? " ★" : ""}` : "↗"}</i>`;
    button.addEventListener("click", async () => {
      await calendarExperienceEnsureMonth(key);
      calendarExperienceSetFocusMemory(key);
      renderCalendar();
    });
    strip.appendChild(button);
  }

  const facts = calendarExperienceDayFacts(focus);
  agenda.innerHTML = "";
  const head = document.createElement("div");
  head.className = "calendarWeekAgendaHead";
  head.innerHTML = `<div><div class="eyebrow">${esc(t("Выбранный день"))}</div><h2>${esc(calendarExperienceLongLabel(focus))}</h2><p>${esc(calendarExperienceSummaryText(focus))}</p></div><button type="button">${esc(state.language === "en" ? "Hourly day" : "Почасовой день")}</button>`;
  head.querySelector("button").addEventListener("click", () => calendarExperienceSetMode("day"));
  agenda.appendChild(head);

  const list = document.createElement("div");
  list.className = "calendarWeekAgendaList";
  const rows = [];
  for (const segment of facts.segments) rows.push({ icon:"◷", title:facts.shift ? shiftDisplayName(facts.shift) : t("Смена"), meta:segment.range, color:facts.shift?.color });
  for (const task of facts.tasks.slice(0, 5)) rows.push({ icon:"✓", title:task.text || t("Задача"), meta:[task.dueTime, calendarExperienceTaskLabel(task)].filter(Boolean).join(" · "), task });
  for (const item of facts.important.slice(0, 3)) rows.push({ icon:"★", title:item.title || t("Важная дата"), meta:repeatLabel(item.repeatMode), color:item.color, important:true });
  if (Math.abs(facts.overtime) > .001) rows.push({ icon:"＋", title:t("Переработка"), meta:`${facts.overtime > 0 ? "+" : ""}${fmtHours(facts.overtime)} ${state.language === "en" ? "h" : "ч"}`, overtime:true });
  if (!rows.length) rows.push({ icon:"○", title:t("Планов на этот день нет"), meta:t("Можно спокойно оставить его свободным") });
  for (const row of rows) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "calendarAgendaRow";
    if (row.color) button.style.setProperty("--event-color", row.color);
    button.innerHTML = `<span>${esc(row.icon)}</span><span><b>${esc(row.title)}</b><small>${esc(row.meta || "")}</small></span><i>›</i>`;
    button.addEventListener("click", () => {
      if (row.task && typeof openTaskDetails === "function") openTaskDetails(row.task.id);
      else if (row.important) location.hash = "#important";
      else if (row.overtime) location.hash = "#overtime";
      else calendarExperienceSetMode("day");
    });
    list.appendChild(button);
  }
  agenda.appendChild(list);
}

function calendarExperienceTimelineEvents(key){
  const facts = calendarExperienceDayFacts(key);
  const events = [];
  for (const segment of facts.segments) {
    const start = calendarExperienceTimeMinutes(segment.startTime);
    const end = calendarExperienceTimeMinutes(segment.endTime);
    if (start == null || end == null || end <= start) continue;
    events.push({
      type:"shift", start, end, color:facts.shift?.color || "var(--accent)",
      title:facts.shift ? shiftDisplayName(facts.shift) : t("Смена"), meta:segment.range,
    });
  }
  for (const task of facts.tasks) {
    const time = task.dueTime || "";
    const start = calendarExperienceTimeMinutes(time);
    if (start == null) continue;
    events.push({ type:"task", start, end:Math.min(1440, start + 45), title:task.text || t("Задача"), meta:calendarExperienceTaskLabel(task), task });
  }
  for (const reminder of facts.reminders) {
    // Important dates already live in the all-day rail. Their notification time
    // is delivery metadata, not a second calendar event inside the hourly grid.
    if (String(reminder?.type || "").toUpperCase() === "IMPORTANT_DAY") continue;
    const time = calendarExperienceReminderTime(reminder);
    const start = calendarExperienceTimeMinutes(time);
    if (start == null) continue;
    events.push({ type:"reminder", start, end:Math.min(1440, start + 30), title:reminder.title || t("Напоминание"), meta:reminder.details || "", reminder });
  }
  for (const credit of creditsOf(key)) {
    const startTime = String(credit.displayStart || "").slice(11,16) || String(credit.timeRange || "").match(/(\d{2}:\d{2})/)?.[1] || "";
    const endTime = String(credit.displayEnd || "").slice(11,16) || String(credit.timeRange || "").match(/[–-](\d{2}:\d{2})/)?.[1] || "";
    const start = calendarExperienceTimeMinutes(startTime);
    const end = calendarExperienceTimeMinutes(endTime);
    if (start == null) continue;
    events.push({ type:"overtime", start, end:end != null && end > start ? end : Math.min(1440, start + 60), title:t("Переработка"), meta:`+${fmtHours(credit.hours)} ${state.language === "en" ? "h" : "ч"}${credit.reason ? ` · ${credit.reason}` : ""}`, credit });
  }
  return events.sort((a,b) => a.start - b.start || a.end - b.end);
}
function calendarExperienceRenderAllDay(key){
  const box = $("calendarAllDay");
  if (!box) return;
  const facts = calendarExperienceDayFacts(key);
  const items = [];
  if (facts.shift && !facts.segments.length) items.push({ type:"shift", icon:"◷", text:shiftDisplayName(facts.shift), color:facts.shift.color });
  for (const item of facts.important) items.push({ type:"important", icon:"★", text:item.title || t("Важная дата"), color:item.color });
  for (const task of facts.tasks.filter(item => !item.dueTime)) items.push({ type:"task", icon:"✓", text:task.text || t("Задача"), task });
  if (facts.notes.length) items.push({ type:"note", icon:"▤", text:`${state.language === "en" ? "Notes" : "Заметки"}: ${facts.notes.length}` });
  box.hidden = !items.length;
  box.innerHTML = "";
  if (!items.length) return;

  const head = document.createElement("div");
  head.className = "calendarAllDayHead";
  head.innerHTML = `<span>${esc(state.language === "en" ? "All day" : "Весь день")}</span><small>${items.length}</small>`;
  const list = document.createElement("div");
  list.className = "calendarAllDayItems";
  for (const item of items) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = `calendarAllDayItem ${item.type}`;
    if (item.color) button.style.setProperty("--event-color", item.color);
    button.innerHTML = `<span aria-hidden="true">${esc(item.icon || "○")}</span><b>${esc(item.text)}</b>`;
    button.addEventListener("click", () => {
      if (item.task && typeof openTaskDetails === "function") openTaskDetails(item.task.id);
      else calendarExperienceOpenLegacyDetails(key);
    });
    list.appendChild(button);
  }
  box.append(head, list);
}
function calendarExperienceRenderDay(){
  const title = $("calendarDayTitle");
  const subtitle = $("calendarDaySubtitle");
  const hours = $("calendarTimelineHours");
  const canvas = $("calendarTimelineCanvas");
  if (!title || !subtitle || !hours || !canvas) return;
  const key = calendarExperienceFocusDate();
  title.textContent = calendarExperienceLongLabel(key);
  subtitle.textContent = `${calendarExperienceSummaryText(key)} · ${state.timeSettings?.displayTimezone || state.timeSettings?.workTimezone || browserTimeZone()}`;
  calendarExperienceRenderAllDay(key);

  hours.innerHTML = "";
  canvas.innerHTML = "";
  for (let hour = 0; hour <= 24; hour += 2) {
    const label = document.createElement("span");
    label.style.top = `${(hour / 24) * 100}%`;
    label.textContent = `${String(hour).padStart(2, "0")}:00`;
    hours.appendChild(label);
    const line = document.createElement("i");
    line.style.top = `${(hour / 24) * 100}%`;
    canvas.appendChild(line);
  }

  const laneEnds = [];
  for (const event of calendarExperienceTimelineEvents(key)) {
    let lane = laneEnds.findIndex(end => end <= event.start);
    if (lane < 0) lane = laneEnds.length;
    laneEnds[lane] = event.end;
    const button = document.createElement("button");
    button.type = "button";
    button.className = `calendarTimelineEvent ${event.type}`;
    button.style.setProperty("--start", String(event.start / 14.4));
    button.style.setProperty("--duration", String(Math.max(2.8, (event.end - event.start) / 14.4)));
    button.style.setProperty("--lane", String(Math.min(lane, 3)));
    if (event.color) button.style.setProperty("--event-color", event.color);
    const start = `${String(Math.floor(event.start / 60)).padStart(2,"0")}:${String(event.start % 60).padStart(2,"0")}`;
    const end = `${String(Math.floor(event.end / 60)).padStart(2,"0")}:${String(event.end % 60).padStart(2,"0")}`.replace(/^24:/,"24:");
    button.innerHTML = `<b>${esc(event.title)}</b><span>${esc(event.meta || `${start}–${end}`)}</span>`;
    button.addEventListener("click", () => {
      if (event.task && typeof openTaskDetails === "function") openTaskDetails(event.task.id);
      else if (event.type === "overtime") location.hash = "#overtime";
      else calendarExperienceOpenLegacyDetails(key);
    });
    canvas.appendChild(button);
  }

  if (key === todayKey()) {
    const now = new Date();
    const minute = now.getHours() * 60 + now.getMinutes();
    const line = document.createElement("div");
    line.className = "calendarNowLine";
    line.style.top = `${(minute / 1440) * 100}%`;
    line.innerHTML = `<span>${String(now.getHours()).padStart(2,"0")}:${String(now.getMinutes()).padStart(2,"0")}</span>`;
    canvas.appendChild(line);
    requestAnimationFrame(() => line.scrollIntoView({ block:"center", behavior:"smooth" }));
  }

  if (!canvas.querySelector(".calendarTimelineEvent")) {
    const empty = document.createElement("div");
    empty.className = "calendarTimelineEmpty";
    empty.innerHTML = `<b>${esc(t("День свободен"))}</b><span>${esc(t("Добавьте задачу, смену или заметку"))}</span>`;
    canvas.appendChild(empty);
  }
}

function renderCalendarExperience(){
  const root = $("calendarExperience");
  if (!root) return;
  const shellNext = document.documentElement.dataset.shell !== "classic";
  root.hidden = !shellNext;
  const mode = shellNext ? state.calendarExperience.mode : "month";
  const month = $("calendarMonthExperience");
  const week = $("calendarWeekExperience");
  const day = $("calendarDayExperience");
  if (month) month.hidden = mode !== "month";
  if (week) week.hidden = mode !== "week";
  if (day) day.hidden = mode !== "day";

  document.querySelectorAll("[data-calendar-mode]").forEach(button => {
    const selected = button.dataset.calendarMode === mode;
    button.classList.toggle("on", selected);
    button.setAttribute("aria-pressed", String(selected));
    button.textContent = state.language === "en"
      ? ({ month:"Month", week:"Week", day:"Day" }[button.dataset.calendarMode] || button.textContent)
      : ({ month:"Месяц", week:"Неделя", day:"День" }[button.dataset.calendarMode] || button.textContent);
  });

  const focus = calendarExperienceFocusDate();
  const label = $("calendarFocusLabel");
  if (label) label.textContent = mode === "week" ? calendarExperienceWeekRangeLabel(calendarExperienceWeekStart(focus)) : calendarExperienceLongLabel(focus);
  if (mode === "week") calendarExperienceRenderWeek();
  if (mode === "day") calendarExperienceRenderDay();
}

async function calendarExperienceRestoreFocus(){
  const route = (location.hash || "").replace(/^#/, "");
  if (route !== "calendar" || state.calendarExperience.mode === "month") return;
  const focus = calendarExperienceFocusDate();
  if (!calendarExperienceLoadedMonthMatches(focus)) await calendarExperienceEnsureMonth(focus);
}

async function calendarExperienceNavigate(delta){
  const mode = state.calendarExperience.mode;
  const step = mode === "day" ? Number(delta) : mode === "week" ? Number(delta) * 7 : 0;
  if (!step) return false;
  const key = calendarExperienceOffset(calendarExperienceFocusDate(), step);
  await calendarExperienceEnsureMonth(key);
  calendarExperienceSetFocusMemory(key);
  calendarExperienceCloseLegacyPanel();
  renderCalendar();
  return true;
}
function calendarExperienceHeaderNavigate(delta){
  if (state.calendarExperience.mode === "month") return false;
  calendarExperienceNavigate(delta).catch(error => { console.error(error); setSave("err", error.message); });
  return true;
}
function calendarExperienceHeaderToday(){
  if (state.calendarExperience.mode === "month") return false;
  calendarExperienceOpen(todayKey(), state.calendarExperience.mode).catch(error => { console.error(error); setSave("err", error.message); });
  return true;
}

const calendarExperienceBaseRenderCalendar = renderCalendar;
renderCalendar = function renderCalendarWithExperience(){
  calendarExperienceBaseRenderCalendar();
  renderCalendarExperience();
};
const calendarExperienceBaseSelectDay = selectDay;
selectDay = function selectDayWithExperience(key){
  if (key) calendarExperienceSetFocusMemory(key);
  return calendarExperienceBaseSelectDay(key);
};

document.querySelectorAll("[data-calendar-mode]").forEach(button => button.addEventListener("click", () => calendarExperienceSetMode(button.dataset.calendarMode)));
$("calendarDayOpenDetails")?.addEventListener("click", () => calendarExperienceOpenLegacyDetails());

for (const id of ["calendarWeekExperience", "calendarDayExperience"]) {
  const target = $(id);
  if (!target) continue;
  let startX = null;
  target.addEventListener("touchstart", event => { startX = event.touches?.[0]?.clientX ?? null; }, { passive:true });
  target.addEventListener("touchend", event => {
    if (startX == null) return;
    const endX = event.changedTouches?.[0]?.clientX ?? startX;
    const delta = endX - startX;
    startX = null;
    if (Math.abs(delta) < 55) return;
    calendarExperienceNavigate(delta < 0 ? 1 : -1).catch(console.error);
  }, { passive:true });
}
