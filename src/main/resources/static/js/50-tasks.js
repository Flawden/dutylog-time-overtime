/*
 * 50-tasks.js — Задачи и важные дни: секции панели дня + общий экран задач
 * Часть бывшего app.js (распил v26.1). Файлы делят ГЛОБАЛЬНУЮ область
 * видимости (это не ES-модули); порядок подключения в index.html — закон.
 * Инвариант: склейка всех js/*.js по порядку === старый app.js.
 */
/* ─── Важные дни ───────────────────────────────────────────── */
async function refreshImportantSettings(){
  if (!moduleEnabled("important_dates")) { state.importantDays = []; renderImportantSettings(); return; }
  try {
    state.importantDays = await api.importantDays();
    renderImportantSettings();
  } catch (err) {
    console.error(err);
  }
}

function renderImportantSettings(){
  const box = document.getElementById("importantSettingsList");
  if (!box) return;
  const items = (state.importantDays || []).slice().sort((a,b) => String(a.date).localeCompare(String(b.date)) || String(a.title).localeCompare(String(b.title), "ru"));
  box.innerHTML = "";
  if (!items.length) {
    const empty = document.createElement("div");
    empty.className = "emptyLine";
    empty.textContent = t("Важных дат пока нет.");
    box.appendChild(empty);
    return;
  }
  for (const item of items) {
    const row = document.createElement("div");
    row.className = "importantItem settingsImportantItem";
    const dot = document.createElement("span");
    dot.className = "importantDot";
    dot.style.background = item.color || "var(--accent)";
    const title = document.createElement("span");
    title.className = "importantTitle";
    title.textContent = item.title;
    const date = document.createElement("span");
    date.className = "importantMode mono";
    date.textContent = (item.date || "").split("-").reverse().join(".");
    const mode = document.createElement("span");
    mode.className = "importantMode";
    mode.textContent = repeatLabel(item.repeatMode);
    const del = document.createElement("button");
    del.className = "tinyDel";
    del.type = "button";
    del.textContent = t("удалить");
    del.title = t("Удалить важный день полностью, включая повторения");
    del.addEventListener("click", () => removeImportantDay(item.id));
    row.append(dot, title, date, mode, del);
    box.appendChild(row);
  }
}

function renderImportantDays(){
  if (!moduleEnabled("important_dates")) { updateAccSummaries(); return; }
  const box = $("importantList");
  if (!box || !state.selected) return;
  const items = importantOf(state.selected);
  box.innerHTML = "";
  if (!items.length) {
    const empty = document.createElement("div");
    empty.className = "emptyLine";
    empty.textContent = t("В этот день важных событий нет.");
    box.appendChild(empty);
    updateAccSummaries();
    return;
  }
  for (const item of items) {
    const row = document.createElement("div");
    row.className = "importantItem";
    const dot = document.createElement("span");
    dot.className = "importantDot";
    dot.style.background = item.color || "var(--accent)";
    const title = document.createElement("span");
    title.className = "importantTitle";
    title.textContent = item.title;
    const mode = document.createElement("span");
    mode.className = "importantMode";
    mode.textContent = repeatLabel(item.repeatMode);
    const del = document.createElement("button");
    del.className = "tinyDel";
    del.type = "button";
    del.textContent = t("удалить");
    del.title = t("Удалить важный день полностью, включая повторения");
    del.addEventListener("click", () => removeImportantDay(item.id));
    row.append(dot, title, mode, del);
    box.appendChild(row);
  }
  updateAccSummaries();
}

