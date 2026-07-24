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
Object.assign(I18N_EN, {
  "Новая задача":"New task", "Добавить задачу":"Add task", "Создать задачу":"Create task",
  "Разобрать запись в задачу":"Turn Inbox item into a task",
  "Текст уже взят из «Входящих». Проверь дату и при необходимости добавь детали.":"The text comes from Inbox. Check the date and add details when needed.",
  "Достаточно текста и даты. Остальное можно заполнить позже.":"Text and date are enough. Everything else can be added later.",
  "Изменения применятся к существующей задаче.":"Changes will update the existing task.",
  "Тег: максимум 40 символов":"Tag: maximum 40 characters", "Тегов задачи: максимум 10":"A task can have at most 10 tags",
  "напиши текст задачи":"enter the task text", "укажи дату":"choose a date",
  "Задача не найдена":"Task not found", "Задача добавлена":"Task added",
  "Удалить задачу":"Delete task", "Удалить запись":"Delete item", "Удалить запись из входящих?":"Delete this Inbox item?",
  "В задачу":"To task", "Архив":"Archive", "Вернуть":"Restore",
  "входящие":"inbox", "разобрано":"organised", "не разобрано":"unorganised",
  "сохранено на устройстве · ждёт синхронизации":"saved on this device · waiting for sync",
  "Текст записи не должен быть пустым":"Inbox text must not be empty", "Текст записи: максимум 2000 символов":"Inbox text: maximum 2000 characters",
  "Напиши мысль — остальное можно разобрать позже.":"Write the thought now — organise it later.",
  "Сохранено на устройстве":"Saved on this device", "Сохранено во Входящие":"Saved to Inbox",
  "Не удалось сохранить запись":"Failed to save the item", "сохранение…":"saving…", "загрузка…":"loading…",
  "Загружаю входящие…":"Loading Inbox…", "Входящие пусты.":"Inbox is empty.",
  "Сохраняй мысли сразу — структуру можно добавить позже.":"Capture thoughts immediately and organise them later.",
  "Задач на этот день пока нет.":"No tasks for this day yet.",
  "Нажми «Добавить задачу» — дата уже будет подставлена.":"Select Add task — the date will already be filled in.",
  "По фильтрам задач нет.":"No tasks match the filters.", "Сбрось фильтр или выбери другую категорию.":"Clear the filter or choose another category.",
  "Ничего не найдено":"Nothing found", "Задач пока нет":"No tasks yet",
  "Сбрось фильтры или измени период.":"Clear the filters or change the date range.",
  "Нажми «Новая задача» или закинь мысль во «Входящие».":"Select New task or capture a thought in Inbox.",
  "просрочено":"overdue", "выполнено":"done", "открытых":"open", "просроченных":"overdue", "выполненных":"done",
  "Разобрать":"Organise", "Входящие":"Inbox",
  "Новые мысли можно сохранить оффлайн. Разбор, редактирование и удаление входящих требуют связи с сервером.":"New thoughts can be saved offline. Organising, editing and deleting Inbox items requires a server connection.",
  "Дата выбранного дня подставится автоматически.":"The selected day is filled in automatically.",
  "＋ Добавить задачу":"＋ Add task", "＋ Новая задача":"＋ New task",
  "Сначала поймай мысль. Разобрать её в задачу можно позже.":"Capture the thought first. You can turn it into a task later.",
  "открыто":"open", "Быстро записать мысль…":"Capture a thought quickly…",
  "показать разобранные":"show organised", "Обновить":"Refresh",
  "Теги":"Tags", "Дополнительно":"More options", "категория, теги, срок и напоминание":"category, tags, due date and reminder",
  "Что нужно сделать?":"What needs to be done?", "документы, звонки":"documents, calls",
  "Включите модуль «Уведомления», чтобы получать напоминания.":"Enable Notifications to receive reminders.",
  "Записать мысль":"Capture a thought", "Никаких категорий и форм. Просто запиши — разберёшь позже.":"No categories or forms. Write it now and organise it later.",
  "Что пришло в голову?":"What came to mind?", "Сохранить во входящие":"Save to Inbox",
  "Быстрое действие":"Quick action", "Что добавить?":"What would you like to add?",
  "мгновенно во «Входящие»":"instantly to Inbox", "дата и детали":"date and details",
  "начислить часы":"earn hours", "Списать переработку":"Use overtime", "оформить отгул":"record time off",
  "Быстро добавить":"Quick add", "Что нужно запомнить?":"What do you need to remember?",
  "Сохрани как входящее или используй текст как заготовку для действия.":"Save it to Inbox or use the text as a starting point for an action.",
  "Запиши одной строкой…":"Write it in one line…", "Во Входящие":"To Inbox",
  "Enter — во Входящие · Shift+Enter — новая строка":"Enter — save to Inbox · Shift+Enter — new line",
  "Выбери действие — текст будет подставлен автоматически.":"Choose an action — the text will be prefilled automatically.",
  "Выбери нужное действие.":"Choose an action.",
  "или сразу оформить":"or create it now", "текст уже будет подставлен":"the text will be prefilled",
  "Заметка на сегодня":"Today’s note", "открыть или дописать":"open or append",
  "Важный день":"Important date", "дата и повтор":"date and recurrence",
  "Временные записи, которые можно разобрать позже":"Temporary entries you can organise later",
  "Неразобранные записи":"Unorganised entries", "Быстро записать…":"Write something quickly…",
  "Напиши, что нужно запомнить.":"Write what you need to remember.",
  "Запись добавлена в заметку":"Added to today’s note", "Запись сохранена":"Entry saved",
  "Сохраняй записи сразу — структуру можно добавить позже.":"Capture entries immediately and add structure later.",
  "пусто":"empty"
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

/* ─── Задачи, входящие и быстрый захват ─────────────────────── */
function normalizeTaskCategory(value){
  return String(value || "").trim().toLowerCase();
}
function parseTaskTags(value){
  const source = Array.isArray(value) ? value : String(value || "").split(/[,;\n]+/);
  const unique = [];
  const seen = new Set();
  for (const raw of source) {
    const tag = String(raw || "").trim().replace(/^#+/, "").toLowerCase();
    if (!tag || seen.has(tag)) continue;
    if (tag.length > 40) throw new Error(t("Тег: максимум 40 символов"));
    seen.add(tag);
    unique.push(tag);
    if (unique.length > 10) throw new Error(t("Тегов задачи: максимум 10"));
  }
  return unique;
}
function taskEditorMessage(text = "", tone = ""){
  const box = $("taskEditMessage");
  if (!box) return;
  box.textContent = text;
  box.className = "appModalMessage" + (tone ? ` ${tone}` : "");
}
function quickActionMessage(text = "", tone = ""){
  const box = $("quickActionMessage");
  if (!box) return;
  box.textContent = text;
  box.className = "appModalMessage" + (tone ? ` ${tone}` : "");
}
function renderTaskMetadataSuggestions(){
  const categories = allTaskCategories();
  const categoryList = $("taskCategorySuggestions");
  if (categoryList) categoryList.innerHTML = categories.map(value => `<option value="${esc(value)}"></option>`).join("");
  const tagList = $("taskTagSuggestions");
  if (tagList) tagList.innerHTML = allTaskTags().map(value => `<option value="${esc(value)}"></option>`).join("");
  const chips = $("taskSuggestedTags");
  if (chips) {
    chips.innerHTML = "";
    for (const tag of allTaskTags().slice(0, 12)) {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "taskSuggestionChip";
      button.textContent = `#${tag}`;
      button.addEventListener("click", () => {
        let tags = [];
        try { tags = parseTaskTags($("taskEditTags")?.value || ""); } catch (_) {}
        if (!tags.includes(tag)) tags.push(tag);
        if ($("taskEditTags")) $("taskEditTags").value = tags.slice(0, 10).join(", ");
      });
      chips.appendChild(button);
    }
    chips.hidden = chips.childElementCount === 0;
  }
}
async function loadTaskMetadata(silent = true){
  if (!moduleEnabled("tasks")) {
    state.taskMetadata = { categories:[], tags:[] };
    renderTaskMetadataSuggestions();
    return;
  }
  try {
    const metadata = await api.taskMetadata();
    state.taskMetadata = {
      categories:Array.isArray(metadata?.categories) ? metadata.categories : [],
      tags:Array.isArray(metadata?.tags) ? metadata.tags : [],
    };
    renderTaskMetadataSuggestions();
    renderTaskCategoryFilter();
    renderTaskBoardCategoryFilter();
  } catch (err) {
    console.error(err);
    if (!silent) setSave("err", err.message);
  }
}
function updateTaskReminderControls(){
  const enabled = !!$("taskEditReminderEnabled")?.checked;
  const available = state.modulesLoaded && moduleEnabled("notifications");
  if ($("taskEditReminderEnabled")) {
    $("taskEditReminderEnabled").disabled = !available;
    if (!available) $("taskEditReminderEnabled").checked = false;
  }
  if ($("taskEditReminderBefore")) $("taskEditReminderBefore").disabled = !available || !enabled;
  if ($("taskEditReminderBeforeLabel")) $("taskEditReminderBeforeLabel").hidden = !available || !enabled;
  if ($("taskEditReminderHint")) $("taskEditReminderHint").hidden = available;
}
function resetTaskEditorFields({ date = null, text = "", inboxId = null } = {}){
  state.editingTaskId = null;
  state.editingTaskMode = "create";
  state.editingTaskInboxId = inboxId == null ? null : Number(inboxId);
  $("taskEditText").value = text || "";
  $("taskEditDate").value = date || state.selected || todayKey();
  $("taskEditCategory").value = "";
  $("taskEditTags").value = "";
  $("taskEditPriority").value = "NORMAL";
  $("taskEditDueDate").value = "";
  $("taskEditDueTime").value = "";
  $("taskEditReminderEnabled").checked = false;
  $("taskEditReminderBefore").value = "60";
  $("taskEditAdvanced").open = false;
  $("taskEditTitle").textContent = inboxId ? t("Разобрать запись в задачу") : t("Новая задача");
  $("taskEditHint").textContent = inboxId
    ? t("Текст уже взят из «Входящих». Проверь дату и при необходимости добавь детали.")
    : t("Достаточно текста и даты. Остальное можно заполнить позже.");
  $("taskEditSave").textContent = inboxId ? t("Создать задачу") : t("Добавить задачу");
  taskEditorMessage();
  updateTaskReminderControls();
  renderTaskMetadataSuggestions();
}
function openTaskCreate(options = {}){
  if (!moduleEnabled("tasks")) return setSave("err", t("модуль выключен"));
  resetTaskEditorFields(options);
  openAppModal("taskEditModal", "taskEditText");
}
function closeTaskEditor(){
  state.editingTaskId = null;
  state.editingTaskMode = "create";
  state.editingTaskInboxId = null;
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
  state.editingTaskMode = "edit";
  state.editingTaskInboxId = null;
  $("taskEditText").value = task.text || "";
  $("taskEditDate").value = task.date || state.selected || todayKey();
  $("taskEditCategory").value = task.category || "";
  $("taskEditTags").value = (task.tags || []).join(", ");
  $("taskEditPriority").value = task.priority || "NORMAL";
  $("taskEditDueDate").value = task.dueDate || "";
  $("taskEditDueTime").value = task.dueTime || "";
  $("taskEditReminderEnabled").checked = !!task.reminderEnabled;
  $("taskEditReminderBefore").value = String(task.reminderMinutesBefore ?? 60);
  $("taskEditAdvanced").open = !!(task.category || (task.tags || []).length || task.priority !== "NORMAL" || task.dueDate || task.dueTime || task.reminderEnabled);
  $("taskEditTitle").textContent = t("Редактировать задачу");
  $("taskEditHint").textContent = t("Изменения применятся к существующей задаче.");
  $("taskEditSave").textContent = t("Сохранить");
  taskEditorMessage();
  updateTaskReminderControls();
  renderTaskMetadataSuggestions();
  openAppModal("taskEditModal", "taskEditText");
}
function taskEditorPayload(original = null){
  const text = $("taskEditText").value.trim();
  const date = $("taskEditDate").value;
  if (!text) throw new Error(t("напиши текст задачи"));
  if (!date) throw new Error(t("укажи дату"));
  const tags = parseTaskTags($("taskEditTags").value);
  const remindersAvailable = state.modulesLoaded && moduleEnabled("notifications");
  const reminderEnabled = remindersAvailable ? !!$("taskEditReminderEnabled").checked : !!original?.reminderEnabled;
  const reminderMinutesBefore = remindersAvailable
    ? (reminderEnabled ? Number($("taskEditReminderBefore").value || 0) : null)
    : (original?.reminderMinutesBefore ?? null);
  if (reminderEnabled && (!Number.isInteger(reminderMinutesBefore) || reminderMinutesBefore < 0 || reminderMinutesBefore > 10080)) {
    throw new Error(t("напоминание: от 0 до 10080 минут"));
  }
  return {
    date,
    text,
    category:normalizeTaskCategory($("taskEditCategory").value),
    tags,
    priority:$("taskEditPriority").value || "NORMAL",
    dueDate:$("taskEditDueDate").value || "",
    dueTime:$("taskEditDueTime").value || "",
    reminderEnabled,
    reminderMinutesBefore,
  };
}
async function saveTaskEditor(){
  const mode = state.editingTaskMode || "create";
  const id = Number(state.editingTaskId);
  const original = mode === "edit" ? taskById(id) : null;
  let payload;
  try { payload = taskEditorPayload(original); }
  catch (err) { return taskEditorMessage(err.message, "err"); }

  $("taskEditSave").disabled = true;
  taskEditorMessage(t("сохранение…"));
  setSave("saving");
  try {
    let saved;
    if (mode === "edit") {
      if (!id) throw new Error(t("Задача не найдена"));
      saved = await api.updateTask(id, payload);
    } else if (state.editingTaskInboxId) {
      const { text, ...conversion } = payload;
      const result = await api.convertInboxToTask(state.editingTaskInboxId, conversion);
      saved = result.task;
    } else {
      saved = await api.createTask(payload);
    }
    upsertTaskInMaps(saved);
    await Promise.all([loadTaskBoard(true), loadTaskMetadata(true), loadInbox(true)]);
    if (typeof invalidateBrowserNotificationSchedule === "function") invalidateBrowserNotificationSchedule();
    renderTasks();
    renderTaskBoard();
    renderCalendar();
    closeTaskEditor();
    setSave("saved", mode === "edit" ? t("Задача обновлена") : t("Задача добавлена"));
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
    taskEditorMessage(err.message || t("Не удалось сохранить задачу"), "err");
  } finally {
    $("taskEditSave").disabled = false;
  }
}

function removeTaskFromMaps(id){
  for (const k of Object.keys(state.tasksByDate)) {
    state.tasksByDate[k] = state.tasksByDate[k].filter(task => Number(task.id) !== Number(id));
    if (!state.tasksByDate[k].length) delete state.tasksByDate[k];
  }
}
function upsertTaskInMaps(task){
  if (!task) return;
  removeTaskFromMaps(task.id);
  addToDateMap(state.tasksByDate, task);
}
async function updateTaskDetails(id, patch){
  setSave("saving");
  try {
    const updated = await api.updateTask(id, patch);
    upsertTaskInMaps(updated);
    await Promise.all([loadTaskBoard(true), loadTaskMetadata(true)]);
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
  const oldTask = taskById(id);
  if (oldTask) upsertTaskInMaps({ ...oldTask, done:!!done });
  state.taskBoard.items = (state.taskBoard.items || []).map(task => Number(task.id) === Number(id) ? { ...task, done:!!done } : task);
  renderTasks();
  renderTaskBoard();
  renderCalendar();
  try {
    const result = await dataLayer.setTaskDone(id, done);
    if (result.task) upsertTaskInMaps(result.task);
    if (!result.queued) await loadTaskBoard(true);
    if (typeof invalidateBrowserNotificationSchedule === "function") invalidateBrowserNotificationSchedule();
    setSave("saved");
    renderTasks();
    renderTaskBoard();
    renderCalendar();
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
    if (oldTask) upsertTaskInMaps(oldTask);
    await loadMonth();
    renderTasks();
    renderTaskBoard();
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
    state.taskBoard.items = (state.taskBoard.items || []).filter(task => Number(task.id) !== Number(id));
    await Promise.all([loadTaskBoard(true), loadTaskMetadata(true)]);
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

function renderTaskCategoryFilter(){
  const sel = $("taskCategoryFilter");
  if (!sel) return;
  const current = sel.value || state.taskFilters.category || "all";
  sel.innerHTML = `<option value="all">${esc(t("все категории"))}</option>`;
  for (const category of allTaskCategories()) {
    const option = document.createElement("option");
    option.value = category;
    option.textContent = category;
    sel.appendChild(option);
  }
  sel.value = [...sel.options].some(option => option.value === current) ? current : "all";
  state.taskFilters.category = sel.value;
}
function filteredTasksForSelected(){
  const items = tasksOf(state.selected);
  const status = state.taskFilters.status || "all";
  const category = state.taskFilters.category || "all";
  return items.filter(task => {
    if (category !== "all" && (task.category || "") !== category) return false;
    if (status === "open" && task.done) return false;
    if (status === "done" && !task.done) return false;
    if (status === "overdue" && (!task.overdue || task.done)) return false;
    return true;
  });
}
function buildTaskMeta(task){
  const meta = document.createElement("div");
  meta.className = "taskMeta";
  if (task.category) {
    const badge = document.createElement("span");
    badge.className = "taskBadge cat";
    badge.textContent = task.category;
    meta.appendChild(badge);
  }
  for (const tag of task.tags || []) {
    const badge = document.createElement("span");
    badge.className = "taskBadge tag";
    badge.textContent = `#${tag}`;
    meta.appendChild(badge);
  }
  if (task.priority && task.priority !== "NORMAL") {
    const badge = document.createElement("span");
    badge.className = "taskBadge " + task.priority.toLowerCase();
    badge.textContent = taskPriorityLabel(task.priority);
    meta.appendChild(badge);
  }
  const due = taskDueLabel(task);
  if (due) {
    const badge = document.createElement("span");
    badge.className = "taskBadge";
    badge.textContent = due;
    meta.appendChild(badge);
  }
  if (task.reminderEnabled) {
    const badge = document.createElement("span");
    badge.className = "taskBadge";
    badge.textContent = `🔔 ${task.reminderMinutesBefore ?? 0}м`;
    meta.appendChild(badge);
  }
  if (task.overdue && !task.done) {
    const badge = document.createElement("span");
    badge.className = "taskBadge overdue";
    badge.textContent = t("просрочено");
    meta.appendChild(badge);
  }
  if (task.done) {
    const badge = document.createElement("span");
    badge.className = "taskBadge";
    badge.textContent = t("выполнено");
    meta.appendChild(badge);
  }
  return meta;
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
    renderEmptyState(box, {
      icon:"✓",
      title:"Задач на этот день пока нет.",
      text:"Нажми «Добавить задачу» — дата уже будет подставлена.",
      variant:"compact"
    });
    updateAccSummaries();
    return;
  }
  if (!items.length) {
    renderEmptyState(box, { icon:"⌕", title:"По фильтрам задач нет.", text:"Сбрось фильтр или выбери другую категорию.", variant:"compact" });
    updateAccSummaries();
    return;
  }
  for (const task of items) {
    const row = document.createElement("div");
    row.dataset.taskId = String(task.id);
    row.className = "taskItem" + (task.done ? " done" : "") + (task.overdue && !task.done ? " overdue" : "");
    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.checked = !!task.done;
    checkbox.addEventListener("change", () => toggleTask(task.id, checkbox.checked));
    const body = document.createElement("button");
    body.type = "button";
    body.className = "taskItemBody";
    const text = document.createElement("span");
    text.className = "taskText";
    text.textContent = task.text;
    body.append(text, buildTaskMeta(task));
    body.addEventListener("click", () => editTask(task));
    const remove = document.createElement("button");
    remove.className = "tinyDel";
    remove.type = "button";
    remove.textContent = "×";
    remove.title = t("Удалить задачу");
    remove.addEventListener("click", () => removeTask(task.id));
    row.append(checkbox, body, remove);
    box.appendChild(row);
  }
  updateAccSummaries();
}

/* ─── Входящие ─────────────────────────────────────────────── */
function queuedInboxView(queue){
  return (queue || [])
    .filter(item => item.type === "captureInbox")
    .map(item => ({
      id:`local-${item.payload?.clientOperationId || item.id}`,
      text:item.payload?.text || "",
      status:"OPEN",
      createdAt:item.createdAt,
      localOnly:true,
      clientOperationId:item.payload?.clientOperationId || null,
    }));
}
async function loadInbox(silent = true){
  if (!moduleEnabled("tasks")) {
    state.inbox.items = [];
    renderInbox();
    return;
  }
  state.inbox.loading = true;
  if (!silent) renderInbox();
  try {
    const queued = queuedInboxView(await dataLayer.getQueueItems());
    let remote = (state.inbox.items || []).filter(item => !item.localOnly);
    if (navigator.onLine) {
      remote = await api.inbox(state.inbox.includeArchived ? "all" : "open");
    }
    const queuedIds = new Set(queued.map(item => item.clientOperationId).filter(Boolean));
    remote = remote.filter(item => !queuedIds.has(item.clientOperationId));
    state.inbox.items = [...queued, ...remote];
  } catch (err) {
    console.error(err);
    if (!silent) setSave("err", err.message);
  } finally {
    state.inbox.loading = false;
    renderInbox();
  }
}
function inboxTimeLabel(value){
  if (!value) return "";
  try { return new Intl.DateTimeFormat(currentLocale(), { dateStyle:"short", timeStyle:"short" }).format(new Date(value)); }
  catch (_) { return String(value).slice(0, 16).replace("T", " "); }
}
function renderInbox(){
  const list = $("inboxList");
  if (!list) return;
  const items = state.inbox.items || [];
  const openCount = items.filter(item => item.status === "OPEN").length;
  const tray = $("taskInboxCard");
  tray?.classList.toggle("empty", openCount === 0);
  if ($("inboxCount")) $("inboxCount").textContent = String(openCount);
  if ($("inboxStatus")) $("inboxStatus").textContent = state.inbox.loading
    ? t("загрузка…")
    : (openCount ? `${openCount} ${t("не разобрано")}` : t("пусто"));
  if (state.inbox.loading && !items.length) {
    renderLoadingState(list, "Загружаю входящие…", 2);
    return;
  }
  list.innerHTML = "";
  if (!items.length) {
    renderEmptyState(list, {
      icon:"↘",
      title:"Входящие пусты.",
      text:"Сохраняй записи сразу — структуру можно добавить позже.",
      variant:"compact"
    });
    return;
  }
  for (const item of items) {
    const row = document.createElement("article");
    row.className = "inboxItem" + (item.status === "ARCHIVED" ? " archived" : "") + (item.localOnly ? " local" : "");
    const body = document.createElement("div");
    body.className = "inboxItemBody";
    const text = document.createElement("div");
    text.className = "inboxItemText";
    text.textContent = item.text;
    const meta = document.createElement("div");
    meta.className = "inboxItemMeta";
    meta.textContent = item.localOnly
      ? t("сохранено на устройстве · ждёт синхронизации")
      : `${item.status === "ARCHIVED" ? t("разобрано") : t("входящие")} · ${inboxTimeLabel(item.createdAt)}`;
    body.append(text, meta);
    const actions = document.createElement("div");
    actions.className = "inboxItemActions";
    if (!item.localOnly && item.status === "OPEN") {
      const convert = document.createElement("button");
      convert.type = "button";
      convert.className = "primary";
      convert.textContent = t("В задачу");
      convert.addEventListener("click", () => openTaskCreate({ text:item.text, inboxId:item.id, date:state.selected || todayKey() }));
      const archive = document.createElement("button");
      archive.type = "button";
      archive.textContent = t("Архив");
      archive.addEventListener("click", () => setInboxArchived(item.id, true));
      actions.append(convert, archive);
    } else if (!item.localOnly && item.status === "ARCHIVED") {
      const restore = document.createElement("button");
      restore.type = "button";
      restore.textContent = t("Вернуть");
      restore.addEventListener("click", () => setInboxArchived(item.id, false));
      actions.appendChild(restore);
    }
    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "dangerGhost";
    remove.textContent = "×";
    remove.title = t("Удалить запись");
    remove.addEventListener("click", () => removeInboxItem(item));
    actions.appendChild(remove);
    row.append(body, actions);
    list.appendChild(row);
  }
}
async function captureInbox(text){
  const clean = String(text || "").trim();
  if (!clean) throw new Error(t("Текст записи не должен быть пустым"));
  const result = await dataLayer.captureInbox(clean);
  if (result.item) state.inbox.items = [result.item, ...(state.inbox.items || []).filter(item => item.id !== result.item.id)];
  await loadInbox(true);
  setSave("saved", result.queued ? t("Сохранено на устройстве") : t("Запись сохранена"));
  return result;
}
function quickActionDraftText(){
  return String($("quickActionText")?.value || "").trim();
}
async function saveQuickActionInbox(){
  if (!moduleEnabled("tasks")) return setSave("err", t("модуль выключен"));
  const text = quickActionDraftText();
  if (!text) return quickActionMessage(t("Напиши, что нужно запомнить."), "err");
  const button = $("quickActionInbox");
  if (button) button.disabled = true;
  quickActionMessage(t("сохранение…"));
  try {
    await captureInbox(text);
    if ($("quickActionText")) $("quickActionText").value = "";
    closeQuickActions();
  } catch (err) {
    console.error(err);
    quickActionMessage(err.message || t("Не удалось сохранить запись"), "err");
  } finally {
    if (button) button.disabled = false;
  }
}
async function setInboxArchived(id, archived){
  setSave("saving");
  try {
    await api.updateInbox(id, { archived:!!archived });
    await loadInbox(true);
    setSave("saved");
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}
async function removeInboxItem(item){
  if (!confirm(t("Удалить запись из входящих?"))) return;
  try {
    if (item.localOnly) await dataLayer.discardQueuedInbox(item.clientOperationId);
    else await api.deleteInbox(item.id);
    state.inbox.items = (state.inbox.items || []).filter(existing => existing.id !== item.id);
    await loadInbox(true);
    setSave("saved");
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}

/* ─── Общий экран задач ─────────────────────────────────────── */
function renderTaskBoardCategoryFilter(){
  const select = $("taskBoardCategory");
  if (!select) return;
  const current = select.value || state.taskBoard.filters.category || "all";
  select.innerHTML = `<option value="all">${esc(t("все категории"))}</option>`;
  for (const category of allTaskCategories()) {
    const option = document.createElement("option");
    option.value = category;
    option.textContent = category;
    select.appendChild(option);
  }
  select.value = [...select.options].some(option => option.value === current) ? current : "all";
  state.taskBoard.filters.category = select.value;
}
function syncTaskBoardFiltersToInputs(){
  const filters = state.taskBoard.filters;
  if ($("taskBoardStatusFilter")) $("taskBoardStatusFilter").value = filters.status || "open";
  if ($("taskBoardPriority")) $("taskBoardPriority").value = filters.priority || "all";
  if ($("taskBoardSearch")) $("taskBoardSearch").value = filters.q || "";
  if ($("taskBoardFrom")) $("taskBoardFrom").value = filters.from || "";
  if ($("taskBoardTo")) $("taskBoardTo").value = filters.to || "";
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
    const filters = state.taskBoard.filters;
    const page = state.taskBoard.page || { page:0, size:50 };
    const query = {
      status:filters.status || "open",
      category:filters.category !== "all" ? filters.category : "",
      priority:filters.priority !== "all" ? filters.priority : "",
      q:filters.q || "",
      from:filters.from || "",
      to:filters.to || "",
      page:page.page || 0,
      size:page.size || 50,
    };
    const response = normalizePageResponse(await api.taskBoard(query), page.size || 50);
    state.taskBoard.items = response.items;
    state.taskBoard.page = response;
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
  const open = items.filter(task => !task.done).length;
  const overdue = items.filter(task => task.overdue && !task.done).length;
  const done = items.filter(task => task.done).length;
  $("taskBoardStatus").textContent = pageRangeText(page);
  $("taskBoardStats").innerHTML = `
    <span class="pill">${t("всего")} <b>${page.total || items.length}</b></span>
    <span class="pill">${t("открытых")} <b>${open}</b></span>
    <span class="pill">${t("просроченных")} <b>${overdue}</b></span>
    <span class="pill">${t("выполненных")} <b>${done}</b></span>`;
  renderPager("taskBoardPager", page,
    nextPage => { state.taskBoard.page.page = nextPage; loadTaskBoard(false); },
    nextSize => { state.taskBoard.page.size = nextSize; resetTaskBoardPage(); loadTaskBoard(false); });
  list.innerHTML = "";
  if (!items.length) {
    renderEmptyState(list, {
      icon:"✓",
      title:page.total ? "Ничего не найдено" : "Задач пока нет",
      text:page.total ? "Сбрось фильтры или измени период." : "Нажми «Новая задача» или закинь мысль во «Входящие».",
      variant:"board"
    });
    return;
  }
  for (const task of items) {
    const row = document.createElement("div");
    row.className = "taskBoardItem" + (task.done ? " done" : "") + (task.overdue && !task.done ? " overdue" : "");
    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.checked = !!task.done;
    checkbox.addEventListener("change", () => toggleTask(task.id, checkbox.checked));
    const date = document.createElement("button");
    date.type = "button";
    date.className = "taskBoardDate";
    date.textContent = taskBoardDateLabel(task);
    date.title = `${t("Открыть день")} ${task.date}`;
    date.addEventListener("click", () => goToTaskDate(task.date));
    const body = document.createElement("button");
    body.type = "button";
    body.className = "taskBoardBody";
    const text = document.createElement("div");
    text.className = "taskText";
    text.textContent = task.text;
    body.append(text, buildTaskMeta(task));
    body.addEventListener("click", () => editTask(task));
    const actions = document.createElement("div");
    actions.className = "taskBoardActions";
    const remove = document.createElement("button");
    remove.type = "button";
    remove.textContent = "×";
    remove.title = t("Удалить задачу");
    remove.addEventListener("click", () => removeTask(task.id));
    actions.appendChild(remove);
    row.append(checkbox, date, body, actions);
    list.appendChild(row);
  }
}
async function goToTaskDate(date){
  if (!date) return;
  const [year, month] = date.split("-").map(Number);
  if (state.y !== year || state.m !== month - 1) {
    state.y = year;
    state.m = month - 1;
    await loadMonth();
  }
  selectDay(date);
}
function setTaskBoardQuickStatus(status){
  state.taskBoard.filters.status = status;
  resetTaskBoardPage();
  loadTaskBoard(false);
}

/* ─── Глобальные быстрые действия ───────────────────────────── */
function hasQuickDraftAction(){
  return moduleEnabled("tasks") || moduleEnabled("notes") || moduleEnabled("important_dates");
}
function firstQuickActionFocus(){
  if (hasQuickDraftAction()) return "quickActionText";
  return "quickActionCredit";
}
function openQuickActions(){
  const hasDraft = hasQuickDraftAction();
  const canInbox = moduleEnabled("tasks");
  quickActionMessage();
  if ($("quickActionText")) $("quickActionText").value = "";
  if ($("quickActionsTitle")) $("quickActionsTitle").textContent = t(hasDraft ? "Что нужно запомнить?" : "Что добавить?");
  if ($("quickActionsHint")) $("quickActionsHint").textContent = t(
    canInbox
      ? "Сохрани как входящее или используй текст как заготовку для действия."
      : (hasDraft ? "Выбери действие — текст будет подставлен автоматически." : "Выбери нужное действие.")
  );
  if ($("quickActionKeyHint")) $("quickActionKeyHint").textContent = t(
    canInbox ? "Enter — во Входящие · Shift+Enter — новая строка" : "Выбери действие — текст будет подставлен автоматически."
  );
  openAppModal("quickActionsModal", firstQuickActionFocus());
}
function closeQuickActions(){
  quickActionMessage();
  closeAppModal("quickActionsModal");
}
function quickActionTask(){
  const text = quickActionDraftText();
  closeQuickActions();
  openTaskCreate({ text, date:state.selected || todayKey() });
}
async function quickActionNote(){
  if (!moduleEnabled("notes")) return setSave("err", t("модуль выключен"));
  const text = quickActionDraftText();
  const date = state.selected || todayKey();
  const [year, month] = date.split("-").map(Number);
  closeQuickActions();
  await goto(year, month - 1);
  location.hash = "#calendar";
  setTimeout(() => {
    selectDay(date);
    const section = $("accNote");
    if (section) section.open = true;
    setTab("edit");
    const editor = $("noteEdit");
    if (!editor) return;
    if (text) {
      const existing = editor.value.trimEnd();
      editor.value = existing ? `${existing}
${text}` : text;
      editor.dispatchEvent(new Event("input", { bubbles:true }));
      setSave("saved", t("Запись добавлена в заметку"));
    }
    editor.focus({ preventScroll:true });
    editor.setSelectionRange(editor.value.length, editor.value.length);
  }, 0);
}
function quickActionImportant(){
  if (!moduleEnabled("important_dates")) return setSave("err", t("модуль выключен"));
  const text = quickActionDraftText();
  const date = state.selected || todayKey();
  closeQuickActions();
  resetImportantBoardForm();
  location.hash = "#important";
  setTimeout(() => {
    if ($("importantBoardTitle")) $("importantBoardTitle").value = text;
    if ($("importantBoardDate")) $("importantBoardDate").value = date;
    $("importantBoardTitle")?.focus({ preventScroll:true });
  }, 0);
}
function quickActionOvertime(kind){
  closeQuickActions();
  if (!moduleEnabled("overtime")) return setSave("err", t("модуль выключен"));
  if (kind === "usage") openOvertimeUsageModal(state.selected || todayKey());
  else openOvertimeCreditModal(state.selected || todayKey());
}

$("taskCreateForDay")?.addEventListener("click", () => openTaskCreate({ date:state.selected || todayKey() }));
$("taskBoardCreate")?.addEventListener("click", () => openTaskCreate({ date:state.selected || todayKey() }));
$("taskStatusFilter")?.addEventListener("change", () => { state.taskFilters.status = $("taskStatusFilter").value; renderTasks(); });
$("taskCategoryFilter")?.addEventListener("change", () => { state.taskFilters.category = $("taskCategoryFilter").value; renderTasks(); });
$("taskEditCategory")?.addEventListener("blur", () => { $("taskEditCategory").value = normalizeTaskCategory($("taskEditCategory").value); });
$("taskEditDueDate")?.addEventListener("change", () => {
  if ($("taskEditDueDate").value && state.modulesLoaded && moduleEnabled("notifications")) $("taskEditReminderEnabled").checked = true;
  updateTaskReminderControls();
});
$("taskEditReminderEnabled")?.addEventListener("change", updateTaskReminderControls);
$("taskEditForm")?.addEventListener("submit", event => { event.preventDefault(); saveTaskEditor(); });
$("taskEditClose")?.addEventListener("click", closeTaskEditor);
$("taskEditCancel")?.addEventListener("click", closeTaskEditor);
$("taskEditBackdrop")?.addEventListener("click", closeTaskEditor);

$("quickActionInbox")?.addEventListener("click", saveQuickActionInbox);
$("quickActionText")?.addEventListener("keydown", event => {
  if (moduleEnabled("tasks") && event.key === "Enter" && !event.shiftKey) {
    event.preventDefault();
    saveQuickActionInbox();
  }
});
$("inboxQuickSave")?.addEventListener("click", async () => {
  const input = $("inboxQuickText");
  const text = input?.value || "";
  try {
    await captureInbox(text);
    if (input) input.value = "";
    const tray = $("taskInboxCard");
    if (tray) tray.open = true;
  } catch (err) { setSave("err", err.message); input?.focus(); }
});
$("inboxQuickText")?.addEventListener("keydown", event => {
  if (event.key === "Enter" && !event.shiftKey) { event.preventDefault(); $("inboxQuickSave")?.click(); }
});
$("inboxShowArchived")?.addEventListener("change", () => {
  state.inbox.includeArchived = !!$("inboxShowArchived").checked;
  loadInbox(false);
});
$("inboxRefresh")?.addEventListener("click", () => loadInbox(false));

$("taskBoardOpen")?.addEventListener("click", () => setTaskBoardQuickStatus("open"));
$("taskBoardOverdue")?.addEventListener("click", () => setTaskBoardQuickStatus("overdue"));
$("taskBoardAll")?.addEventListener("click", () => setTaskBoardQuickStatus("all"));
$("taskBoardThisMonth")?.addEventListener("click", () => { const range = monthFromTo(); state.taskBoard.filters.from = range.from; state.taskBoard.filters.to = range.to; resetTaskBoardPage(); loadTaskBoard(false); });
$("taskBoardClear")?.addEventListener("click", () => { state.taskBoard.filters = { status:"open", category:"all", priority:"all", q:"", from:"", to:"" }; resetTaskBoardPage(); loadTaskBoard(false); });
$("taskBoardStatusFilter")?.addEventListener("change", () => { state.taskBoard.filters.status = $("taskBoardStatusFilter").value; resetTaskBoardPage(); loadTaskBoard(false); });
$("taskBoardCategory")?.addEventListener("change", () => { state.taskBoard.filters.category = $("taskBoardCategory").value; resetTaskBoardPage(); loadTaskBoard(false); });
$("taskBoardPriority")?.addEventListener("change", () => { state.taskBoard.filters.priority = $("taskBoardPriority").value; resetTaskBoardPage(); loadTaskBoard(false); });
$("taskBoardFrom")?.addEventListener("change", () => { state.taskBoard.filters.from = $("taskBoardFrom").value; resetTaskBoardPage(); loadTaskBoard(false); });
$("taskBoardTo")?.addEventListener("change", () => { state.taskBoard.filters.to = $("taskBoardTo").value; resetTaskBoardPage(); loadTaskBoard(false); });
$("taskBoardSearch")?.addEventListener("input", () => {
  clearTimeout(window.__taskBoardTimer);
  window.__taskBoardTimer = setTimeout(() => {
    state.taskBoard.filters.q = $("taskBoardSearch").value.trim();
    resetTaskBoardPage();
    loadTaskBoard(true);
  }, 350);
});

$("globalQuickAdd")?.addEventListener("click", openQuickActions);
$("quickActionsClose")?.addEventListener("click", closeQuickActions);
$("quickActionsBackdrop")?.addEventListener("click", closeQuickActions);
$("quickActionTask")?.addEventListener("click", quickActionTask);
$("quickActionNote")?.addEventListener("click", quickActionNote);
$("quickActionImportant")?.addEventListener("click", quickActionImportant);
$("quickActionCredit")?.addEventListener("click", () => quickActionOvertime("credit"));
$("quickActionUsage")?.addEventListener("click", () => quickActionOvertime("usage"));


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
