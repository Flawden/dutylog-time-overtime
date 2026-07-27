/*
 * 30-calendar.js — Calendar: markdown, month grid, selected-day panel and accordion UI
 *
 * DutyLog uses ordered browser scripts, not ES modules yet.
 * Keep the order in index.html stable: 10-core → 20-data → 30-calendar
 * → 35-today → 40-overtime → 50-tasks → 60-settings → 70-user-boot.
 */

/* ─── Markdown (мини-парсер) ────────────────────────────────── */
function inlineMd(s){
  return s
    .replace(/`([^`]+)`/g, '<code class="mdc">$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
    .replace(/\*([^*]+)\*/g, "<em>$1</em>")
    .replace(/~~([^~]+)~~/g, "<del>$1</del>")
    .replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noopener" class="mda">$1</a>');
}
function renderMd(src){
  const lines = esc(src).split("\n");
  const out = [];
  let list = null, inCode = false, codeBuf = [];
  const closeList = () => { if (list) { out.push(`</${list}>`); list = null; } };
  for (const raw of lines) {
    if (raw.trim().startsWith("```")) {
      if (inCode) { out.push(`<pre class="mdpre"><code>${codeBuf.join("\n")}</code></pre>`); codeBuf = []; inCode = false; }
      else { closeList(); inCode = true; }
      continue;
    }
    if (inCode) { codeBuf.push(raw); continue; }
    const h = raw.match(/^(#{1,4})\s+(.*)$/);
    if (h) { closeList(); out.push(`<div class="mdh mdh${h[1].length}">${inlineMd(h[2])}</div>`); continue; }
    if (/^(-{3,}|\*{3,})\s*$/.test(raw)) { closeList(); out.push('<hr class="mdhr">'); continue; }
    const q = raw.match(/^>\s?(.*)$/);
    if (q) { closeList(); out.push(`<blockquote class="mdq">${inlineMd(q[1])}</blockquote>`); continue; }
    const task = raw.match(/^[-*]\s+\[( |x|X)\]\s+(.*)$/);
    if (task) {
      if (list !== "ul") { closeList(); out.push('<ul class="mdul">'); list = "ul"; }
      const done = task[1].toLowerCase() === "x";
      out.push(`<li class="mdtask"><span class="mdbox${done ? " on" : ""}">${done ? "✓" : ""}</span><span class="${done ? "mddone" : ""}">${inlineMd(task[2])}</span></li>`);
      continue;
    }
    const ul = raw.match(/^[-*]\s+(.*)$/);
    if (ul) { if (list !== "ul") { closeList(); out.push('<ul class="mdul">'); list = "ul"; } out.push(`<li>${inlineMd(ul[1])}</li>`); continue; }
    const ol = raw.match(/^\d+[.)]\s+(.*)$/);
    if (ol) { if (list !== "ol") { closeList(); out.push('<ol class="mdol">'); list = "ol"; } out.push(`<li>${inlineMd(ol[1])}</li>`); continue; }
    closeList();
    if (raw.trim() === "") continue;
    out.push(`<p class="mdp">${inlineMd(raw)}</p>`);
  }
  if (inCode) out.push(`<pre class="mdpre"><code>${codeBuf.join("\n")}</code></pre>`);
  closeList();
  return out.join("");
}

/* ─── Рендер календаря ──────────────────────────────────────── */
function occurrenceFromLegacyDay(day){
  const interval = day?.shiftInterval;
  if (!day?.shiftTypeId || !interval?.displayStart || !interval?.displayEnd) return null;
  return normalizeShiftOccurrence({
    dayEntryId:null, sourceDate:day.date, shiftTypeId:day.shiftTypeId,
    startInstant:interval.startInstant, endInstant:interval.endInstant,
    sourceStart:interval.workStart, sourceEnd:interval.workEnd,
    displayStart:interval.displayStart, displayEnd:interval.displayEnd,
    sourceTimezone:interval.workTimezone, displayTimezone:interval.displayTimezone,
    breakMinutes:interval.breakMinutes, elapsedMinutes:interval.elapsedMinutes,
    netMinutes:interval.netMinutes, legacyLocal:interval.legacyLocal !== false
  });
}