async function addImportantDay(){
  if (!moduleEnabled("important_dates")) return setSave("err", t("модуль выключен"));
  const k = $("impDate")?.value || state.selected;
  if (!k) return setSave("err", t("укажи дату важного дня"));
  const title = $("impTitle").value.trim();
  if (!title) return setSave("err", t("укажи название важного дня"));
  setSave("saving");
  try {
    await api.createImportantDay({
      title,
      date: k,
      repeatMode: $("impRepeat").value,
      color: $("impColor").value || "#F5B841",
    });
    $("impTitle").value = "";
    if ($("impDate")) $("impDate").value = k;
    await refreshImportantSettings();
    await loadMonth();
    setSave("saved");
    renderImportantDays();
    renderImportantSettings();
    renderCalendar();
    updateAccSummaries();
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}

async function removeImportantDay(id){
  if (!confirm(t("Удалить важный день целиком, включая повторения?"))) return;
  setSave("saving");
  try {
    await api.deleteImportantDay(id);
    await refreshImportantSettings();
    await loadMonth();
    setSave("saved");
    renderImportantDays();
    renderImportantSettings();
    renderCalendar();
    updateAccSummaries();
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}

$("impAdd").addEventListener("click", addImportantDay);
$("impTitle").addEventListener("keydown", e => { if (e.key === "Enter") addImportantDay(); });
$("impDateSelected")?.addEventListener("click", () => { if (!state.selected) return setSave("err", t("сначала выбери день в календаре")); $("impDate").value = state.selected; });
$("impDateToday")?.addEventListener("click", () => { $("impDate").value = todayKey(); });

/* ─── Задачи дня ────────────────────────────────────────────── */
function renderTaskCategoryFilter(){
  const sel = $("taskCategoryFilter");
  if (!sel) return;
  const current = sel.value || state.taskFilters.category || "all";
  sel.innerHTML = `<option value="all">все категории</option>`;
  for (const cat of allTaskCategories()) {
    const opt = document.createElement("option");
    opt.value = cat; opt.textContent = cat;
    sel.appendChild(opt);
  }
  sel.value = [...sel.options].some(o => o.value === current) ? current : "all";
  state.taskFilters.category = sel.value;
}
function filteredTasksForSelected(){
  const items = tasksOf(state.selected);
  const status = state.taskFilters.status || "all";
  const category = state.taskFilters.category || "all";
  return items.filter(t => {
    if (category !== "all" && (t.category || "") !== category) return false;
    if (status === "open" && t.done) return false;
    if (status === "done" && !t.done) return false;
    if (status === "overdue" && (!t.overdue || t.done)) return false;
    return true;
  });
}
function renderTasks(){
  if (!moduleEnabled("tasks")) { updateAccSummaries(); return; }
  const box = $("taskList");
  if (!box || !state.selected) return;
  renderTaskCategoryFilter();
  const all = tasksOf(state.selected);
  const items = filteredTasksForSelected();
  box.innerHTML = "";
  if (!all.length) {
    const empty = document.createElement("div");
    empty.className = "emptyLine";
    empty.textContent = t("Задач пока нет. После добавления открытые задачи будут отмечены в календаре.");
    box.appendChild(empty);
    updateAccSummaries();
    return;
  }
  if (!items.length) {
    const empty = document.createElement("div");
    empty.className = "emptyLine";
    empty.textContent = t("По фильтрам задач нет.");
    box.appendChild(empty);
    updateAccSummaries();
    return;
  }
  for (const task of items) {
    const row = document.createElement("div");
    row.className = "taskItem" + (task.done ? " done" : "") + (task.overdue && !task.done ? " overdue" : "");
    const cb = document.createElement("input");
    cb.type = "checkbox";
    cb.checked = !!task.done;
    cb.addEventListener("change", () => toggleTask(task.id, cb.checked));
    const body = document.createElement("div");
    body.style.flex = "1";
    body.style.minWidth = "0";
    const text = document.createElement("span");
    text.className = "taskText";
    text.textContent = task.text;
    const meta = document.createElement("div");
    meta.className = "taskMeta";
    if (task.category) {
      const b = document.createElement("span"); b.className = "taskBadge cat"; b.textContent = task.category; meta.appendChild(b);
    }
    if (task.priority && task.priority !== "NORMAL") {
      const b = document.createElement("span"); b.className = "taskBadge " + task.priority.toLowerCase(); b.textContent = taskPriorityLabel(task.priority); meta.appendChild(b);
    }
    const due = taskDueLabel(task);
    if (due) { const b = document.createElement("span"); b.className = "taskBadge"; b.textContent = due; meta.appendChild(b); }
    if (task.reminderEnabled) { const b = document.createElement("span"); b.className = "taskBadge"; b.textContent = `🔔 ${task.reminderMinutesBefore ?? 0}м`; meta.appendChild(b); }
    if (task.overdue && !task.done) { const b = document.createElement("span"); b.className = "taskBadge overdue"; b.textContent = t("просрочено"); meta.appendChild(b); }
    body.append(text, meta);
    const edit = document.createElement("button");
    edit.className = "tinyDel"; edit.type = "button"; edit.textContent = t("ред."); edit.title = t("Редактировать задачу");
    edit.addEventListener("click", () => editTask(task));
    const del = document.createElement("button");
    del.className = "tinyDel";
    del.type = "button";
    del.textContent = "×";
    del.title = t("Удалить задачу");
    del.addEventListener("click", () => removeTask(task.id));
    row.append(cb, body, edit, del);
    box.appendChild(row);
  }
  updateAccSummaries();
}
function editTask(task){
  const text = prompt("Текст задачи", task.text || "");
  if (text === null) return;
  const category = prompt("Категория", task.category || "") ?? task.category;
  const dueDate = prompt("Срок yyyy-MM-dd, пусто — без срока", task.dueDate || "") ?? task.dueDate;
  const dueTime = prompt("Время срока HH:mm, пусто — без времени", task.dueTime || "") ?? task.dueTime;
  const reminder = confirm(t("Включить напоминание для этой задачи?"));
  updateTaskDetails(task.id, { text, category, dueDate, dueTime, reminderEnabled: reminder, reminderMinutesBefore: reminder ? (task.reminderMinutesBefore ?? 60) : null });
}

function removeTaskFromMaps(id){
  for (const k of Object.keys(state.tasksByDate)) {
    state.tasksByDate[k] = state.tasksByDate[k].filter(t => t.id !== id);
    if (!state.tasksByDate[k].length) delete state.tasksByDate[k];
  }
}
function upsertTaskInMaps(task){
  if (!task) return;
  removeTaskFromMaps(task.id);
  addToDateMap(state.tasksByDate, task);
}

async function addTask(){
  if (!moduleEnabled("tasks")) return setSave("err", t("модуль выключен"));
  const k = state.selected;
  if (!k) return;
  const text = $("taskText").value.trim();
  if (!text) return setSave("err", t("напиши текст задачи"));
  setSave("saving");
  try {
    const created = await api.createTask({
      date: k,
      text,
      category: $("taskCategory").value.trim() || null,
      priority: $("taskPriority").value || "NORMAL",
      dueDate: $("taskDueDate").value || null,
      dueTime: $("taskDueTime").value || null,
      reminderEnabled: $("taskReminderEnabled").checked,
      reminderMinutesBefore: $("taskReminderEnabled").checked ? Number($("taskReminderBefore").value || 0) : null,
    });
    upsertTaskInMaps(created);
    $("taskText").value = "";
    await loadTaskBoard(true);
    setSave("saved");
    renderTasks();
    renderCalendar();
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}

async function updateTaskDetails(id, patch){
  setSave("saving");
  try {
    const updated = await api.updateTask(id, patch);
    upsertTaskInMaps(updated);
    await loadTaskBoard(true);
    setSave("saved");
    renderTasks();
    renderCalendar();
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}

async function toggleTask(id, done){
  if (!moduleEnabled("tasks")) return setSave("err", t("модуль выключен"));
  setSave("saving");
  const oldTask = Object.values(state.tasksByDate).flat().find(t => Number(t.id) === Number(id)) || null;
  if (oldTask) upsertTaskInMaps({ ...oldTask, done: !!done });
  renderTasks();
  renderCalendar();
  try {
    const res = await dataLayer.setTaskDone(id, done);
    if (res.task) upsertTaskInMaps(res.task);
    if (!res.queued) await loadTaskBoard(true);
    else {
      state.taskBoard.items = (state.taskBoard.items || []).map(t => Number(t.id) === Number(id) ? { ...t, done: !!done } : t);
      renderTaskBoard();
    }
    setSave("saved");
    renderTasks();
    renderCalendar();
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
    if (oldTask) upsertTaskInMaps(oldTask);
    await loadMonth();
    renderTasks();
    renderCalendar();
  }
}

async function removeTask(id){
  if (!moduleEnabled("tasks")) return setSave("err", t("модуль выключен"));
  if (!confirm(t("Удалить задачу?"))) return;
  setSave("saving");
  try {
    await api.deleteTask(id);
    removeTaskFromMaps(id);
    state.taskBoard.items = (state.taskBoard.items || []).filter(t => t.id !== id);
    await loadTaskBoard(true);
    setSave("saved");
    renderTasks();
    renderTaskBoard();
    renderCalendar();
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}


/* ─── Общий экран задач ─────────────────────────────────────── */
function renderTaskBoardCategoryFilter(){
  const sel = $("taskBoardCategory");
  if (!sel) return;
  const current = sel.value || state.taskBoard.filters.category || "all";
  sel.innerHTML = `<option value="all">все категории</option>`;
  for (const cat of allTaskCategories()) {
    const opt = document.createElement("option");
    opt.value = cat; opt.textContent = cat;
    sel.appendChild(opt);
  }
  sel.value = [...sel.options].some(o => o.value === current) ? current : "all";
  state.taskBoard.filters.category = sel.value;
}
function syncTaskBoardFiltersToInputs(){
  const f = state.taskBoard.filters;
  if ($("taskBoardStatusFilter")) $("taskBoardStatusFilter").value = f.status || "open";
  if ($("taskBoardPriority")) $("taskBoardPriority").value = f.priority || "all";
  if ($("taskBoardSearch")) $("taskBoardSearch").value = f.q || "";
  if ($("taskBoardFrom")) $("taskBoardFrom").value = f.from || "";
  if ($("taskBoardTo")) $("taskBoardTo").value = f.to || "";
}
async function loadTaskBoard(silent = true){
  if (!moduleEnabled("tasks")) {
    state.taskBoard.items = [];
    state.taskBoard.page = { page:0, size:50, total:0, totalPages:0, hasPrevious:false, hasNext:false };
    renderTaskBoard();
    return;
  }
  try {
    const f = state.taskBoard.filters;
    const page = state.taskBoard.page || { page:0, size:50 };
    const query = {
      status: f.status || "open",
      category: f.category !== "all" ? f.category : "",
      priority: f.priority !== "all" ? f.priority : "",
      q: f.q || "",
      from: f.from || "",
      to: f.to || "",
      page: page.page || 0,
      size: page.size || 50,
    };
    const res = normalizePageResponse(await api.taskBoard(query), page.size || 50);
    state.taskBoard.items = res.items;
    state.taskBoard.page = res;
    renderTaskBoard();
  } catch (err) {
    console.error(err);
    if (!silent) setSave("err", err.message);
  }
}
function resetTaskBoardPage(){
  state.taskBoard.page = { ...(state.taskBoard.page || {}), page:0 };
}
function taskBoardDateLabel(task){
  const main = (task.dueDate || task.date || "").split("-").reverse().join(".");
  if (!task.dueDate) return main;
  return `${main}${task.dueTime ? " " + task.dueTime : ""}`;
}
function buildTaskMeta(task){
  const meta = document.createElement("div");
  meta.className = "taskMeta";
  if (task.category) {
    const b = document.createElement("span"); b.className = "taskBadge cat"; b.textContent = task.category; meta.appendChild(b);
  }
  if (task.priority && task.priority !== "NORMAL") {
    const b = document.createElement("span"); b.className = "taskBadge " + task.priority.toLowerCase(); b.textContent = taskPriorityLabel(task.priority); meta.appendChild(b);
  }
  const due = taskDueLabel(task);
  if (due) { const b = document.createElement("span"); b.className = "taskBadge"; b.textContent = due; meta.appendChild(b); }
  if (task.reminderEnabled) { const b = document.createElement("span"); b.className = "taskBadge"; b.textContent = `🔔 ${task.reminderMinutesBefore ?? 0}м`; meta.appendChild(b); }
  if (task.overdue && !task.done) { const b = document.createElement("span"); b.className = "taskBadge overdue"; b.textContent = t("просрочено"); meta.appendChild(b); }
  if (task.done) { const b = document.createElement("span"); b.className = "taskBadge"; b.textContent = t("выполнено"); meta.appendChild(b); }
  return meta;
}
function renderTaskBoard(){
  const list = $("taskBoardList");
  if (!list) return;
  syncTaskBoardFiltersToInputs();
  renderTaskBoardCategoryFilter();
  const items = state.taskBoard.items || [];
  const page = { ...(state.taskBoard.page || {}), items };
  const open = items.filter(t => !t.done).length;
  const overdue = items.filter(t => t.overdue && !t.done).length;
  const done = items.filter(t => t.done).length;
  $("taskBoardStatus").textContent = `${pageRangeText(page)}`;
  $("taskBoardStats").innerHTML = `
    <span class="pill">на странице <b>${items.length}</b></span>
    <span class="pill">всего по фильтрам <b>${page.total || items.length}</b></span>
    <span class="pill">открытых на странице <b>${open}</b></span>
    <span class="pill">просроченных на странице <b>${overdue}</b></span>
    <span class="pill">выполненных на странице <b>${done}</b></span>`;
  renderPager("taskBoardPager", page, nextPage => { state.taskBoard.page.page = nextPage; loadTaskBoard(false); }, nextSize => { state.taskBoard.page.size = nextSize; resetTaskBoardPage(); loadTaskBoard(false); });
  list.innerHTML = "";
  if (!items.length) {
    const empty = document.createElement("div");
    empty.className = "emptyLine";
    empty.textContent = t("По этим фильтрам задач нет.");
    list.appendChild(empty);
    return;
  }
  for (const task of items) {
    const row = document.createElement("div");
    row.className = "taskBoardItem" + (task.done ? " done" : "") + (task.overdue && !task.done ? " overdue" : "");
    const cb = document.createElement("input");
    cb.type = "checkbox";
    cb.checked = !!task.done;
    cb.addEventListener("change", () => toggleTask(task.id, cb.checked));
    const date = document.createElement("button");
    date.type = "button";
    date.className = "taskBoardDate";
    date.textContent = taskBoardDateLabel(task);
    date.title = `Открыть день ${task.date}`;
    date.addEventListener("click", () => goToTaskDate(task.date));
    const body = document.createElement("div");
    body.className = "taskBoardBody";
    const text = document.createElement("div");
    text.className = "taskText";
    text.textContent = task.text;
    body.append(text, buildTaskMeta(task));
    const actions = document.createElement("div");
    actions.className = "taskBoardActions";
    const edit = document.createElement("button");
    edit.type = "button"; edit.textContent = t("ред."); edit.title = t("Редактировать задачу");
    edit.addEventListener("click", () => editTask(task));
    const del = document.createElement("button");
    del.type = "button"; del.textContent = "×"; del.title = t("Удалить задачу");
    del.addEventListener("click", () => removeTask(task.id));
    actions.append(edit, del);
    row.append(cb, date, body, actions);
    list.appendChild(row);
  }
}
async function goToTaskDate(k){
  if (!k) return;
  const [y, m] = k.split("-").map(Number);
  const targetYear = y, targetMonth = m - 1;
  if (state.y !== targetYear || state.m !== targetMonth) {
    state.y = targetYear; state.m = targetMonth;
    await loadMonth();
  }
  selectDay(k);
}
function setTaskBoardQuickStatus(status){
  state.taskBoard.filters.status = status;
  resetTaskBoardPage();
  loadTaskBoard(false);
}

$("taskAdd").addEventListener("click", addTask);
$("taskText").addEventListener("keydown", e => { if (e.key === "Enter") addTask(); });
$("taskStatusFilter").addEventListener("change", () => { state.taskFilters.status = $("taskStatusFilter").value; renderTasks(); });
$("taskCategoryFilter").addEventListener("change", () => { state.taskFilters.category = $("taskCategoryFilter").value; renderTasks(); });
$("taskDueDate").addEventListener("change", () => { if ($("taskDueDate").value) $("taskReminderEnabled").checked = true; });

$("taskBoardOpen").addEventListener("click", () => setTaskBoardQuickStatus("open"));
$("taskBoardOverdue").addEventListener("click", () => setTaskBoardQuickStatus("overdue"));
$("taskBoardAll").addEventListener("click", () => setTaskBoardQuickStatus("all"));
$("taskBoardThisMonth").addEventListener("click", () => { const r = monthFromTo(); state.taskBoard.filters.from = r.from; state.taskBoard.filters.to = r.to; resetTaskBoardPage(); loadTaskBoard(false); });
$("taskBoardClear").addEventListener("click", () => { state.taskBoard.filters = { status:"open", category:"all", priority:"all", q:"", from:"", to:"" }; resetTaskBoardPage(); loadTaskBoard(false); });
$("taskBoardStatusFilter").addEventListener("change", () => { state.taskBoard.filters.status = $("taskBoardStatusFilter").value; resetTaskBoardPage(); loadTaskBoard(false); });
$("taskBoardCategory").addEventListener("change", () => { state.taskBoard.filters.category = $("taskBoardCategory").value; resetTaskBoardPage(); loadTaskBoard(false); });
$("taskBoardPriority").addEventListener("change", () => { state.taskBoard.filters.priority = $("taskBoardPriority").value; resetTaskBoardPage(); loadTaskBoard(false); });
$("taskBoardFrom").addEventListener("change", () => { state.taskBoard.filters.from = $("taskBoardFrom").value; resetTaskBoardPage(); loadTaskBoard(false); });
$("taskBoardTo").addEventListener("change", () => { state.taskBoard.filters.to = $("taskBoardTo").value; resetTaskBoardPage(); loadTaskBoard(false); });
$("taskBoardSearch").addEventListener("input", () => { clearTimeout(window.__taskBoardTimer); window.__taskBoardTimer = setTimeout(() => { state.taskBoard.filters.q = $("taskBoardSearch").value.trim(); resetTaskBoardPage(); loadTaskBoard(true); }, 350); });

function renderChips(){
  const box = $("chips");
  box.innerHTML = "";
  const cur = state.days[state.selected]?.shiftTypeId ?? null;
  for (const s of state.shiftTypes) {
    const b = document.createElement("button");
    const on = cur === s.id;
    b.className = "chip";
    b.style.background = on ? s.color : s.color + "1F";
    b.style.color = on ? "#14171C" : s.color;
    b.style.border = `1px solid ${on ? s.color : s.color + "55"}`;
    b.innerHTML = esc(s.name) + (shiftPlannedHours(s) ? ` <span class="h">·${fmtHours(shiftPlannedHours(s))}ч</span>` : "");
    const meta = shiftMetaText(s);
    if (meta) b.title = meta;
    b.addEventListener("click", () => toggleShift(s.id));
    box.appendChild(b);
  }
  // Плюсик — переход к настройкам смен
  const plus = document.createElement("button");
  plus.className = "chip plus";
  plus.textContent = "+";
  plus.title = t("Создать или настроить смену в настройках");
  plus.addEventListener("click", () => {
    openSettingsSection("shifts", true, "nsName");
  });
  box.appendChild(plus);
  renderCustomList();
  if (!$("tplBox")?.hidden) renderScheduleControls();
  renderQuickScenarioContext();
  updateAccSummaries();
}


function normalizeDayEmojiValue(value){
  const raw = String(value || "").trim();
  return raw.length > 32 ? raw.slice(0, 32) : raw;
}
function renderDayEmojiControls(){
  if (!$('dayEmojiGrid')) return;
  const k = state.selected;
  const cur = k ? normalizeDayEmojiValue(state.days[k]?.dayEmoji) : "";
  const grid = $('dayEmojiGrid');
  grid.innerHTML = "";
  for (const emoji of DAY_EMOJI_PRESETS) {
    const b = document.createElement("button");
    b.type = "button";
    b.className = "emojiChoice" + (emoji === cur ? " on" : "");
    b.textContent = emoji;
    b.title = t("Поставить маркер ") + emoji;
    b.addEventListener("click", () => setDayEmoji(emoji));
    grid.appendChild(b);
  }
  const input = $('dayEmojiCustom');
  if (input && document.activeElement !== input) input.value = cur;
  if ($('dayEmojiPreview')) $('dayEmojiPreview').textContent = cur ? `В календаре будет видно: ${cur}` : "Маркер не выбран.";
}
async function setDayEmoji(value){
  const k = state.selected;
  if (!k) return;
  const cur = state.days[k] || {};
  const next = {
    shiftTypeId: cur.shiftTypeId ?? null,
    note: cur.note || null,
    dayEmoji: normalizeDayEmojiValue(value) || "",
    overtimeHours: numOr0(cur.overtimeHours),
    timeOffHours: numOr0(cur.timeOffHours),
  };
  applyLocal(k, next);
  renderCalendar();
  renderDayEmojiControls();
  updateAccSummaries();
  await pushDaySnapshot(k, next);
}

async function toggleShift(id){
  const k = state.selected;
  const cur = state.days[k] || {};
  const next = {
    shiftTypeId: cur.shiftTypeId === id ? null : id,
    note: cur.note || null,
    dayEmoji: cur.dayEmoji || null,
    overtimeHours: numOr0(cur.overtimeHours),
    timeOffHours: numOr0(cur.timeOffHours),
  };
  applyLocal(k, next);
  updateAccSummaries();
  renderChips(); renderCalendar();
  await pushDaySnapshot(k, next);
}

function applyLocal(k, next){
  const clean = sanitizeDayForModules(next);
  const hasOvertime = Math.abs(clean.overtimeHours) > 0.0001 || Math.abs(clean.timeOffHours) > 0.0001;
  if (!clean.shiftTypeId && !(clean.note || "").trim() && !(clean.dayEmoji || "").trim() && !hasOvertime) delete state.days[k];
  else state.days[k] = clean;
}

/*
 * Отправка дня на сервер. Важно: сохраняем снимок данных, а не читаем
 * state.days[k] внутри setTimeout. Иначе можно потерять заметку, если
 * пользователь напечатал текст и сразу переключил месяц.
 */
async function pushDaySnapshot(k, payload){
  setSave("saving");
  try {
    const cleanPayload = sanitizeDayForModules(payload);
    const res = await dataLayer.putDay(k, {
      shiftTypeId: cleanPayload.shiftTypeId ?? null,
      note: cleanPayload.note ?? null,
      dayEmoji: cleanPayload.dayEmoji ?? null,
      overtimeHours: numOr0(cleanPayload.overtimeHours),
      timeOffHours: numOr0(cleanPayload.timeOffHours),
    });
    setSave(res.queued ? "saved" : "saved");
    if (res.queued) updateOfflineStatus();
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}

/* Заметка: локально сразу, на сервер — с задержкой */
let noteTimer = null;
let pendingDaySave = null;

function scheduleDaySave(k, payload){
  clearTimeout(noteTimer);
  pendingDaySave = { k, payload: { ...payload } };
  noteTimer = setTimeout(() => {
    const p = pendingDaySave;
    pendingDaySave = null;
    noteTimer = null;
    if (p) pushDaySnapshot(p.k, p.payload);
  }, 600);
}

async function flushPendingSave(){
  if (!pendingDaySave) return;
  clearTimeout(noteTimer);
  const p = pendingDaySave;
  pendingDaySave = null;
  noteTimer = null;
  await pushDaySnapshot(p.k, p.payload);
}

$("noteEdit").addEventListener("input", () => {
  if (!moduleEnabled("notes")) return;
  const k = state.selected;
  if (!k) return;
  const cur = state.days[k] || {};
  const next = {
    shiftTypeId: cur.shiftTypeId ?? null,
    note: $("noteEdit").value,
    dayEmoji: cur.dayEmoji || null,
    overtimeHours: numOr0(cur.overtimeHours),
    timeOffHours: numOr0(cur.timeOffHours),
  };
  applyLocal(k, next);
  updateAccSummaries();
  renderCalendar();
  scheduleDaySave(k, next);
});

function setTab(tabName){
  state.tab = tabName;
  $("tabEdit").classList.toggle("on", tabName === "edit");
  $("tabPrev").classList.toggle("on", tabName === "preview");
  $("noteEdit").hidden = tabName !== "edit";
  $("notePrev").hidden = tabName !== "preview";
  if (tabName === "preview") {
    const note = $("noteEdit").value;
    $("notePrev").innerHTML = note.trim() ? renderMd(note) : `<span class="empty">${esc(t("Заметка пустая — нечего показывать."))}</span>`;
  }
}
$("tabEdit").addEventListener("click", () => setTab("edit"));
$("tabPrev").addEventListener("click", () => setTab("preview"));
$("pClose").addEventListener("click", () => selectDay(null));
