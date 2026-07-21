/*
 * 50-tasks.js — Tasks and important dates: day-panel sections and board UI
 *
 * DutyLog uses ordered browser scripts, not ES modules yet.
 * Keep the order in index.html stable: 10-core → 20-data → 30-calendar
 * → 40-overtime → 50-tasks → 60-settings → 70-user-boot.
 */

Object.assign(I18N_EN, {
  "Даты":"Dates", "Важные дни":"Important dates", "ближайшие":"upcoming", "прошедшие":"past",
  "повторяющиеся":"recurring", "все даты":"all dates", "Название важного дня":"Important date title",
  "Название":"Title", "Следующее событие":"Next occurrence", "Базовая дата":"Base date", "Повтор":"Repeat",
  "Открыть день":"Open day", "Редактировать":"Edit", "Добавить":"Add", "Отмена":"Cancel",
  "Сохранить":"Save", "Событие обновлено":"Event updated", "Событие добавлено":"Event added",
  "поиск: название или дата…":"search: title or date…", "Важных дат пока нет.":"No important dates yet.",
  "Добавь первую дату здесь или из выбранного дня календаря.":"Add the first date here or from a selected calendar day.",
  "По фильтрам ничего не найдено.":"Nothing matches the filters.", "Сбрось поиск или выбери другой период.":"Clear search or choose another period.",
  "Редактировать задачу":"Edit task", "Текст задачи":"Task text", "Дата":"Date", "Категория":"Category",
  "Приоритет":"Priority", "Срок":"Due date", "Время срока":"Due time", "Напомнить о задаче":"Remind me",
  "За сколько минут":"Minutes before", "Задача обновлена":"Task updated", "Не удалось сохранить задачу":"Failed to save task"
});
Object.assign(I18N_RU, Object.fromEntries(Object.entries(I18N_EN).map(([ru,en]) => [en, ru])));