function normalizeShiftOccurrence(value = {}){
  return {
    ...value,
    dayEntryId:Number(value.dayEntryId || 0) || null,
    shiftTypeId:Number(value.shiftTypeId || 0) || null,
    sourceDate:String(value.sourceDate || ""),
    sourceStart:String(value.sourceStart || ""),
    sourceEnd:String(value.sourceEnd || ""),
    displayStart:String(value.displayStart || ""),
    displayEnd:String(value.displayEnd || ""),
    breakMinutes:Number(value.breakMinutes || 0),
    elapsedMinutes:Number(value.elapsedMinutes || 0),
    netMinutes:Number(value.netMinutes || 0),
    legacyLocal:!!value.legacyLocal,
  };
}
function localDatePart(value){ return String(value || "").slice(0,10); }
function localTimePart(value){ return String(value || "").slice(11,16); }
function nextIsoDate(value){
  const [y,m,d] = String(value).split("-").map(Number);
  const next = new Date(Date.UTC(y, m - 1, d + 1));
  return `${next.getUTCFullYear()}-${String(next.getUTCMonth()+1).padStart(2,"0")}-${String(next.getUTCDate()).padStart(2,"0")}`;
}
function occurrenceSegments(occurrence){
  const occ = normalizeShiftOccurrence(occurrence);
  const startDate = localDatePart(occ.displayStart);
  const endDate = localDatePart(occ.displayEnd);
  const startTime = localTimePart(occ.displayStart);
  const endTime = localTimePart(occ.displayEnd);
  if (!startDate || !endDate || !startTime || !endTime) return [];
  if (startDate === endDate) return [{ occurrence:occ, displayDate:startDate, startTime, endTime, range:`${startTime}–${endTime}`, first:true, last:true }];
  const segments = [];
  let date = startDate;
  let guard = 0;
  while (date <= endDate && guard++ < 8) {
    const first = date === startDate;
    const last = date === endDate;
    if (last && endTime === "00:00") break;
    const segmentStart = first ? startTime : "00:00";
    const segmentEnd = last ? endTime : "24:00";
    segments.push({ occurrence:occ, displayDate:date, startTime:segmentStart, endTime:segmentEnd, range:`${segmentStart}–${segmentEnd}`, first, last });
    date = nextIsoDate(date);
  }
  return segments;
}
function rebuildShiftOccurrenceIndex(){
  state.shiftSegmentsByDate = {};
  for (const occurrence of state.shiftOccurrences || []) {
    for (const segment of occurrenceSegments(occurrence)) {
      (state.shiftSegmentsByDate[segment.displayDate] ||= []).push(segment);
    }
  }
  for (const segments of Object.values(state.shiftSegmentsByDate)) {
    segments.sort((a,b) => a.startTime.localeCompare(b.startTime) || Number(a.occurrence.dayEntryId || 0) - Number(b.occurrence.dayEntryId || 0));
  }
}
function shiftSegmentsOf(k){ return state.shiftSegmentsByDate?.[k] || []; }
function primaryShiftSegment(k){ return shiftSegmentsOf(k)[0] || null; }
function shiftOccurrenceForDate(k){ return primaryShiftSegment(k)?.occurrence || null; }
function shiftSourceDateForSelected(){ return shiftOccurrenceForDate(state.selected)?.sourceDate || state.selected; }
function stOf(k){
  const projected = primaryShiftSegment(k);
  if (projected) return state.shiftTypes.find(s => Number(s.id) === Number(projected.occurrence.shiftTypeId)) || null;
  const e = state.days[k];
  const sourceType = e ? state.shiftTypes.find(s => Number(s.id) === Number(e.shiftTypeId)) : null;
  // Untimed types such as “Выходной” remain floating calendar-day markers.
  return sourceType && (!sourceType.startTime || !sourceType.endTime) ? sourceType : null;
}

