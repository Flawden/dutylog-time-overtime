/*
 * 30-calendar.js — Calendar: markdown, month grid, selected-day panel and accordion UI
 *
 * DutyLog uses ordered browser scripts, not ES modules yet.
 * Keep the order in index.html stable: 10-core → 20-data → 30-calendar
 * → 40-overtime → 50-tasks → 60-settings → 70-user-boot.
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
function stOf(k){ const e = state.days[k]; return e ? state.shiftTypes.find(s => s.id === e.shiftTypeId) : null; }

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
    cell.className = "cell" + (state.selected === k ? " sel" : "") + (k === tk ? " todayCell" : "");
    if (st) {
      cell.style.background = st.color + "26";
      if (state.selected !== k) cell.style.borderColor = st.color + "55";
      const bar = document.createElement("div");
      bar.className = "bar"; bar.style.background = st.color;
      cell.appendChild(bar);
    }
    if (showNotes && entry?.note?.trim()) {
      const ear = document.createElement("div");
      ear.className = "ear";
      ear.style.borderTop = `14px solid ${st ? st.color : "var(--dim)"}`;
      ear.title = t("Есть заметка");
      cell.appendChild(ear);
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
    }
    const ledgerBal = showOvertime ? ledgerNetOf(k) : 0;
    const legacyBal = showOvertime ? numOr0(entry?.overtimeHours) - numOr0(entry?.timeOffHours) : 0;
    const bal = Math.abs(ledgerBal) > 0.0001 ? ledgerBal : legacyBal;
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
}

function renderSummary(){
  const counts = {}; let hours = 0, overtime = 0, timeOff = 0;
  for (const [k, v] of Object.entries(state.days)) {
    if (moduleEnabled("overtime")) {
      overtime += numOr0(v.overtimeHours);
      timeOff += numOr0(v.timeOffHours);
    }
    if (!v.shiftTypeId) continue;
    counts[v.shiftTypeId] = (counts[v.shiftTypeId] || 0) + 1;
    const st = state.shiftTypes.find(s => s.id === v.shiftTypeId);
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
    if (moduleEnabled("notes")) $("noteEdit").value = state.days[k]?.note || "";
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

async function applyScheduleTemplate(){
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
      if (e.date.startsWith(prefix)) {
        state.days[e.date] = normalizeDay(e);
      }
    }
    setSave("saved");
    renderChips();
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
  const st = state.shiftTypes.find(s => s.id === state.days[k]?.shiftTypeId);
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

  // Заметка: первая строка
  const note = moduleEnabled("notes") ? (state.days[k]?.note || "").trim() : "";
  const firstLine = note.split("\n")[0].replace(/^#+\s*/, "");
  if ($("sumNote")) $("sumNote").textContent = !moduleEnabled("notes") ? t("Скрыто модулем") : (note
    ? (firstLine.length > 34 ? firstLine.slice(0, 34) + "…" : firstLine)
    : "—");
}

$("tplPreset").addEventListener("change", renderScheduleControls);
$("tplDays").addEventListener("input", () => { $("tplDays").dataset.userTouched = "1"; });
$("tplApply").addEventListener("click", applyScheduleTemplate);