/* ─── Важные дни ───────────────────────────────────────────── */
async function refreshImportantSettings(){
  if (!moduleEnabled("important_dates")) {
    state.importantDays = [];
    renderImportantBoard();
    return;
  }
  try {
    state.importantDays = await api.importantDays();
    renderImportantBoard();
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}

function importantDateParts(value){
  const parts = String(value || "").split("-").map(Number);
  return parts.length === 3 && parts.every(Number.isFinite) ? parts : null;
}
function clampedDate(year, monthIndex, day){
  const last = new Date(year, monthIndex + 1, 0).getDate();
  return keyOf(year, monthIndex, Math.min(day, last));
}
function importantNextOccurrence(item, today = todayKey()){
  const parts = importantDateParts(item.date);
  if (!parts) return item.date || "";
  const [year, month, day] = parts;
  if (item.repeatMode === "YEARLY") {
    const currentYear = Number(today.slice(0,4));
    let candidate = clampedDate(currentYear, month - 1, day);
    if (candidate < today) candidate = clampedDate(currentYear + 1, month - 1, day);
    return candidate;
  }
  if (item.repeatMode === "MONTHLY") {
    const [ty, tm] = today.split("-").map(Number);
    let candidate = clampedDate(ty, tm - 1, day);
    if (candidate < today) {
      const next = new Date(ty, tm, 1);
      candidate = clampedDate(next.getFullYear(), next.getMonth(), day);
    }
    return candidate;
  }
  return keyOf(year, month - 1, day);
}
function importantBoardItems(){
  const filters = state.importantFilters || { scope:"all", q:"" };
  const today = todayKey();
  const q = String(filters.q || "").trim().toLowerCase();
  return (state.importantDays || []).map(item => ({ ...item, nextOccurrence:importantNextOccurrence(item, today) }))
    .filter(item => {
      if (filters.scope === "recurring" && item.repeatMode === "NONE") return false;
      if (filters.scope === "past" && !(item.repeatMode === "NONE" && item.date < today)) return false;
      if (filters.scope === "upcoming" && item.repeatMode === "NONE" && item.date < today) return false;
      if (q && !`${item.title || ""} ${item.date || ""} ${item.nextOccurrence || ""}`.toLowerCase().includes(q)) return false;
      return true;
    })
    .sort((a,b) => String(a.nextOccurrence).localeCompare(String(b.nextOccurrence)) || String(a.title).localeCompare(String(b.title), currentLocale()));
}
function resetImportantBoardForm(){
  state.editingImportantDayId = null;
  if ($("importantBoardTitle")) $("importantBoardTitle").value = "";
  if ($("importantBoardDate")) $("importantBoardDate").value = todayKey();
  if ($("importantBoardRepeat")) $("importantBoardRepeat").value = "YEARLY";
  if ($("importantBoardColor")) $("importantBoardColor").value = "#F5B841";
  if ($("importantBoardSave")) $("importantBoardSave").textContent = t("Добавить");
  if ($("importantBoardCancel")) $("importantBoardCancel").hidden = true;
}
function startEditImportantDay(id){
  const item = (state.importantDays || []).find(x => Number(x.id) === Number(id));
  if (!item) return setSave("err", t("Важный день не найден"));
  state.editingImportantDayId = Number(id);
  location.hash = "#important";
  $("importantBoardTitle").value = item.title || "";
  $("importantBoardDate").value = item.date || todayKey();
  $("importantBoardRepeat").value = item.repeatMode || "NONE";
  $("importantBoardColor").value = item.color || "#F5B841";
  $("importantBoardSave").textContent = t("Сохранить");
  $("importantBoardCancel").hidden = false;
  $("importantBoardTitle").focus();
  renderImportantBoard();
}
async function openImportantCalendarDay(date){
  if (!date) return;
  const [y,m] = date.split("-").map(Number);
  if (!y || !m) return;
  await goto(y, m - 1);
  location.hash = "#calendar";
  selectDay(date);
}
function renderImportantBoard(){
  const box = $("importantBoardList");
  if (!box) return;
  const items = importantBoardItems();
  const total = (state.importantDays || []).length;
  if ($("importantBoardStatus")) {
    $("importantBoardStatus").className = "status statusMetrics";
    $("importantBoardStatus").innerHTML = `<span class="statusChip"><b>${items.length}</b> ${esc(t("показано"))}</span><span class="statusChip"><b>${total}</b> ${esc(t("всего"))}</span>`;
  }
  if ($("importantBoardScope")) $("importantBoardScope").value = state.importantFilters?.scope || "all";
  if ($("importantBoardSearch")) $("importantBoardSearch").value = state.importantFilters?.q || "";
  box.innerHTML = "";
  if (!total) {
    renderEmptyState(box, { icon:"★", title:"Важных дат пока нет.", text:"Добавь первую дату здесь или из выбранного дня календаря.", variant:"board" });
    return;
  }
  if (!items.length) {
    renderEmptyState(box, { icon:"⌕", title:"По фильтрам ничего не найдено.", text:"Сбрось поиск или выбери другой период.", variant:"board" });
    return;
  }
  for (const item of items) {
    const row = document.createElement("div");
    row.className = "importantBoardRow" + (Number(item.id) === Number(state.editingImportantDayId) ? " editing" : "");
    row.innerHTML = `
      <span class="importantDot" style="background:${esc(item.color || "#F5B841")}"></span>
      <span class="importantBoardMain"><b>${esc(item.title)}</b><small>${esc(t("Базовая дата"))}: ${esc(formatDateHuman(item.date))} · ${esc(repeatLabel(item.repeatMode))}</small></span>
      <span class="importantBoardNext"><small>${esc(t("Следующее событие"))}</small><b class="mono">${esc(formatDateHuman(item.nextOccurrence))}</b></span>
      <span class="importantBoardActions">
        <button type="button" data-important-open="${item.id}">${esc(t("Открыть день"))}</button>
        <button type="button" data-important-edit="${item.id}">${esc(t("ред."))}</button>
        <button type="button" data-important-delete="${item.id}">${esc(t("удалить"))}</button>
      </span>`;
    box.appendChild(row);
  }
  box.querySelectorAll("[data-important-open]").forEach(btn => btn.addEventListener("click", () => {
    const item = (state.importantDays || []).find(x => Number(x.id) === Number(btn.dataset.importantOpen));
    openImportantCalendarDay(importantNextOccurrence(item));
  }));
  box.querySelectorAll("[data-important-edit]").forEach(btn => btn.addEventListener("click", () => startEditImportantDay(Number(btn.dataset.importantEdit))));
  box.querySelectorAll("[data-important-delete]").forEach(btn => btn.addEventListener("click", () => removeImportantDay(Number(btn.dataset.importantDelete))));
}
async function saveImportantBoardItem(){
  if (!moduleEnabled("important_dates")) return setSave("err", t("модуль выключен"));
  const title = $("importantBoardTitle")?.value.trim();
  const date = $("importantBoardDate")?.value;
  if (!title) return setSave("err", t("укажи название важного дня"));
  if (!date) return setSave("err", t("укажи дату важного дня"));
  const payload = {
    title, date,
    repeatMode:$("importantBoardRepeat")?.value || "NONE",
    color:$("importantBoardColor")?.value || "#F5B841"
  };
  setSave("saving");
  try {
    if (state.editingImportantDayId) await api.updateImportantDay(state.editingImportantDayId, payload);
    else await api.createImportantDay(payload);
    const wasEditing = !!state.editingImportantDayId;
    resetImportantBoardForm();
    await refreshImportantSettings();
    if (typeof invalidateBrowserNotificationSchedule === "function") invalidateBrowserNotificationSchedule();
    await loadMonth();
    renderCalendar();
    setSave("saved", t(wasEditing ? "Событие обновлено" : "Событие добавлено"));
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}

function renderImportantDays(){
  if (!moduleEnabled("important_dates")) { updateAccSummaries(); return; }
  const box = $("importantList");
  if (!box || !state.selected) return;
  const items = importantOf(state.selected);
  box.innerHTML = "";
  if (!items.length) {
    renderEmptyState(box, { icon:"★", title:"В этот день важных событий нет.", text:"Отлично. Здесь пока чисто.", variant:"compact" });
    updateAccSummaries();
    return;
  }
  for (const item of items) {
    const row = document.createElement("div");
    row.className = "importantItem";
    row.innerHTML = `<span class="importantDot" style="background:${esc(item.color || "#F5B841")}"></span><span class="importantTitle">${esc(item.title)}</span><span class="importantMode">${esc(repeatLabel(item.repeatMode))}</span><button type="button" data-day-important-edit="${item.id}">${esc(t("ред."))}</button><button class="tinyDel" type="button" data-day-important-delete="${item.id}">${esc(t("удалить"))}</button>`;
    box.appendChild(row);
  }
  box.querySelectorAll("[data-day-important-edit]").forEach(btn => btn.addEventListener("click", () => startEditImportantDay(Number(btn.dataset.dayImportantEdit))));
  box.querySelectorAll("[data-day-important-delete]").forEach(btn => btn.addEventListener("click", () => removeImportantDay(Number(btn.dataset.dayImportantDelete))));
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
    await api.createImportantDay({ title, date:k, repeatMode:$("impRepeat").value, color:$("impColor").value || "#F5B841" });
    $("impTitle").value = "";
    if ($("impDate")) $("impDate").value = k;
    await refreshImportantSettings();
    if (typeof invalidateBrowserNotificationSchedule === "function") invalidateBrowserNotificationSchedule();
    await loadMonth();
    setSave("saved");
    renderImportantDays();
    renderImportantBoard();
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
    if (Number(state.editingImportantDayId) === Number(id)) resetImportantBoardForm();
    await refreshImportantSettings();
    if (typeof invalidateBrowserNotificationSchedule === "function") invalidateBrowserNotificationSchedule();
    await loadMonth();
    setSave("saved");
    renderImportantDays();
    renderImportantBoard();
    renderCalendar();
    updateAccSummaries();
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}

$("impAdd")?.addEventListener("click", addImportantDay);
$("impTitle")?.addEventListener("keydown", e => { if (e.key === "Enter") addImportantDay(); });
$("impDateSelected")?.addEventListener("click", () => { if (!state.selected) return setSave("err", t("сначала выбери день в календаре")); $("impDate").value = state.selected; });
$("impDateToday")?.addEventListener("click", () => { $("impDate").value = todayKey(); });
$("importantBoardSave")?.addEventListener("click", saveImportantBoardItem);
$("importantBoardCancel")?.addEventListener("click", resetImportantBoardForm);
$("importantBoardTitle")?.addEventListener("keydown", e => { if (e.key === "Enter") saveImportantBoardItem(); });
$("importantBoardScope")?.addEventListener("change", e => { state.importantFilters.scope = e.target.value; renderImportantBoard(); });
$("importantBoardSearch")?.addEventListener("input", e => { state.importantFilters.q = e.target.value; renderImportantBoard(); });
$("importantBoardToday")?.addEventListener("click", () => { $("importantBoardDate").value = todayKey(); });
$("importantBoardClear")?.addEventListener("click", () => { state.importantFilters = { scope:"all", q:"" }; renderImportantBoard(); });
resetImportantBoardForm();

/* ─── Задачи дня ────────────────────────────────────────────── */
function updateTaskReminderControls(){
  const notificationsAvailable = state.modulesLoaded && moduleEnabled("notifications");
  const enabled = $("taskReminderEnabled");
  const before = $("taskReminderBefore");
  const hint = $("taskReminderModuleHint");
  if (!enabled || !before) return;
  enabled.disabled = !notificationsAvailable;
  if (!notificationsAvailable) enabled.checked = false;
  before.disabled = !notificationsAvailable || !enabled.checked;
  if (hint) hint.hidden = notificationsAvailable;
  const title = notificationsAvailable ? "" : t("Включите модуль «Уведомления», чтобы напоминания задач отправлялись.");
  $("taskReminderToggleLabel")?.setAttribute("title", title);
  $("taskReminderBeforeLabel")?.setAttribute("title", title);
}
function renderTaskCategoryFilter(){
  const sel = $("taskCategoryFilter");
  if (!sel) return;
  const current = sel.value || state.taskFilters.category || "all";
  sel.innerHTML = `<option value="all">${esc(t("все категории"))}</option>`;
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
  updateTaskReminderControls();
  if (!moduleEnabled("tasks")) { updateAccSummaries(); return; }
  const box = $("taskList");
  if (!box || !state.selected) return;
  renderTaskCategoryFilter();
  const all = tasksOf(state.selected);
  const items = filteredTasksForSelected();
  box.innerHTML = "";
  if (!all.length) {
    renderEmptyState(box, {
      icon:"✓",
      title:"Задач пока нет. После добавления открытые задачи будут отмечены в календаре.",
      text:"Выбери день в календаре и добавь первую задачу.",
      variant:"compact"
    });
    updateAccSummaries();
    return;
  }
  if (!items.length) {
    renderEmptyState(box, { icon:"⌕", title:"По фильтрам задач нет.", text:"Попробуй сбросить фильтры или выбрать другой период.", variant:"compact" });
    updateAccSummaries();
    return;
  }
  for (const task of items) {
    const row = document.createElement("div");
    row.dataset.taskId = String(task.id);
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
function taskEditorMessage(text = "", tone = ""){
  const box = $("taskEditMessage");
  if (!box) return;
  box.textContent = text;
  box.className = "appModalMessage" + (tone ? ` ${tone}` : "");
}
function syncTaskEditorReminder(){
  const enabled = !!$("taskEditReminderEnabled")?.checked;
  const available = state.modulesLoaded && moduleEnabled("notifications");
  if ($("taskEditReminderEnabled")) {
    $("taskEditReminderEnabled").disabled = !available;
    if (!available) $("taskEditReminderEnabled").checked = false;
  }
  if ($("taskEditReminderBefore")) $("taskEditReminderBefore").disabled = !available || !enabled;
  if ($("taskEditReminderBeforeLabel")) $("taskEditReminderBeforeLabel").hidden = !available || !enabled;
}
function closeTaskEditor(){
  state.editingTaskId = null;
  taskEditorMessage();
  closeAppModal("taskEditModal");
}
function taskById(id){
  const all = [...Object.values(state.tasksByDate || {}).flat(), ...(state.taskBoard?.items || [])];
  return all.find(item => Number(item.id) === Number(id)) || null;
}
function editTask(task){
  if (!task) return;
  state.editingTaskId = Number(task.id);
  $("taskEditText").value = task.text || "";
  $("taskEditDate").value = task.date || state.selected || todayKey();
  $("taskEditCategory").value = task.category || "";
  $("taskEditPriority").value = task.priority || "NORMAL";
  $("taskEditDueDate").value = task.dueDate || "";
  $("taskEditDueTime").value = task.dueTime || "";
  $("taskEditReminderEnabled").checked = !!task.reminderEnabled;
  $("taskEditReminderBefore").value = String(task.reminderMinutesBefore ?? 60);
  taskEditorMessage();
  syncTaskEditorReminder();
  openAppModal("taskEditModal", "taskEditText");
}
async function saveTaskEditor(){
  const id = Number(state.editingTaskId);
  if (!id) return;
  const text = $("taskEditText").value.trim();
  const date = $("taskEditDate").value;
  if (!text) return taskEditorMessage(t("напиши текст задачи"), "err");
  if (!date) return taskEditorMessage(t("укажи дату"), "err");
  const original = taskById(id);
  const remindersAvailable = state.modulesLoaded && moduleEnabled("notifications");
  const reminderEnabled = remindersAvailable ? !!$("taskEditReminderEnabled").checked : !!original?.reminderEnabled;
  const reminderMinutesBefore = remindersAvailable
    ? (reminderEnabled ? Number($("taskEditReminderBefore").value || 0) : null)
    : (original?.reminderMinutesBefore ?? null);
  if (reminderEnabled && (!Number.isFinite(reminderMinutesBefore) || reminderMinutesBefore < 0 || reminderMinutesBefore > 10080)) {
    return taskEditorMessage(t("напоминание: от 0 до 10080 минут"), "err");
  }
  $("taskEditSave").disabled = true;
  taskEditorMessage(t("сохранение…"));
  const updated = await updateTaskDetails(id, {
    text,
    date,
    category:$("taskEditCategory").value.trim(),
    priority:$("taskEditPriority").value || "NORMAL",
    dueDate:$("taskEditDueDate").value || "",
    dueTime:$("taskEditDueTime").value || "",
    reminderEnabled,
    reminderMinutesBefore,
  });
  $("taskEditSave").disabled = false;
  if (!updated) return taskEditorMessage(t("Не удалось сохранить задачу"), "err");
  closeTaskEditor();
  setSave("saved", t("Задача обновлена"));
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
    const remindersAvailable = state.modulesLoaded && moduleEnabled("notifications");
    const reminderEnabled = remindersAvailable && $("taskReminderEnabled").checked;
    const created = await api.createTask({
      date: k,
      text,
      category: $("taskCategory").value.trim() || null,
      priority: $("taskPriority").value || "NORMAL",
      dueDate: $("taskDueDate").value || null,
      dueTime: $("taskDueTime").value || null,
      reminderEnabled,
      reminderMinutesBefore: reminderEnabled ? Number($("taskReminderBefore").value || 0) : null,
    });
    upsertTaskInMaps(created);
    $("taskText").value = "";
    await loadTaskBoard(true);
    if (typeof invalidateBrowserNotificationSchedule === "function") invalidateBrowserNotificationSchedule();
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
    if (typeof invalidateBrowserNotificationSchedule === "function") invalidateBrowserNotificationSchedule();
    setSave("saved");
    renderTasks();
    renderCalendar();
    return updated;
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
    return null;
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
    if (typeof invalidateBrowserNotificationSchedule === "function") invalidateBrowserNotificationSchedule();
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
    if (typeof invalidateBrowserNotificationSchedule === "function") invalidateBrowserNotificationSchedule();
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
  sel.innerHTML = `<option value="all">${esc(t("все категории"))}</option>`;
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
    state.ui.loadingTasks = true;
    if (!silent) renderTaskBoard();
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
  } finally {
    state.ui.loadingTasks = false;
    renderTaskBoard();
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
  if (state.ui?.loadingTasks) {
    $("taskBoardStatus").textContent = t("загрузка…");
    $("taskBoardStats").innerHTML = "";
    $("taskBoardPager").innerHTML = "";
    renderLoadingState(list, "Загружаю задачи…", 4);
    return;
  }
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
    renderEmptyState(list, {
      icon:"✓",
      title: page.total ? "Ничего не найдено" : "Пустой список",
      text: page.total ? "Попробуй сбросить фильтры или выбрать другой период." : "Выбери день в календаре и добавь первую задачу.",
      variant:"board"
    });
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
$("taskDueDate").addEventListener("change", () => {
  if ($("taskDueDate").value && state.modulesLoaded && moduleEnabled("notifications")) $("taskReminderEnabled").checked = true;
  updateTaskReminderControls();
});
$("taskReminderEnabled")?.addEventListener("change", updateTaskReminderControls);
$("taskEditReminderEnabled")?.addEventListener("change", syncTaskEditorReminder);
$("taskEditForm")?.addEventListener("submit", event => { event.preventDefault(); saveTaskEditor(); });
$("taskEditClose")?.addEventListener("click", closeTaskEditor);
$("taskEditCancel")?.addEventListener("click", closeTaskEditor);
$("taskEditBackdrop")?.addEventListener("click", closeTaskEditor);

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
    b.dataset.shiftTypeId = String(s.id);
    b.className = "chip" + (on ? " on" : "");
    b.setAttribute("aria-pressed", on ? "true" : "false");
    b.style.background = on ? s.color : s.color + "1F";
    b.style.color = on ? "#14171C" : s.color;
    b.style.border = `1px solid ${on ? s.color : s.color + "55"}`;
    b.innerHTML = esc(shiftDisplayName(s)) + (shiftPlannedHours(s) ? ` <span class="h">·${fmtHours(shiftPlannedHours(s))}${state.language === "en" ? "h" : "ч"}</span>` : "");
    const meta = shiftMetaText(s);
    if (meta) b.title = meta;
    b.addEventListener("click", () => toggleShift(s.id));
    box.appendChild(b);
  }
  // Плюсик — отдельный менеджер типов смен, без перегрузки настроек.
  const plus = document.createElement("button");
  plus.className = "chip plus";
  plus.textContent = "+";
  plus.title = t("Создать или настроить смену");
  plus.addEventListener("click", () => openShiftTypeManager());
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
  await flushPendingSave();
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
  if (!k) return;
  // A debounced note save contains a full day snapshot. Flush it first, otherwise
  // its older shiftTypeId can arrive after the deletion and resurrect the shift.
  await flushPendingSave();
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

const daySaveChains = new Map();
const dayLocalRevisions = new Map();

function nextDayLocalRevision(k){
  const revision = Number(dayLocalRevisions.get(k) || 0) + 1;
  dayLocalRevisions.set(k, revision);
  return revision;
}

function applyLocal(k, next){
  const clean = sanitizeDayForModules(next);
  const hasOvertime = Math.abs(clean.overtimeHours) > 0.0001 || Math.abs(clean.timeOffHours) > 0.0001;
  if (!clean.shiftTypeId && !(clean.note || "").trim() && !(clean.dayEmoji || "").trim() && !hasOvertime) delete state.days[k];
  else state.days[k] = clean;
  nextDayLocalRevision(k);
}

function queueDaySave(k, operation){
  const previous = daySaveChains.get(k) || Promise.resolve();
  const current = previous.catch(() => {}).then(operation);
  daySaveChains.set(k, current);
  return current.finally(() => {
    if (daySaveChains.get(k) === current) daySaveChains.delete(k);
  });
}

/*
 * Отправка дня на сервер. Важно: сохраняем снимок данных, а не читаем
 * state.days[k] внутри setTimeout. Иначе можно потерять заметку, если
 * пользователь напечатал текст и сразу переключил месяц.
 */
async function pushDaySnapshot(k, payload){
  const cleanPayload = sanitizeDayForModules(payload);
  const revisionAtQueueTime = Number(dayLocalRevisions.get(k) || 0);
  setSave("saving");
  return queueDaySave(k, async () => {
    try {
      const res = await dataLayer.putDay(k, cleanPayload);
      // Apply the server response only when the user has not edited the same day
      // again while this request was in flight. Writes are serialized per date.
      if (!res.queued && Number(dayLocalRevisions.get(k) || 0) === revisionAtQueueTime) {
        const saved = res.day ? normalizeDay(res.day) : null;
        if (saved) state.days[k] = saved; else delete state.days[k];
        renderCalendar();
        if (state.selected === k) { renderChips(); renderDayEmojiControls(); updateAccSummaries(); }
      }
      setSave("saved");
      if (!res.queued && typeof invalidateBrowserNotificationSchedule === "function") invalidateBrowserNotificationSchedule();
      if (res.queued) updateOfflineStatus();
      return res;
    } catch (err) {
      console.error(err);
      setSave("err", err.message);
      return null;
    }
  });
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