function shiftIntervalRange(interval, projection = "display"){
  if (!interval) return "";
  const start = projection === "work" ? interval.workStart : interval.displayStart;
  const end = projection === "work" ? interval.workEnd : interval.displayEnd;
  return displayDateTimeRange(start, end);
}

function shiftIntervalTitle(interval){
  if (!interval) return "";
  const work = shiftIntervalRange(interval, "work");
  const display = shiftIntervalRange(interval, "display");
  if (interval.sameTimezone) return `${work} · ${interval.workTimezone || ""}`.trim();
  return `${t("Рабочая смена")}: ${work} · ${interval.workTimezone}\n${t("В часовом поясе отображения")}: ${display} · ${interval.displayTimezone}`;
}

function renderCalendar(){
  $("monthName").textContent = monthName(state.m);
  $("yearName").textContent = state.y;

  const grid = $("grid");
  grid.innerHTML = "";
  if (state.ui?.loadingCalendar) {
    for (let i = 0; i < 35; i++) {
      const c = document.createElement("div");
      c.className = "cell calendarSkeleton";
      c.innerHTML = "<span></span><i></i><em></em>";
      grid.appendChild(c);
    }
    const summary = $("summary");
    if (summary) summary.innerHTML = `<span class="lbl">${esc(t("загрузка…"))}</span><span style="color:var(--dim)">${esc(t("Загружаю календарь…"))}</span>`;
    if (typeof renderTodayDashboard === "function" && document.body.dataset.view === "today") renderTodayDashboard();
    return;
  }
  const first = new Date(state.y, state.m, 1);
  const offset = (first.getDay() + 6) % 7;
  const count = new Date(state.y, state.m + 1, 0).getDate();
  const tk = todayKey();
  const showNotes = moduleEnabled("notes");
  const showTasks = moduleEnabled("tasks");
  const showImportant = moduleEnabled("important_dates");
  const showOvertime = moduleEnabled("overtime");
  const showNotifications = moduleEnabled("notifications");

  for (let i = 0; i < offset; i++) {
    const c = document.createElement("div");
    c.className = "cell empty";
    grid.appendChild(c);
  }
  for (let d = 1; d <= count; d++) {
    const k = keyOf(state.y, state.m, d);
    const st = stOf(k);
    const entry = state.days[k];
    const cell = document.createElement("button");
    cell.dataset.date = k;
    cell.setAttribute("aria-label", `${d} ${monthNameGen(state.m)} ${state.y}`);
    cell.className = "cell" + (state.selected === k ? " sel" : "") + (k === tk ? " todayCell" : "");
    if (st) {
      cell.style.background = st.color + "26";
      if (state.selected !== k) cell.style.borderColor = st.color + "55";
      const bar = document.createElement("div");
      bar.className = "bar"; bar.style.background = st.color;
      cell.appendChild(bar);
    }
    const dayNotes = showNotes ? notesOfDay(k) : [];
    if (showNotes && (dayNotes.length || entry?.note?.trim())) {
      const ear = document.createElement("div");
      ear.className = "ear";
      ear.style.borderTop = `14px solid ${st ? st.color : "var(--dim)"}`;
      ear.title = dayNotes.length > 1 ? `${t("Заметки")}: ${dayNotes.length}` : t("Есть заметка");
      cell.appendChild(ear);
      if (dayNotes.length > 1) {
        const badge = document.createElement("span");
        badge.className = "noteCountBadge";
        badge.textContent = dayNotes.length;
        badge.title = `${t("Заметки")}: ${dayNotes.length}`;
        cell.appendChild(badge);
      }
    }
    if ((entry?.dayEmoji || "").trim()) {
      const em = document.createElement("span");
      em.className = "dayEmoji";
      em.textContent = entry.dayEmoji;
      em.title = t("Маркер дня");
      cell.appendChild(em);
    }
    const num = document.createElement("span");
    num.className = "num" + (k === tk ? " today" : "");
    num.textContent = d;
    cell.appendChild(num);
    if (st) {
      const nm = document.createElement("span");
      nm.className = "shift"; nm.style.color = st.color; nm.textContent = shiftDisplayName(st);
      cell.appendChild(nm);
      const segment = primaryShiftSegment(k);
      if (segment) {
        const occ = segment.occurrence;
        cell.title = `${t("Текущее отображение")}: ${segment.range} · ${occ.displayTimezone || ""}\n${t("Исходная смена")}: ${displayDateTimeRange(occ.sourceStart, occ.sourceEnd)} · ${occ.sourceTimezone || ""}`;
        const clock = document.createElement("span");
        clock.className = "shiftClock";
        clock.textContent = segment.range;
        cell.appendChild(clock);
        const extra = shiftSegmentsOf(k).length - 1;
        if (extra > 0) {
          const more = document.createElement("span");
          more.className = "shiftMore";
          more.textContent = `+${extra}`;
          cell.appendChild(more);
        }
      }
    }
    const bal = showOvertime ? ledgerNetOf(k) : 0;
    if (showOvertime && Math.abs(bal) > 0.0001) {
      const ot = document.createElement("span");
      ot.className = "otMark";
      ot.textContent = `${bal > 0 ? "+" : ""}${fmtHours(bal)}${state.language === "en" ? "h" : "ч"}`;
      cell.appendChild(ot);
    }

    const important = showImportant ? importantOf(k) : [];
    const activeTasks = showTasks ? activeTasksOf(k) : [];
    const allTasks = showTasks ? tasksOf(k) : [];
    const reminders = showNotifications ? remindersOf(k) : [];
    if (important.length || activeTasks.length || allTasks.length || reminders.length) {
      const marks = document.createElement("span");
      marks.className = "miniMarks";
      if (important.length) {
        const im = document.createElement("span");
        im.className = "importantMark";
        im.textContent = "★";
        im.style.background = important[0].color || "var(--accent)";
        im.title = important.map(x => x.title).join(", ");
        marks.appendChild(im);
      }
      const overdueTasks = overdueTasksOf(k);
      if (overdueTasks.length) {
        const tm = document.createElement("span");
        tm.className = "taskMark overdue";
        tm.textContent = "!!";
        tm.title = `${t("Просроченные задачи")}: ${overdueTasks.length}`;
        marks.appendChild(tm);
      } else if (activeTasks.length) {
        const tm = document.createElement("span");
        tm.className = "taskMark";
        tm.textContent = "!";
        tm.title = `${t("Невыполненные задачи")}: ${activeTasks.length}`;
        marks.appendChild(tm);
      } else if (allTasks.length) {
        const tm = document.createElement("span");
        tm.className = "taskMark done";
        tm.textContent = "✓";
        tm.title = t("Все задачи выполнены");
        marks.appendChild(tm);
      }
      if (reminders.length) {
        const nm = document.createElement("span");
        nm.className = "notifyMark";
        nm.textContent = "🔔";
        nm.title = `${t("Напоминания")}: ${reminders.length}`;
        marks.appendChild(nm);
      }
      cell.appendChild(marks);
    }
    cell.addEventListener("click", () => selectDay(state.selected === k ? null : k));
    grid.appendChild(cell);
  }
  renderSummary();
  if (typeof renderTodayDashboard === "function" && document.body.dataset.view === "today") renderTodayDashboard();
}

function renderSummary(){
  const counts = {}; let hours = 0;
  const monthStart = monthFromTo().from;
  const monthEnd = monthFromTo().to;
  const monthOvertime = moduleEnabled("overtime")
    ? overtimeRangeTotals(monthStart, monthEnd)
    : { earned:0, used:0, remaining:0 };
  const overtime = monthOvertime.earned;
  const timeOff = monthOvertime.used;
  for (const v of Object.values(state.days)) {
    const sourceType = state.shiftTypes.find(s => Number(s.id) === Number(v.shiftTypeId));
    if (sourceType && (!sourceType.startTime || !sourceType.endTime)) counts[sourceType.id] = (counts[sourceType.id] || 0) + 1;
  }
  for (const occurrence of state.shiftOccurrences || []) {
    if (!occurrenceSegments(occurrence).some(segment => segment.displayDate >= monthStart && segment.displayDate <= monthEnd)) continue;
    counts[occurrence.shiftTypeId] = (counts[occurrence.shiftTypeId] || 0) + 1;
    const st = state.shiftTypes.find(s => Number(s.id) === Number(occurrence.shiftTypeId));
    if (st) hours += shiftPlannedHours(st);
  }
  const el = $("summary");
  el.innerHTML = `<span class="lbl">${esc(t("Итого:"))}</span>`;
  let any = Math.abs(overtime) > 0.0001 || Math.abs(timeOff) > 0.0001;
  for (const s of state.shiftTypes) {
    if (!counts[s.id]) continue;
    any = true;
    const span = document.createElement("span");
    span.innerHTML = `<span class="dot" style="background:${s.color}"></span>${esc(shiftDisplayName(s))} — <b>${counts[s.id]}</b>`;
    el.appendChild(span);
  }
  const balance = overtime - timeOff;
  if (Math.abs(overtime) > 0.0001 || Math.abs(timeOff) > 0.0001) {
    const o = document.createElement("span");
    o.className = "over";
    o.textContent = t(`переработка: ${balance > 0 ? "+" : ""}${fmtHours(balance)} ч`);
    o.title = t(`Начислено: ${fmtHours(overtime)} ч, списано: ${fmtHours(timeOff)} ч`);
    el.appendChild(o);
  }
  if (hours > 0) {
    const h = document.createElement("span");
    h.className = "hrs"; h.textContent = `${fmtHours(hours)} ${state.language === "en" ? "h" : "ч"}`;
    el.appendChild(h);
  }
  const acc = state.overtimeAccount;
  if (moduleEnabled("overtime") && acc && (numOr0(acc.totalEarnedHours) > 0 || numOr0(acc.totalUsedHours) > 0)) {
    const global = document.createElement("span");
    global.className = "over";
    global.textContent = t(`общий остаток переработки: ${numOr0(acc.balanceHours) > 0 ? "+" : ""}${fmtHours(acc.balanceHours)} ч`);
    global.title = t(`Всего начислено: ${fmtHours(acc.totalEarnedHours)} ч, всего списано: ${fmtHours(acc.totalUsedHours)} ч`);
    el.appendChild(global);
    any = true;
  }
  if (!any) {
    const s = document.createElement("span");
    s.style.color = "var(--dim)"; s.textContent = t("Смены ещё не отмечены. Выберите день в календаре.");
    el.appendChild(s);
  }
  if (moduleEnabled("overtime")) renderLedgerTable();
}

/* ─── Панель дня ────────────────────────────────────────────── */
function selectDay(k){
  state.selected = k;
  $("layout").classList.toggle("with-panel", !!k);
  document.body.classList.toggle("panel-open", !!k);
  $("panel").hidden = !k;
  if (k) {
    const [y, m, d] = k.split("-").map(Number);
    const date = new Date(y, m - 1, d);
    $("pWeekday").textContent = weekdayName((date.getDay() + 6) % 7);
    $("pDate").innerHTML = state.language === "en" ? `${monthNameGen(m - 1)} ${d} <span class="yr mono">${y}</span>` : `${d} ${monthNameGen(m - 1)} <span class="yr mono">${y}</span>`;
    if (moduleEnabled("notes") && typeof renderDayNotes === "function") renderDayNotes();
    if (moduleEnabled("overtime")) resetOvertimeForms(k);
    if (moduleEnabled("notes")) setTab("edit");
    renderSelectedDayModules();
  }
  renderCalendar();
}


function renderScheduleControls(){
  if (!state.selected) return;
  const input = $("tplDays");
  if (!input.dataset.userTouched) input.value = DEFAULT_SCHEDULE_DAYS;

  const tpl = SCHEDULE_TEMPLATES[$("tplPreset").value];
  const names = effectiveTemplateNames(tpl, state.selected).map(shiftDisplayName);
  const missing = [...new Set(tpl.names)].filter(name => !findShiftByName(name));
  const hint = $("tplHint");
  if (missing.length) {
    hint.textContent = t(`Не хватает смен: ${missing.map(shiftDisplayName).join(", ")}. Перезагрузи страницу или создай их вручную.`);
    $("tplApply").disabled = true;
  } else if (tpl.weekly) {
    hint.textContent = t(`Пятидневка привязана к дням недели: Пн–Пт рабочие, Сб–Вс выходные. От выбранного дня пойдёт так: ${names.join(" → ")}.`);
    $("tplApply").disabled = false;
  } else {
    hint.textContent = t(`Шаблон от выбранного дня: ${names.join(" → ")}. Заметки не стираются, меняется только тип смены.`);
    $("tplApply").disabled = false;
  }
}

async function applyScheduleTemplate(event){
  event?.preventDefault();
  event?.stopPropagation();
  const k = state.selected;
  if (!k) return;
  await flushPendingSave();

  const tpl = SCHEDULE_TEMPLATES[$("tplPreset").value];
  const names = effectiveTemplateNames(tpl, k);
  const displayNames = names.map(shiftDisplayName);
  const shifts = names.map(findShiftByName);
  if (shifts.some(s => !s)) {
    renderScheduleControls();
    return setSave("err", t("не хватает смен для шаблона"));
  }

  const count = Number($("tplDays").value);
  if (!Number.isInteger(count) || count < 1 || count > 366) {
    return setSave("err", t("количество дней: от 1 до 366"));
  }

  setSave("saving");
  try {
    const changed = await api.fillDays({
      startDate: k,
      days: count,
      shiftTypeIds: shifts.map(s => s.id),
      overwriteExistingShift: $("tplOverwrite").checked,
    });

    const prefix = monthPrefix();
    for (const e of changed) {
      if (e.date.startsWith(prefix)) state.days[e.date] = normalizeDay(e);
    }

    // A successful mutation must be followed by a direct, cache-bypassing read from the
    // server. Do not route this through loadMonth(): that path is intentionally allowed
    // to paint IndexedDB first, which is useful on boot but wrong immediately after a
    // write where the user expects authoritative state.
    const requestedYear = state.y;
    const requestedMonth = state.m;
    const bundle = await api.month(requestedYear, requestedMonth, { fresh:true });
    if (state.y !== requestedYear || state.m !== requestedMonth) return;
    await dataLayer.writeSnapshot(bundle, requestedYear, requestedMonth);
    applyCalendarBundle(bundle);

    const monthStart = monthFromTo(requestedYear, requestedMonth).from;
    const monthEnd = monthFromTo(requestedYear, requestedMonth).to;
    const expectedVisible = changed.filter(e => e.date >= monthStart && e.date <= monthEnd).length;
    const actualVisible = Object.keys(state.days).filter(date => date >= monthStart && date <= monthEnd && state.days[date]?.shiftTypeId).length;
    if (actualVisible < expectedVisible) {
      throw new Error(`${t("Сервер вернул неполный график. Изменения не подтверждены.")} (${actualVisible}/${expectedVisible})`);
    }

    setSave("saved");
    renderChips();
    renderNotifications();
    renderCalendar();
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}

/* ─── Аккордеон панели дня ──────────────────────────────────── */
const ACC_IDS = ["accShift", "accEmoji", "accSched", "accOt", "accImp", "accTasks", "accNote"];
const ACC_STORE = "acc-open-v1";

// Восстанавливаем, какие секции пользователь держал открытыми
(function initAccordion(){
  let open = null;
  try { open = JSON.parse(localStorage.getItem(ACC_STORE)); } catch (e) { /* битые данные — дефолт */ }
  if (Array.isArray(open)) {
    for (const id of ACC_IDS) if ($(id)) $(id).open = open.includes(id);
  }
  for (const id of ACC_IDS) {
    const el = $(id);
    if (!el) continue;
    el.addEventListener("toggle", () => {
      localStorage.setItem(ACC_STORE, JSON.stringify(ACC_IDS.filter(i => $(i) && !$(i).hidden && $(i).open)));
      if (id === "accSched" && el.open) renderScheduleControls();
    });
  }
})();

/* Выжимки в заголовках свёрнутых секций — панель читается не раскрывая */
function updateAccSummaries(){
  const k = state.selected;
  if (!k) return;

  // Смена
  const st = stOf(k);
  $("sumShift").innerHTML = st
    ? `<span class="dot" style="background:${st.color}"></span><span style="color:${st.color}">${esc(shiftDisplayName(st))}${shiftPlannedHours(st) ? " · " + fmtHours(shiftPlannedHours(st)) + (state.language === "en" ? "h" : "ч") : ""}</span>`
    : t("не отмечена");

  // График — какой шаблон сейчас выбран
  const tpl = SCHEDULE_TEMPLATES[$("tplPreset").value];
  $("sumSched").textContent = scheduleTemplateLabel(tpl);

  // Переработка: движение за день, иначе общий баланс
  if ($("sumOt")) {
    if (moduleEnabled("overtime")) {
      const dayNet = ledgerNetOf(k);
      const bal = numOr0(state.overtimeAccount?.balanceHours);
      $("sumOt").textContent = Math.abs(dayNet) > 0.001
        ? `${t("за день")} ${dayNet > 0 ? "+" : ""}${fmtHours(dayNet)} ${state.language === "en" ? "h" : "ч"}`
        : `${t("баланс")} ${bal > 0 ? "+" : ""}${fmtHours(bal)} ${state.language === "en" ? "h" : "ч"}`;
      $("sumOt").style.color = Math.abs(dayNet) > 0.001 ? "var(--accent)" : "";
    } else { $("sumOt").textContent = t("Скрыто модулем"); $("sumOt").style.color = ""; }
  }

  // Важные дни
  if ($("sumImp")) {
    const imp = moduleEnabled("important_dates") ? importantOf(k) : [];
    $("sumImp").innerHTML = !moduleEnabled("important_dates")
      ? esc(t("Скрыто модулем"))
      : (imp.length ? `<span style="color:var(--accent)">★ ${imp.length}</span>&nbsp;${esc(imp[0].title)}${imp.length > 1 ? "…" : ""}` : "—");
  }

  // Задачи: сделано/всего
  if ($("sumTasks")) {
    const all = moduleEnabled("tasks") ? tasksOf(k) : [];
    const undone = moduleEnabled("tasks") ? activeTasksOf(k).length : 0;
    const overdue = moduleEnabled("tasks") ? overdueTasksOf(k).length : 0;
    $("sumTasks").textContent = !moduleEnabled("tasks") ? t("Скрыто модулем") : (all.length ? (overdue ? `${overdue} ${t("просроч.")} · ${all.length - undone}/${all.length}` : `${all.length - undone}/${all.length} ${t("сделано")}`) : "—");
    $("sumTasks").style.color = overdue ? "var(--danger)" : (undone > 0 ? "var(--accent)" : "");
  }

  // Emoji-маркер
  const emoji = (state.days[k]?.dayEmoji || "").trim();
  if ($("sumEmoji")) $("sumEmoji").textContent = emoji || "—";

  // Заметки: количество и название активной/первой
  const notes = moduleEnabled("notes") ? notesOfDay(k) : [];
  const first = notes[0] || null;
  const firstLine = String(first?.title || first?.content || state.days[k]?.note || "").trim().split("\n")[0].replace(/^#+\s*/, "");
  if ($("sumNote")) $("sumNote").textContent = !moduleEnabled("notes") ? t("Скрыто модулем") : (notes.length
    ? `${notes.length} · ${firstLine.length > 26 ? firstLine.slice(0, 26) + "…" : (firstLine || t("Без названия"))}`
    : "—");
}

$("tplPreset").addEventListener("change", renderScheduleControls);
$("tplDays").addEventListener("input", () => { $("tplDays").dataset.userTouched = "1"; });
$("tplApply").addEventListener("click", applyScheduleTemplate);
