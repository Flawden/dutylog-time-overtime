/*
 * 50-tasks.js — Tasks and important dates: day-panel sections and board UI
 *
 * DutyLog uses ordered browser scripts, not ES modules yet.
 * Keep the order in index.html stable: 10-core → 20-data → 30-calendar
 * → 35-today → 37-calendar-experience → 40-overtime → 50-tasks → 60-settings → 70-user-boot.
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
  "Теги":"Tags", "Дополнительно":"More options", "описание, категория, теги, срок и напоминание":"description, category, tags, due date and reminder",
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
Object.assign(I18N_EN, {
  "Подзадачи":"Subtasks", "Подзадача":"Subtask", "подзадач":"subtasks", "необязательно":"optional",
  "＋ Добавить подзадачу":"＋ Add subtask", "Что нужно сделать сначала?":"What needs to happen first?",
  "Переместить выше":"Move up", "Переместить ниже":"Move down", "Удалить подзадачу":"Delete subtask",
  "Текст подзадачи: максимум 300 символов":"Subtask text: maximum 300 characters",
  "Подзадач: максимум 50":"A task can have at most 50 subtasks",
  "У задачи есть незавершённые подзадачи. Отметить выполненными и задачу, и все подзадачи?":"This task has unfinished subtasks. Complete the task and every subtask?",
  "Подзадачи можно отмечать только при подключении к серверу.":"Subtasks can only be checked while connected to the server.",
  "Не удалось обновить подзадачу":"Failed to update subtask",
  "Один уровень вложенности. Enter добавляет следующую строку, стрелки меняют порядок.":"One level only. Enter adds the next row; arrows change the order.",
  "выполнено подзадач":"subtasks completed",
  "Срок подзадачи":"Subtask due date", "Без срока":"No due date",
  "Срок не может быть раньше времени задачи.":"The due date cannot be earlier than the task date.",
  "Срок подзадачи не может быть раньше даты задачи.":"A subtask due date cannot be earlier than the task date.",
  "Выполненные":"Completed",
  "Детали задачи":"Task details", "Описание":"Description", "Описание пока не добавлено.":"No description yet.",
  "Открытая задача":"Open task", "Задача выполнена":"Task completed", "Задача просрочена":"Task overdue",
  "Дата задачи":"Task date", "Напоминание":"Reminder", "минут до срока":"minutes before due time",
  "Исходный срок":"Source deadline",
  "Вернуть в открытые":"Reopen", "Выполнить":"Complete",
  "Контекст, ссылки, договорённости…":"Context, links, agreements…",
  "Описание задачи: максимум 4000 символов":"Task description: maximum 4000 characters"
});
Object.assign(I18N_RU, Object.fromEntries(Object.entries(I18N_EN).map(([ru,en]) => [en, ru])));

Object.assign(I18N_EN, {
  "Проект":"Project", "все проекты":"all projects", "Запланировано":"Planned",
  "Весь день":"All day", "Дата начала":"Start date", "Время начала":"Start time",
  "Дата окончания":"End date", "Время окончания":"End time", "Длительность, минут":"Duration, minutes",
  "Интервал не является дедлайном":"The planned interval is not a deadline",
  "Точечная задача":"Point task", "Длительность":"Duration", "мин":"min",
  "Дедлайн":"Deadline", "Исходный интервал":"Source interval",
  "Укажите время начала или включите «Весь день».":"Choose a start time or enable All day.",
  "Дата и время окончания должны быть заполнены вместе.":"End date and end time must be filled together.",
  "Окончание должно быть позже начала.":"The end must be later than the start.",
  "Длительность: от 1 минуты до 7 дней.":"Duration must be between 1 minute and 7 days.",
  "Дедлайн не может быть раньше окончания запланированного интервала.":"The deadline cannot be earlier than the planned end.",
  "Дедлайн не может быть раньше начала задачи.":"The deadline cannot be earlier than the planned start.",
  "поиск во входящих…":"search Inbox…", "По запросу во входящих ничего не найдено.":"No Inbox items match the search.",
  "Очисти поиск или измени запрос.":"Clear the search or change the query.",
  "описание, проект, категория, теги, дедлайн и напоминание":"description, project, category, tags, deadline and reminder"
});
Object.assign(I18N_RU, Object.fromEntries(Object.entries(I18N_EN).map(([ru,en]) => [en, ru])));

/* ─── Notes & Important Events Next ─────────────────────────── */
Object.assign(I18N_EN, {
  "Важная дата":"Important date", "Событие":"Event", "Период":"Period",
  "Новое событие":"New event", "Редактировать событие":"Edit event",
  "Весь день":"All day", "Место":"Place", "Категория":"Category",
  "Описание":"Description", "Напоминания":"Reminders", "без напоминаний":"no reminders",
  "Открыть в календаре":"Open in calendar", "поиск по заметкам…":"search notes…",
  "По заметкам ничего не найдено.":"No notes found.", "Оффлайн-правка поставлена в очередь":"Offline edit queued",
  "Редактирование доступно оффлайн и синхронизируется позже.":"Editing works offline and syncs later."
});
Object.assign(I18N_RU, Object.fromEntries(Object.entries(I18N_EN).map(([ru,en]) => [en, ru])));

function normalizeImportantEvent(item){
  const eventType = item?.eventType || "IMPORTANT_DATE";
  const allDay = eventType === "IMPORTANT_DATE" ? true : item?.allDay !== false;
  return {
    ...item,
    eventType,
    allDay,
    startDate:item?.startDate || item?.date || "",
    endDate:item?.endDate || item?.startDate || item?.date || "",
    reminders:Array.isArray(item?.reminders) ? item.reminders.map(Number).filter(Number.isFinite) : []
  };
}

async function refreshImportantSettings(){
  if (!moduleEnabled("important_dates")) {
    state.importantDays = [];
    renderImportantBoard();
    return;
  }
  try {
    state.importantDays = (await api.importantDays()).map(normalizeImportantEvent);
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
  item = normalizeImportantEvent(item);
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
function importantTypeLabel(type){
  return t({ IMPORTANT_DATE:"Важная дата", EVENT:"Событие", PERIOD:"Период" }[type] || "Важная дата");
}
function importantReminderLabel(minutes){
  const n = Number(minutes);
  if (n === 0) return t("в момент начала");
  if (n < 60) return `${n} ${t("мин")}`;
  if (n % 10080 === 0) return `${n / 10080} ${t("нед.")}`;
  if (n % 1440 === 0) return `${n / 1440} ${t("дн.")}`;
  return `${n / 60} ${t("ч")}`;
}
function importantScheduleLabel(item){
  item = normalizeImportantEvent(item);
  const start = item.startDate || item.date;
  const end = item.endDate || start;
  if (item.allDay) {
    return start === end ? formatDateHuman(start) : `${formatDateHuman(start)} — ${formatDateHuman(end)}`;
  }
  const dates = start === end ? formatDateHuman(start) : `${formatDateHuman(start)} — ${formatDateHuman(end)}`;
  return `${dates} · ${item.startTime || "—"}–${item.endTime || "—"}`;
}
function importantBoardItems(){
  const filters = state.importantFilters || { scope:"all", q:"" };
  const today = todayKey();
  const q = String(filters.q || "").trim().toLowerCase();
  return (state.importantDays || []).map(raw => {
    const item = normalizeImportantEvent(raw);
    return { ...item, nextOccurrence:importantNextOccurrence(item, today) };
  }).filter(item => {
    if (filters.scope === "recurring" && item.repeatMode === "NONE") return false;
    if (filters.scope === "past" && !(item.repeatMode === "NONE" && item.endDate < today)) return false;
    if (filters.scope === "upcoming" && item.repeatMode === "NONE" && item.endDate < today) return false;
    const hay = `${item.title || ""} ${item.date || ""} ${item.endDate || ""} ${item.place || ""} ${item.category || ""} ${item.description || ""}`.toLowerCase();
    if (q && !hay.includes(q)) return false;
    return true;
  }).sort((a,b) => String(a.nextOccurrence).localeCompare(String(b.nextOccurrence)) || String(a.title).localeCompare(String(b.title), currentLocale()));
}

function updateImportantModalState(){
  const detailsOpen = $("importantDetailsModal")?.hidden === false;
  const editorOpen = $("importantEditModal")?.hidden === false;
  document.body.classList.toggle("modalOpen", detailsOpen || editorOpen);
}
function setImportantModalVisible(id, visible){
  const modal = $(id);
  if (!modal) return;
  modal.hidden = !visible;
  updateImportantModalState();
}
function closeImportantDetails(){ setImportantModalVisible("importantDetailsModal", false); state.viewingImportantDayId = null; }
function closeImportantEditor(){ setImportantModalVisible("importantEditModal", false); state.editingImportantDayId = null; }
function closeImportantEventModals(){
  const details = $("importantDetailsModal");
  const editor = $("importantEditModal");
  if (details) details.hidden = true;
  if (editor) editor.hidden = true;
  state.viewingImportantDayId = null;
  state.editingImportantDayId = null;
  updateImportantModalState();
}
function importantById(id){ return normalizeImportantEvent((state.importantDays || []).find(x => Number(x.id) === Number(id))); }

function openImportantDetails(id){
  const item = importantById(id);
  if (!item?.id) return setSave("err", t("Важный день не найден"));
  closeImportantEventModals();
  state.viewingImportantDayId = Number(id);
  $("importantDetailsType").textContent = importantTypeLabel(item.eventType);
  $("importantDetailsTitle").textContent = `${item.icon || "★"} ${item.title || ""}`.trim();
  $("importantDetailsSchedule").textContent = `${importantScheduleLabel(item)} · ${repeatLabel(item.repeatMode)}`;
  const details = [];
  if (item.place) details.push([t("Место"), item.place]);
  if (item.category) details.push([t("Категория"), item.category]);
  if (!item.allDay && item.sourceTimezone) details.push([t("Исходный интервал"), item.sourceTimezone]);
  details.push([t("Напоминания"), item.reminders.length ? item.reminders.map(importantReminderLabel).join(", ") : t("без напоминаний")]);
  if (item.description) details.push([t("Описание"), item.description, true]);
  $("importantDetailsBody").innerHTML = details.map(([label,value,wide]) => `<div class="importantDetailsItem${wide ? " wide" : ""}"><span>${esc(label)}</span><p>${esc(value)}</p></div>`).join("");
  setImportantModalVisible("importantDetailsModal", true);
  $("importantDetailsClose")?.focus({ preventScroll:true });
}

function syncImportantEditorFields(){
  const type = $("importantEditType")?.value || "IMPORTANT_DATE";
  const allDay = type === "IMPORTANT_DATE" || !!$("importantEditAllDay")?.checked;
  $("importantEditAllDay").disabled = type === "IMPORTANT_DATE";
  if (type === "IMPORTANT_DATE") $("importantEditAllDay").checked = true;
  $("importantEditEndDateField").hidden = type === "IMPORTANT_DATE";
  document.querySelectorAll(".importantTimedField").forEach(el => { el.hidden = allDay; });
  if (type === "EVENT" && !$("importantEditEndDate").value) $("importantEditEndDate").value = $("importantEditStartDate").value;
}
function openImportantEditor(id = null, contextDate = null){
  const item = id == null ? null : importantById(id);
  closeImportantEventModals();
  state.editingImportantDayId = item?.id ? Number(item.id) : null;
  $("importantEditTitle").textContent = t(item ? "Редактировать событие" : "Новое событие");
  $("importantEditName").value = item?.title || "";
  $("importantEditType").value = item?.eventType || "IMPORTANT_DATE";
  $("importantEditRepeat").value = item?.repeatMode || "NONE";
  $("importantEditStartDate").value = item?.date || contextDate || state.selected || todayKey();
  $("importantEditEndDate").value = item?.endDate || item?.date || contextDate || state.selected || todayKey();
  $("importantEditAllDay").checked = item ? item.allDay : true;
  $("importantEditStartTime").value = item?.startTime || "09:00";
  $("importantEditEndTime").value = item?.endTime || "10:00";
  $("importantEditTimezone").value = item?.sourceTimezone || state.timeSettings?.workTimezone || browserTimeZone();
  $("importantEditPlace").value = item?.place || "";
  $("importantEditCategory").value = item?.category || "";
  $("importantEditIcon").value = item?.icon || "★";
  $("importantEditColor").value = item?.color || "#F5B841";
  $("importantEditDescription").value = item?.description || "";
  document.querySelectorAll('input[name="importantReminder"]').forEach(input => { input.checked = (item?.reminders || []).includes(Number(input.value)); });
  $("importantEditMessage").hidden = true;
  syncImportantEditorFields();
  setImportantModalVisible("importantEditModal", true);
  $("importantEditName")?.focus({ preventScroll:true });
}
function resetImportantBoardForm(){ state.editingImportantDayId = null; }
function startEditImportantDay(id){ openImportantEditor(id); }
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
  if ($("importantBoardSearch") && document.activeElement !== $("importantBoardSearch")) $("importantBoardSearch").value = state.importantFilters?.q || "";
  box.innerHTML = "";
  if (!total) return renderEmptyState(box, { icon:"★", title:"Важных дат пока нет.", text:"Добавь важную дату, событие со временем или период.", variant:"board" });
  if (!items.length) return renderEmptyState(box, { icon:"⌕", title:"По фильтрам ничего не найдено.", text:"Сбрось поиск или выбери другой период.", variant:"board" });
  for (const item of items) {
    const row = document.createElement("article");
    row.className = "importantBoardRow";
    row.dataset.importantDetails = item.id;
    const secondary = [importantTypeLabel(item.eventType), item.place, item.category].filter(Boolean).join(" · ");
    row.innerHTML = `<span class="importantDot" style="background:${esc(item.color || "#F5B841")}"></span><span class="importantBoardMain"><b>${esc(item.icon || "★")} ${esc(item.title || "")}</b><small>${esc(importantScheduleLabel(item))} · ${esc(repeatLabel(item.repeatMode))}</small><span class="importantBoardMetaLine">${esc(secondary)}</span></span><span class="importantBoardNext"><small>${esc(t("Следующее событие"))}</small><b class="mono">${esc(formatDateHuman(item.nextOccurrence))}</b></span><span class="importantBoardActions"><button type="button" data-important-details="${item.id}">${esc(t("Подробнее"))}</button><button type="button" data-important-edit="${item.id}">${esc(t("ред."))}</button></span>`;
    row.addEventListener("click", e => {
      if (e.target.closest("button,a,input,select,textarea,[role=button],[data-important-details],[data-important-edit]")) return;
      openImportantDetails(item.id);
    });
    box.appendChild(row);
  }
  box.querySelectorAll("[data-important-details]").forEach(btn => btn.addEventListener("click", e => {
    e.preventDefault();
    e.stopPropagation();
    openImportantDetails(Number(btn.dataset.importantDetails));
  }));
  box.querySelectorAll("[data-important-edit]").forEach(btn => btn.addEventListener("click", e => {
    e.preventDefault();
    e.stopPropagation();
    startEditImportantDay(Number(btn.dataset.importantEdit));
  }));
}

async function saveImportantEvent(e){
  e?.preventDefault();
  if (!moduleEnabled("important_dates")) return setSave("err", t("модуль выключен"));
  const type = $("importantEditType").value;
  const allDay = type === "IMPORTANT_DATE" || $("importantEditAllDay").checked;
  const payload = {
    title:$("importantEditName").value.trim(), eventType:type,
    date:$("importantEditStartDate").value, endDate:type === "IMPORTANT_DATE" ? "" : $("importantEditEndDate").value,
    allDay, startTime:allDay ? "" : $("importantEditStartTime").value,
    endTime:allDay ? "" : $("importantEditEndTime").value,
    sourceTimezone:allDay ? "" : $("importantEditTimezone").value.trim(),
    repeatMode:$("importantEditRepeat").value, color:$("importantEditColor").value || "#F5B841",
    place:$("importantEditPlace").value.trim(), category:$("importantEditCategory").value.trim(),
    icon:$("importantEditIcon").value.trim(), description:$("importantEditDescription").value.trim(),
    reminders:[...document.querySelectorAll('input[name="importantReminder"]:checked')].map(x => Number(x.value))
  };
  if (!payload.title || !payload.date) {
    $("importantEditMessage").hidden = false;
    $("importantEditMessage").textContent = t("Укажи название и дату начала.");
    return;
  }
  setSave("saving");
  try {
    if (state.editingImportantDayId) await api.updateImportantDay(state.editingImportantDayId, payload);
    else await api.createImportantDay(payload);
    closeImportantEventModals();
    await refreshImportantSettings();
    if (typeof invalidateBrowserNotificationSchedule === "function") invalidateBrowserNotificationSchedule();
    await loadMonth();
    renderCalendar(); renderImportantDays(); updateAccSummaries();
    closeImportantEventModals();
    setSave("saved", t("Событие сохранено"));
  } catch (err) {
    console.error(err);
    $("importantEditMessage").hidden = false;
    $("importantEditMessage").textContent = err.message;
    setSave("err", err.message);
  }
}

function syncImportantSelectedDate(key = state.selected){
  const input = $("impDate");
  if (!input || !/^\d{4}-\d{2}-\d{2}$/.test(String(key || ""))) return;
  input.value = key;
  input.dataset.contextDate = key;
}

function renderImportantDays(){
  if (!moduleEnabled("important_dates")) { updateAccSummaries(); return; }
  const box = $("importantList");
  if (!box || !state.selected) return;
  syncImportantSelectedDate(state.selected);
  const items = importantOf(state.selected).map(normalizeImportantEvent);
  box.innerHTML = "";
  if (!items.length) {
    renderEmptyState(box, { icon:"★", title:"В этот день важных событий нет.", text:"Отлично. Здесь пока чисто.", variant:"compact" });
    updateAccSummaries(); return;
  }
  for (const item of items) {
    const row = document.createElement("button");
    row.type = "button";
    row.className = "importantItem";
    row.innerHTML = `<span class="importantDot" style="background:${esc(item.color || "#F5B841")}"></span><span class="importantTitle">${esc(item.icon || "★")} ${esc(item.title || "")}</span><span class="importantMode">${esc(item.allDay ? repeatLabel(item.repeatMode) : `${item.startTime || ""}–${item.endTime || ""}`)}</span>`;
    row.addEventListener("click", () => openImportantDetails(item.id));
    box.appendChild(row);
  }
  updateAccSummaries();
}

async function addImportantDay(){
  if (!moduleEnabled("important_dates")) return setSave("err", t("модуль выключен"));
  const date = $("impDate")?.value || state.selected;
  const title = $("impTitle")?.value.trim();
  if (!date || !title) return setSave("err", t("укажи название и дату важного дня"));
  setSave("saving");
  try {
    await api.createImportantDay({ title, date, repeatMode:$("impRepeat").value, color:$("impColor").value || "#F5B841", eventType:"IMPORTANT_DATE", allDay:true });
    $("impTitle").value = "";
    await refreshImportantSettings();
    if (typeof invalidateBrowserNotificationSchedule === "function") invalidateBrowserNotificationSchedule();
    await loadMonth(); renderImportantDays(); renderCalendar(); updateAccSummaries(); setSave("saved");
  } catch (err) { console.error(err); setSave("err", err.message); }
}

async function removeImportantDay(id){
  if (!confirm(t("Удалить важное событие целиком, включая повторения?"))) return;
  setSave("saving");
  try {
    await api.deleteImportantDay(id);
    closeImportantDetails(); closeImportantEditor();
    await refreshImportantSettings();
    if (typeof invalidateBrowserNotificationSchedule === "function") invalidateBrowserNotificationSchedule();
    await loadMonth(); renderImportantDays(); renderCalendar(); updateAccSummaries(); setSave("saved");
  } catch (err) { console.error(err); setSave("err", err.message); }
}

$("impAdd")?.addEventListener("click", addImportantDay);
$("impTitle")?.addEventListener("keydown", e => { if (e.key === "Enter") addImportantDay(); });
$("impDateSelected")?.addEventListener("click", () => { if (!state.selected) return setSave("err", t("сначала выбери день в календаре")); $("impDate").value = state.selected; });
$("impDateToday")?.addEventListener("click", () => { $("impDate").value = todayKey(); });
$("accImp")?.addEventListener("toggle", event => {
  if (event.currentTarget.open && state.selected) syncImportantSelectedDate(state.selected);
});
$("importantBoardNew")?.addEventListener("click", () => openImportantEditor(null, todayKey()));
$("importantBoardScope")?.addEventListener("change", e => { state.importantFilters.scope = e.target.value; renderImportantBoard(); });
$("importantBoardSearch")?.addEventListener("input", e => { state.importantFilters.q = e.target.value; renderImportantBoard(); });
$("importantBoardToday")?.addEventListener("click", () => openImportantEditor(null, todayKey()));
$("importantBoardClear")?.addEventListener("click", () => { state.importantFilters = { scope:"all", q:"" }; renderImportantBoard(); });
$("importantEditType")?.addEventListener("change", syncImportantEditorFields);
$("importantEditAllDay")?.addEventListener("change", syncImportantEditorFields);
$("importantEditStartDate")?.addEventListener("change", () => { if (!$("importantEditEndDate").value || $("importantEditEndDate").value < $("importantEditStartDate").value) $("importantEditEndDate").value = $("importantEditStartDate").value; });
$("importantEditForm")?.addEventListener("submit", saveImportantEvent);
for (const id of ["importantEditClose","importantEditCancel","importantEditBackdrop"]) $(id)?.addEventListener("click", closeImportantEditor);
for (const id of ["importantDetailsClose","importantDetailsBackdrop"]) $(id)?.addEventListener("click", closeImportantDetails);
$("importantDetailsEdit")?.addEventListener("click", e => { e.preventDefault(); e.stopPropagation(); openImportantEditor(state.viewingImportantDayId); });
$("importantDetailsDelete")?.addEventListener("click", e => { e.preventDefault(); e.stopPropagation(); removeImportantDay(state.viewingImportantDayId); });
$("importantDetailsOpenDay")?.addEventListener("click", e => { e.preventDefault(); e.stopPropagation(); const item = importantById(state.viewingImportantDayId); closeImportantDetails(); openImportantCalendarDay(importantNextOccurrence(item)); });
document.addEventListener("keydown", e => { if (e.key !== "Escape") return; if (!$("importantEditModal")?.hidden) closeImportantEditor(); else if (!$("importantDetailsModal")?.hidden) closeImportantDetails(); });
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
  const projectList = $("taskProjectSuggestions");
  if (projectList) projectList.innerHTML = allTaskProjects().map(value => `<option value="${esc(value)}"></option>`).join("");
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
    state.taskMetadata = { categories:[], tags:[], projects:[] };
    renderTaskMetadataSuggestions();
    return;
  }
  try {
    const metadata = await api.taskMetadata();
    state.taskMetadata = {
      categories:Array.isArray(metadata?.categories) ? metadata.categories : [],
      tags:Array.isArray(metadata?.tags) ? metadata.tags : [],
      projects:Array.isArray(metadata?.projects) ? metadata.projects : [],
    };
    renderTaskMetadataSuggestions();
    renderTaskCategoryFilter();
    renderTaskBoardCategoryFilter();
    renderTaskBoardProjectFilter();
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
const expandedTaskSubtasks = new Set();

function normalizedTaskSubtasks(task){
  return [...(Array.isArray(task?.subtasks) ? task.subtasks : [])]
    .sort((a,b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0) || Number(a.id || 0) - Number(b.id || 0));
}
function taskSubtaskProgress(task){
  const subtasks = normalizedTaskSubtasks(task);
  return { total:subtasks.length, done:subtasks.filter(item => item.done).length, subtasks };
}
function taskSortDateValue(task){
  return String(task?.dueDate || task?.date || "9999-12-31");
}
function compareTasksOpenFirst(a, b){
  const doneOrder = Number(!!a?.done) - Number(!!b?.done);
  if (doneOrder) return doneOrder;
  const dateOrder = taskSortDateValue(a).localeCompare(taskSortDateValue(b));
  if (dateOrder) return dateOrder;
  const timeOrder = String(a?.dueTime || "99:99").localeCompare(String(b?.dueTime || "99:99"));
  if (timeOrder) return timeOrder;
  const taskDateOrder = String(a?.date || "9999-12-31").localeCompare(String(b?.date || "9999-12-31"));
  if (taskDateOrder) return taskDateOrder;
  return Number(a?.id || 0) - Number(b?.id || 0);
}
function sortedTasksOpenFirst(items){
  return [...(items || [])].sort(compareTasksOpenFirst);
}
function taskCompletionDivider(){
  const divider = document.createElement("div");
  divider.className = "taskCompletionDivider";
  divider.setAttribute("role", "separator");
  divider.textContent = t("Выполненные");
  return divider;
}
function updateTaskEditorSubtaskSummary(){
  const rows = [...($("taskEditSubtaskList")?.querySelectorAll(".taskSubtaskEditorRow") || [])];
  const done = rows.filter(row => row.querySelector('input[type="checkbox"]')?.checked).length;
  if ($("taskEditSubtaskSummary")) {
    $("taskEditSubtaskSummary").textContent = rows.length ? `${done}/${rows.length}` : t("необязательно");
  }
  if ($("taskEditSubtaskAdd")) $("taskEditSubtaskAdd").disabled = rows.length >= 50;
}
function addTaskSubtaskEditorRow(item = {}, { focus = false, after = null } = {}){
  const list = $("taskEditSubtaskList");
  if (!list) return null;
  if (list.children.length >= 50) {
    taskEditorMessage(t("Подзадач: максимум 50"), "err");
    return null;
  }

  const row = document.createElement("div");
  row.className = "taskSubtaskEditorRow";
  if (item.id != null) row.dataset.subtaskId = String(item.id);

  const done = document.createElement("input");
  done.type = "checkbox";
  done.checked = !!item.done;
  done.setAttribute("aria-label", t("Подзадача"));

  const text = document.createElement("input");
  text.type = "text";
  text.maxLength = 300;
  text.placeholder = t("Что нужно сделать сначала?");
  text.value = item.text || "";

  const dueWrap = document.createElement("label");
  dueWrap.className = "taskSubtaskDueField";
  dueWrap.title = t("Срок подзадачи");
  const dueIcon = document.createElement("span");
  dueIcon.setAttribute("aria-hidden", "true");
  dueIcon.textContent = "📅";
  const dueDate = document.createElement("input");
  dueDate.type = "date";
  dueDate.value = item.dueDate || "";
  dueDate.setAttribute("aria-label", t("Срок подзадачи"));
  dueWrap.append(dueIcon, dueDate);

  const up = document.createElement("button");
  up.type = "button";
  up.className = "subtaskMoveUp";
  up.textContent = "↑";
  up.title = t("Переместить выше");

  const down = document.createElement("button");
  down.type = "button";
  down.className = "subtaskMoveDown";
  down.textContent = "↓";
  down.title = t("Переместить ниже");

  const remove = document.createElement("button");
  remove.type = "button";
  remove.className = "subtaskDelete dangerGhost";
  remove.textContent = "×";
  remove.title = t("Удалить подзадачу");

  const move = direction => {
    const sibling = direction < 0 ? row.previousElementSibling : row.nextElementSibling;
    if (!sibling) return;
    if (direction < 0) list.insertBefore(row, sibling);
    else list.insertBefore(sibling, row);
    updateTaskEditorSubtaskSummary();
    text.focus({ preventScroll:true });
  };
  up.addEventListener("click", () => move(-1));
  down.addEventListener("click", () => move(1));
  remove.addEventListener("click", () => {
    row.remove();
    updateTaskEditorSubtaskSummary();
  });
  done.addEventListener("change", updateTaskEditorSubtaskSummary);
  text.addEventListener("keydown", event => {
    if (event.key !== "Enter" || event.shiftKey || event.isComposing) return;
    event.preventDefault();
    addTaskSubtaskEditorRow({}, { focus:true, after:row });
  });

  row.append(done, text, dueWrap, up, down, remove);
  if (after?.parentElement === list) after.after(row);
  else list.appendChild(row);
  updateTaskEditorSubtaskSummary();
  if (focus) text.focus({ preventScroll:true });
  return row;
}
function renderTaskEditorSubtasks(items = []){
  const list = $("taskEditSubtaskList");
  if (!list) return;
  list.innerHTML = "";
  for (const item of items) addTaskSubtaskEditorRow(item);
  if ($("taskEditSubtasks")) $("taskEditSubtasks").open = items.length > 0;
  updateTaskEditorSubtaskSummary();
}
function collectTaskEditorSubtasks(){
  const rows = [...($("taskEditSubtaskList")?.querySelectorAll(".taskSubtaskEditorRow") || [])];
  const subtasks = [];
  for (const row of rows) {
    const text = String(row.querySelector('input[type="text"]')?.value || "").trim();
    if (!text) continue;
    if (text.length > 300) throw new Error(t("Текст подзадачи: максимум 300 символов"));
    const rawId = row.dataset.subtaskId;
    subtasks.push({
      id:rawId ? Number(rawId) : null,
      text,
      done:!!row.querySelector('input[type="checkbox"]')?.checked,
      sortOrder:subtasks.length,
      dueDate:row.querySelector('input[type="date"]')?.value || "",
    });
  }
  if (subtasks.length > 50) throw new Error(t("Подзадач: максимум 50"));
  return subtasks;
}
function buildTaskSubtasksInline(task){
  const progress = taskSubtaskProgress(task);
  if (!progress.total) return null;

  const details = document.createElement("details");
  details.className = "taskSubtasksInline";
  details.open = expandedTaskSubtasks.has(Number(task.id));
  details.addEventListener("toggle", () => {
    if (details.open) expandedTaskSubtasks.add(Number(task.id));
    else expandedTaskSubtasks.delete(Number(task.id));
  });

  const summary = document.createElement("summary");
  summary.textContent = `${t("Подзадачи")} (${progress.done}/${progress.total})`;
  const list = document.createElement("div");
  list.className = "taskSubtaskInlineList";

  for (const subtask of progress.subtasks) {
    const row = document.createElement("label");
    row.className = "taskSubtaskInlineRow" + (subtask.done ? " done" : "");
    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.checked = !!subtask.done;
    checkbox.addEventListener("click", event => event.stopPropagation());
    checkbox.addEventListener("change", () => toggleSubtask(task.id, subtask.id, checkbox.checked));
    const text = document.createElement("span");
    text.className = "taskSubtaskInlineText";
    text.textContent = subtask.text;
    row.append(checkbox, text);
    if (subtask.dueDate) {
      const due = document.createElement("span");
      due.className = "taskSubtaskInlineDue";
      due.textContent = `📅 ${String(subtask.dueDate).split("-").reverse().join(".")}`;
      row.appendChild(due);
    }
    list.appendChild(row);
  }

  details.append(summary, list);
  return details;
}

function taskLocalDateTime(date, time){
  if (!date || !time) return null;
  const value = new Date(`${date}T${time}:00`);
  return Number.isNaN(value.getTime()) ? null : value;
}
function taskDateTimeParts(value){
  const pad = number => String(number).padStart(2, "0");
  return {
    date:`${value.getFullYear()}-${pad(value.getMonth()+1)}-${pad(value.getDate())}`,
    time:`${pad(value.getHours())}:${pad(value.getMinutes())}`,
  };
}
function taskScheduleDuration(startDate, startTime, endDate, endTime){
  const start = taskLocalDateTime(startDate, startTime);
  const end = taskLocalDateTime(endDate, endTime);
  if (!start || !end) return null;
  return Math.round((end.getTime() - start.getTime()) / 60000);
}
function setTaskPlanningError(message = ""){
  const box = $("taskPlanningError");
  if (box) box.textContent = message ? t(message) : "";
}
function taskPlanningDisplay(date, time){
  return date ? `${taskDetailsDate(date)}${time ? ` · ${time}` : ""}` : "";
}
function updateTaskPlanningControls({ source = "end" } = {}){
  const allDay = !!$("taskEditAllDay")?.checked;
  $("taskPlanningEditor")?.classList.toggle("allDay", allDay);
  for (const field of document.querySelectorAll("#taskPlanningEditor .taskTimedField")) field.hidden = allDay;
  setTaskPlanningError();
  const date = $("taskEditDate")?.value || "";
  if (allDay) {
    if ($("taskPlanningSummary")) $("taskPlanningSummary").textContent = date ? `${t("Весь день")} · ${taskDetailsDate(date)}` : t("Весь день");
    return;
  }
  const startTime = $("taskEditStartTime")?.value || "";
  let endDate = $("taskEditEndDate")?.value || "";
  let endTime = $("taskEditEndTime")?.value || "";
  const durationInput = $("taskEditDuration");
  const durationValue = Number(durationInput?.value || 0);
  if (source === "duration" && date && startTime && Number.isInteger(durationValue) && durationValue > 0) {
    const start = taskLocalDateTime(date, startTime);
    if (start) {
      const end = new Date(start.getTime() + durationValue * 60000);
      const parts = taskDateTimeParts(end);
      endDate = parts.date; endTime = parts.time;
      $("taskEditEndDate").value = endDate;
      $("taskEditEndTime").value = endTime;
    }
  } else if (date && startTime && endDate && endTime) {
    const calculated = taskScheduleDuration(date, startTime, endDate, endTime);
    if (calculated != null && calculated > 0 && durationInput) durationInput.value = String(calculated);
  }
  const duration = taskScheduleDuration(date, startTime, endDate, endTime);
  let summary = startTime ? taskPlanningDisplay(date, startTime) : t("Укажите время начала или включите «Весь день».");
  if (startTime && endDate && endTime && duration != null && duration > 0) summary += ` → ${taskPlanningDisplay(endDate, endTime)} · ${duration} ${t("мин")}`;
  else if (startTime) summary += ` · ${t("Точечная задача")}`;
  if ($("taskPlanningSummary")) $("taskPlanningSummary").textContent = summary;
}
function validateTaskPlanning(){
  const allDay = !!$("taskEditAllDay")?.checked;
  const date = $("taskEditDate")?.value || "";
  if (allDay) return { allDay:true, date, startTime:"", endDate:"", endTime:"", duration:null };
  const startTime = $("taskEditStartTime")?.value || "";
  const endDate = $("taskEditEndDate")?.value || "";
  const endTime = $("taskEditEndTime")?.value || "";
  if (!startTime) throw new Error(t("Укажите время начала или включите «Весь день»."));
  if (!!endDate !== !!endTime) throw new Error(t("Дата и время окончания должны быть заполнены вместе."));
  const duration = endDate && endTime ? taskScheduleDuration(date, startTime, endDate, endTime) : null;
  if (duration != null && (duration < 1 || duration > 10080)) {
    throw new Error(duration <= 0 ? t("Окончание должно быть позже начала.") : t("Длительность: от 1 минуты до 7 дней."));
  }
  return { allDay:false, date, startTime, endDate, endTime, duration };
}

function resetTaskEditorFields({ date = null, text = "", inboxId = null } = {}){
  state.editingTaskId = null;
  state.editingTaskMode = "create";
  state.editingTaskInboxId = inboxId == null ? null : Number(inboxId);
  $("taskEditText").value = text || "";
  $("taskEditDate").value = date || state.selected || todayKey();
  $("taskEditAllDay").checked = true;
  $("taskEditStartTime").value = "09:00";
  $("taskEditEndDate").value = "";
  $("taskEditEndTime").value = "";
  $("taskEditDuration").value = "";
  $("taskEditDescription").value = "";
  $("taskEditProject").value = "";
  $("taskEditCategory").value = "";
  $("taskEditTags").value = "";
  $("taskEditPriority").value = "NORMAL";
  $("taskEditDueDate").value = "";
  $("taskEditDueTime").value = "";
  $("taskEditReminderEnabled").checked = false;
  $("taskEditReminderBefore").value = "60";
  $("taskEditAdvanced").open = false;
  renderTaskEditorSubtasks([]);
  $("taskEditTitle").textContent = inboxId ? t("Разобрать запись в задачу") : t("Новая задача");
  $("taskEditHint").textContent = inboxId
    ? t("Текст уже взят из «Входящих». Проверь дату и при необходимости добавь детали.")
    : t("Достаточно текста и даты. Остальное можно заполнить позже.");
  $("taskEditSave").textContent = inboxId ? t("Создать задачу") : t("Добавить задачу");
  taskEditorMessage();
  updateTaskReminderControls();
  updateTaskPlanningControls();
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
  $("taskEditDescription").value = task.description || "";
  $("taskEditProject").value = task.project || "";
  $("taskEditAllDay").checked = task.allDay !== false;
  $("taskEditDate").value = task.scheduledStartDate || task.date || state.selected || todayKey();
  $("taskEditStartTime").value = task.scheduledStartTime || "09:00";
  $("taskEditEndDate").value = task.scheduledEndDate || "";
  $("taskEditEndTime").value = task.scheduledEndTime || "";
  $("taskEditDuration").value = task.scheduledDurationMinutes || "";
  $("taskEditCategory").value = task.category || "";
  $("taskEditTags").value = (task.tags || []).join(", ");
  $("taskEditPriority").value = task.priority || "NORMAL";
  $("taskEditDueDate").value = task.dueDate || "";
  $("taskEditDueTime").value = task.dueTime || "";
  $("taskEditReminderEnabled").checked = !!task.reminderEnabled;
  $("taskEditReminderBefore").value = String(task.reminderMinutesBefore ?? 60);
  renderTaskEditorSubtasks(normalizedTaskSubtasks(task));
  $("taskEditAdvanced").open = !!(task.description || task.project || task.category || (task.tags || []).length || task.priority !== "NORMAL" || task.dueDate || task.dueTime || task.reminderEnabled);
  $("taskEditTitle").textContent = t("Редактировать задачу");
  $("taskEditHint").textContent = t("Изменения применятся к существующей задаче.");
  $("taskEditSave").textContent = t("Сохранить");
  taskEditorMessage();
  updateTaskReminderControls();
  updateTaskPlanningControls();
  renderTaskMetadataSuggestions();
  openAppModal("taskEditModal", "taskEditText");
}
function closeTaskDetails(){
  state.taskDetailsId = null;
  closeAppModal("taskDetailsModal");
}
function taskDetailsFact(label, value){
  if (!value) return null;
  const row = document.createElement("div");
  row.className = "taskDetailsFact";
  const name = document.createElement("span");
  name.textContent = t(label);
  const content = document.createElement("b");
  content.textContent = value;
  row.append(name, content);
  return row;
}
function taskDetailsDate(value){
  return value ? String(value).split("-").reverse().join(".") : "";
}
function renderTaskDetails(task){
  if (!task) return;
  state.taskDetailsId = Number(task.id);
  $("taskDetailsTitle").textContent = task.text || t("Задача");
  $("taskDetailsHint").textContent = task.done ? t("Задача выполнена") : (task.overdue ? t("Задача просрочена") : t("Открытая задача"));
  const meta = $("taskDetailsMeta");
  meta.innerHTML = "";
  meta.append(...buildTaskMeta(task).childNodes);

  const scheduleMain = $("taskDetailsScheduleMain");
  scheduleMain.textContent = taskPlannedLabel(task, { includeDate:true });
  const sourceSchedule = $("taskDetailsScheduleSource");
  const sourceStart = task.scheduledSourceStartDate && task.scheduledSourceStartTime
    ? `${taskDetailsDate(task.scheduledSourceStartDate)} · ${task.scheduledSourceStartTime}` : "";
  const sourceEnd = task.scheduledSourceEndDate && task.scheduledSourceEndTime
    ? `${taskDetailsDate(task.scheduledSourceEndDate)} · ${task.scheduledSourceEndTime}` : "";
  const sourceDiffers = !!sourceStart && (
    task.scheduledSourceStartDate !== task.scheduledStartDate
    || task.scheduledSourceStartTime !== task.scheduledStartTime
    || task.scheduledSourceEndDate !== task.scheduledEndDate
    || task.scheduledSourceEndTime !== task.scheduledEndTime
    || task.scheduledSourceTimezone !== state.profile?.workTimezone
  );
  sourceSchedule.hidden = !sourceDiffers;
  sourceSchedule.textContent = sourceDiffers
    ? `${t("Исходный интервал")}: ${sourceStart}${sourceEnd ? ` → ${sourceEnd}` : ""} · ${task.scheduledSourceTimezone}` : "";

  const description = $("taskDetailsDescription");
  const descriptionText = $("taskDetailsDescriptionText");
  const hasDescription = !!String(task.description || "").trim();
  description.classList.toggle("empty", !hasDescription);
  descriptionText.textContent = hasDescription ? task.description : t("Описание пока не добавлено.");

  const facts = $("taskDetailsFacts");
  facts.innerHTML = "";
  const due = task.dueDate ? `${taskDetailsDate(task.dueDate)}${task.dueTime ? " · " + task.dueTime : ""}` : "";
  const sourceDue = task.deadlineAbsolute && task.dueSourceDate && task.dueSourceTime && task.dueSourceTimezone
    ? `${taskDetailsDate(task.dueSourceDate)} · ${task.dueSourceTime} · ${task.dueSourceTimezone}` : "";
  const deadlineSourceDiffers = sourceDue && (
    task.dueSourceDate !== task.dueDate || task.dueSourceTime !== task.dueTime || task.dueSourceTimezone !== state.profile?.workTimezone
  );
  const rows = [
    taskDetailsFact("Проект", task.project || ""),
    taskDetailsFact("Дедлайн", due),
    taskDetailsFact("Исходный срок", deadlineSourceDiffers ? sourceDue : ""),
    taskDetailsFact("Напоминание", task.reminderEnabled ? `${task.reminderMinutesBefore ?? 0} ${t("минут до срока")}` : ""),
  ].filter(Boolean);
  facts.append(...rows);

  const checklist = $("taskDetailsChecklist");
  checklist.innerHTML = "";
  const progress = taskSubtaskProgress(task);
  $("taskDetailsChecklistSection").hidden = !progress.total;
  if (progress.total) {
    $("taskDetailsChecklistTitle").textContent = `${t("Подзадачи")} (${progress.done}/${progress.total})`;
    for (const subtask of progress.subtasks) {
      const row = document.createElement("label");
      row.className = "taskDetailsSubtask" + (subtask.done ? " done" : "");
      const checkbox = document.createElement("input");
      checkbox.type = "checkbox";
      checkbox.checked = !!subtask.done;
      checkbox.addEventListener("change", async () => {
        await toggleSubtask(task.id, subtask.id, checkbox.checked);
        const refreshed = taskById(task.id);
        if (refreshed) renderTaskDetails(refreshed);
      });
      const text = document.createElement("span");
      text.textContent = subtask.text;
      row.append(checkbox, text);
      if (subtask.dueDate) {
        const dueEl = document.createElement("small");
        dueEl.textContent = `📅 ${taskDetailsDate(subtask.dueDate)}`;
        row.appendChild(dueEl);
      }
      checklist.appendChild(row);
    }
  }
  $("taskDetailsToggle").textContent = task.done ? t("Вернуть в открытые") : t("Выполнить");
}
async function openTaskDetails(taskOrId){
  if (!moduleEnabled("tasks")) return setSave("err", t("модуль выключен"));
  const id = Number(typeof taskOrId === "object" ? taskOrId?.id : taskOrId);
  let task = typeof taskOrId === "object" ? taskOrId : taskById(id);
  if (!id || !task) return setSave("err", t("Задача не найдена"));
  renderTaskDetails(task);
  openAppModal("taskDetailsModal", "taskDetailsEdit");
  if (!navigator.onLine) return;
  try {
    const fresh = await api.task(id);
    upsertTaskEverywhere(fresh);
    if (state.taskDetailsId === id) renderTaskDetails(fresh);
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}
function refreshOpenTaskDetails(task){
  if (!task || Number(state.taskDetailsId) !== Number(task.id)) return;
  if ($("taskDetailsModal")?.hidden) return;
  renderTaskDetails(task);
}

function validateTaskEditorDeadlines(planning, dueDate, dueTime, subtasks, original = null){
  const unchangedAbsolute = !!original?.deadlineAbsolute
    && dueDate === (original.dueDate || "") && dueTime === (original.dueTime || "");
  const plannedEndDate = planning.endDate || planning.date;
  const plannedEndTime = planning.endTime || planning.startTime || "00:00";
  if (!unchangedAbsolute && dueDate) {
    if (dueDate < plannedEndDate) throw new Error(t("Дедлайн не может быть раньше окончания запланированного интервала."));
    if (dueDate === plannedEndDate && dueTime && plannedEndTime && dueTime < plannedEndTime) {
      throw new Error(t("Дедлайн не может быть раньше окончания запланированного интервала."));
    }
  }
  if ((subtasks || []).some(item => item.dueDate && item.dueDate < planning.date)) {
    throw new Error(t("Срок подзадачи не может быть раньше даты задачи."));
  }
}
function taskEditorPayload(original = null){
  const text = $("taskEditText").value.trim();
  const date = $("taskEditDate").value;
  if (!text) throw new Error(t("напиши текст задачи"));
  if (!date) throw new Error(t("укажи дату"));
  const planning = validateTaskPlanning();
  const tags = parseTaskTags($("taskEditTags").value);
  const remindersAvailable = state.modulesLoaded && moduleEnabled("notifications");
  const reminderEnabled = remindersAvailable ? !!$("taskEditReminderEnabled").checked : !!original?.reminderEnabled;
  const reminderMinutesBefore = remindersAvailable
    ? (reminderEnabled ? Number($("taskEditReminderBefore").value || 0) : null)
    : (original?.reminderMinutesBefore ?? null);
  if (reminderEnabled && (!Number.isInteger(reminderMinutesBefore) || reminderMinutesBefore < 0 || reminderMinutesBefore > 10080)) {
    throw new Error(t("напоминание: от 0 до 10080 минут"));
  }
  const dueDate = $("taskEditDueDate").value || "";
  const dueTime = $("taskEditDueTime").value || "";
  const subtasks = collectTaskEditorSubtasks();
  validateTaskEditorDeadlines(planning, dueDate, dueTime, subtasks, original);
  return {
    date:planning.date,
    text,
    description:$("taskEditDescription").value.trim(),
    project:String($("taskEditProject").value || "").trim(),
    category:normalizeTaskCategory($("taskEditCategory").value),
    tags,
    priority:$("taskEditPriority").value || "NORMAL",
    dueDate, dueTime,
    reminderEnabled, reminderMinutesBefore, subtasks,
    allDay:planning.allDay,
    scheduledStartDate:planning.date,
    scheduledStartTime:planning.startTime,
    scheduledEndDate:planning.endDate,
    scheduledEndTime:planning.endTime,
    scheduledDurationMinutes:null,
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
    upsertTaskEverywhere(saved);
    if (typeof dataLayer.updateSnapshotTask === "function") await dataLayer.updateSnapshotTask(saved);
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
  addTaskToDateMap(state.tasksByDate, task);
}
function upsertTaskEverywhere(task){
  if (!task) return;
  upsertTaskInMaps(task);
  state.taskBoard.items = (state.taskBoard.items || []).map(item =>
    Number(item.id) === Number(task.id) ? task : item);
  refreshOpenTaskDetails(task);
}
async function updateTaskDetails(id, patch){
  setSave("saving");
  try {
    const updated = await api.updateTask(id, patch);
    upsertTaskEverywhere(updated);
    if (typeof dataLayer.updateSnapshotTask === "function") await dataLayer.updateSnapshotTask(updated);
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
  const oldTask = taskById(id);
  const pendingSubtasks = normalizedTaskSubtasks(oldTask).filter(item => !item.done);

  if (done && pendingSubtasks.length) {
    if (!confirm(t("У задачи есть незавершённые подзадачи. Отметить выполненными и задачу, и все подзадачи?"))) {
      renderTasks();
      renderTaskBoard();
      return;
    }
    if (!navigator.onLine) {
      setSave("err", t("Подзадачи можно отмечать только при подключении к серверу."));
      renderTasks();
      renderTaskBoard();
      return;
    }
    setSave("saving");
    try {
      const updated = await api.updateTask(id, { done:true, completeSubtasks:true });
      upsertTaskEverywhere(updated);
      if (typeof dataLayer.updateSnapshotTask === "function") await dataLayer.updateSnapshotTask(updated);
      await loadTaskBoard(true);
      if (typeof invalidateBrowserNotificationSchedule === "function") invalidateBrowserNotificationSchedule();
      setSave("saved");
      renderTasks();
      renderTaskBoard();
      renderCalendar();
      return;
    } catch (err) {
      console.error(err);
      setSave("err", err.message);
      if (oldTask) upsertTaskEverywhere(oldTask);
      renderTasks();
      renderTaskBoard();
      renderCalendar();
      return;
    }
  }

  setSave("saving");
  if (oldTask) upsertTaskEverywhere({ ...oldTask, done:!!done });
  renderTasks();
  renderTaskBoard();
  renderCalendar();
  try {
    const result = await dataLayer.setTaskDone(id, done);
    if (result.task) upsertTaskEverywhere(result.task);
    if (!result.queued) await loadTaskBoard(true);
    if (typeof invalidateBrowserNotificationSchedule === "function") invalidateBrowserNotificationSchedule();
    setSave("saved");
    renderTasks();
    renderTaskBoard();
    renderCalendar();
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
    if (oldTask) upsertTaskEverywhere(oldTask);
    await loadMonth();
    renderTasks();
    renderTaskBoard();
    renderCalendar();
  }
}
async function toggleSubtask(taskId, subtaskId, done){
  if (!moduleEnabled("tasks")) return setSave("err", t("модуль выключен"));
  const oldTask = taskById(taskId);
  if (!oldTask || subtaskId == null) return setSave("err", t("Задача не найдена"));
  if (!navigator.onLine) {
    setSave("err", t("Подзадачи можно отмечать только при подключении к серверу."));
    renderTasks();
    renderTaskBoard();
    return;
  }

  const optimistic = {
    ...oldTask,
    subtasks:normalizedTaskSubtasks(oldTask).map(item =>
      Number(item.id) === Number(subtaskId) ? { ...item, done:!!done } : item)
  };
  upsertTaskEverywhere(optimistic);
  renderTasks();
  renderTaskBoard();
  setSave("saving");
  try {
    const updated = await api.updateSubtask(taskId, subtaskId, { done:!!done });
    upsertTaskEverywhere(updated);
    if (typeof dataLayer.updateSnapshotTask === "function") await dataLayer.updateSnapshotTask(updated);
    setSave("saved");
    renderTasks();
    renderTaskBoard();
    renderCalendar();
  } catch (err) {
    console.error(err);
    upsertTaskEverywhere(oldTask);
    setSave("err", err.message || t("Не удалось обновить подзадачу"));
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
    if (Number(state.taskDetailsId) === Number(id)) closeTaskDetails();
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
  return sortedTasksOpenFirst(items.filter(task => {
    if (category !== "all" && (task.category || "") !== category) return false;
    if (status === "open" && task.done) return false;
    if (status === "done" && !task.done) return false;
    if (status === "overdue" && (!task.overdue || task.done)) return false;
    return true;
  }));
}
function buildTaskMeta(task){
  const meta = document.createElement("div");
  meta.className = "taskMeta";
  const planned = document.createElement("span");
  planned.className = "taskPlannedChip";
  planned.textContent = `◷ ${taskPlannedLabel(task, { includeDate:false })}`;
  meta.appendChild(planned);
  if (task.project) {
    const project = document.createElement("span");
    project.className = "taskProjectChip";
    project.textContent = `▣ ${task.project}`;
    meta.appendChild(project);
  }
  if (task.category) {
    const badge = document.createElement("span");
    badge.className = "taskBadge cat";
    badge.textContent = `🏷 ${task.category}`;
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
    badge.textContent = `${task.priority === "URGENT" ? "🔴" : task.priority === "HIGH" ? "🟠" : "🔵"} ${taskPriorityLabel(task.priority)}`;
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
  const progress = taskSubtaskProgress(task);
  if (progress.total) {
    const progressEl = document.createElement("span");
    progressEl.className = "taskSubtaskProgress";
    progressEl.setAttribute("role", "progressbar");
    progressEl.setAttribute("aria-valuemin", "0");
    progressEl.setAttribute("aria-valuemax", String(progress.total));
    progressEl.setAttribute("aria-valuenow", String(progress.done));
    progressEl.setAttribute("aria-label", `${progress.done}/${progress.total} ${t("выполнено подзадач")}`);
    const track = document.createElement("span");
    track.className = "taskSubtaskProgressTrack";
    const fill = document.createElement("span");
    fill.className = "taskSubtaskProgressFill";
    fill.style.width = `${Math.round(progress.done * 100 / progress.total)}%`;
    track.appendChild(fill);
    const value = document.createElement("span");
    value.className = "taskSubtaskProgressValue";
    value.textContent = `${progress.done}/${progress.total}`;
    progressEl.append(track, value);
    meta.appendChild(progressEl);
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
  const showCompletionDivider = items.some(task => !task.done) && items.some(task => task.done);
  let completionDividerShown = false;
  for (const task of items) {
    if (showCompletionDivider && task.done && !completionDividerShown) {
      box.appendChild(taskCompletionDivider());
      completionDividerShown = true;
    }
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
    body.addEventListener("click", () => openTaskDetails(task));
    const remove = document.createElement("button");
    remove.className = "tinyDel";
    remove.type = "button";
    remove.textContent = "×";
    remove.title = t("Удалить задачу");
    remove.addEventListener("click", () => removeTask(task.id));
    row.append(checkbox, body, remove);
    const subtasks = buildTaskSubtasksInline(task);
    if (subtasks) row.appendChild(subtasks);
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
  return formatAbsoluteInstant(value);
}
function renderInbox(){
  const list = $("inboxList");
  if (!list) return;
  const allItems = state.inbox.items || [];
  const query = String(state.inbox.q || "").trim().toLowerCase();
  const items = query ? allItems.filter(item => String(item.text || "").toLowerCase().includes(query)) : allItems;
  const totalOpenCount = allItems.filter(item => item.status === "OPEN").length;
  const visibleOpenCount = items.filter(item => item.status === "OPEN").length;
  const tray = $("taskInboxCard");
  tray?.classList.toggle("empty", totalOpenCount === 0);
  if ($("inboxCount")) $("inboxCount").textContent = String(totalOpenCount);
  if ($("inboxStatus")) $("inboxStatus").textContent = state.inbox.loading
    ? t("загрузка…")
    : (query
      ? `${visibleOpenCount} / ${totalOpenCount} ${t("не разобрано")}`
      : (totalOpenCount ? `${totalOpenCount} ${t("не разобрано")}` : t("пусто")));
  if (state.inbox.loading && !items.length) {
    renderLoadingState(list, "Загружаю входящие…", 2);
    return;
  }
  list.innerHTML = "";
  if (!items.length) {
    renderEmptyState(list, {
      icon:"↘",
      title:query ? "По запросу во входящих ничего не найдено." : "Входящие пусты.",
      text:query ? "Очисти поиск или измени запрос." : "Сохраняй записи сразу — структуру можно добавить позже.",
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
function renderTaskBoardProjectFilter(){
  const select = $("taskBoardProject");
  if (!select) return;
  const current = select.value || state.taskBoard.filters.project || "all";
  select.innerHTML = `<option value="all">${esc(t("все проекты"))}</option>`;
  for (const project of allTaskProjects()) {
    const option = document.createElement("option");
    option.value = project; option.textContent = project; select.appendChild(option);
  }
  select.value = [...select.options].some(option => option.value === current) ? current : "all";
  state.taskBoard.filters.project = select.value;
}
function syncTaskBoardFiltersToInputs(){
  const filters = state.taskBoard.filters;
  if ($("taskBoardStatusFilter")) $("taskBoardStatusFilter").value = filters.status || "open";
  if ($("taskBoardProject")) $("taskBoardProject").value = filters.project || "all";
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
      project:filters.project !== "all" ? filters.project : "",
      priority:filters.priority !== "all" ? filters.priority : "",
      q:filters.q || "",
      scheduledFrom:filters.from || "",
      scheduledTo:filters.to || "",
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
  return taskPlannedLabel(task, { includeDate:true });
}
function renderTaskBoard(){
  const list = $("taskBoardList");
  if (!list) return;
  syncTaskBoardFiltersToInputs();
  renderTaskBoardCategoryFilter();
  renderTaskBoardProjectFilter();
  if (state.ui?.loadingTasks) {
    $("taskBoardStatus").textContent = t("загрузка…");
    $("taskBoardStats").innerHTML = "";
    $("taskBoardPager").innerHTML = "";
    renderLoadingState(list, "Загружаю задачи…", 4);
    return;
  }
  const items = sortedTasksOpenFirst(state.taskBoard.items || []);
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
  const showCompletionDivider = items.some(task => !task.done) && items.some(task => task.done);
  let completionDividerShown = false;
  for (const task of items) {
    if (showCompletionDivider && task.done && !completionDividerShown) {
      list.appendChild(taskCompletionDivider());
      completionDividerShown = true;
    }
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
    const plannedDate = task.scheduledStartDate || task.date;
    date.title = `${t("Открыть день")} ${plannedDate}`;
    date.addEventListener("click", () => goToTaskDate(plannedDate));
    const body = document.createElement("button");
    body.type = "button";
    body.className = "taskBoardBody";
    const text = document.createElement("div");
    text.className = "taskText";
    text.textContent = task.text;
    body.append(text, buildTaskMeta(task));
    body.addEventListener("click", () => openTaskDetails(task));
    const actions = document.createElement("div");
    actions.className = "taskBoardActions";
    const remove = document.createElement("button");
    remove.type = "button";
    remove.textContent = "×";
    remove.title = t("Удалить задачу");
    remove.addEventListener("click", () => removeTask(task.id));
    actions.appendChild(remove);
    row.append(checkbox, date, body, actions);
    const subtasks = buildTaskSubtasksInline(task);
    if (subtasks) row.appendChild(subtasks);
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
  selectDay(date);
  const section = $("accNote");
  if (section) section.open = true;
  if (text) {
    try {
      setSave("saving");
      const created = normalizeDayNote(await api.createDayNote({ date, title:null, content:text, pinned:false }));
      installDayNotes(date, [...notesOfDay(date), created], created.id);
      await persistNotesSnapshot(date);
      setSave("saved", t("Создана новая заметка"));
    } catch (err) {
      console.error(err);
      setSave("err", err.message);
    }
  }
  renderDayNotes();
  setTab("edit");
  $("noteEdit")?.focus({ preventScroll:true });
}

function quickActionImportant(){
  if (!moduleEnabled("important_dates")) return setSave("err", t("модуль выключен"));
  const text = quickActionDraftText();
  const date = state.selected || todayKey();
  closeQuickActions();
  location.hash = "#important";
  setTimeout(() => {
    openImportantEditor(null, date);
    if (text) $("importantEditName").value = text;
    $("importantEditName")?.focus({ preventScroll:true });
  }, 0);
}
function quickActionOvertime(kind){
  closeQuickActions();
  if (kind === "usage") {
    if (!moduleEnabled("vacation") || typeof openAbsenceComposer !== "function") return setSave("err", t("модуль выключен"));
    openAbsenceComposer({ date:state.selected || todayKey(), source:"quick-add" }).catch(error => {
      console.error(error);
      setSave("err", error.message || t("Ошибка"));
    });
    return;
  }
  if (!moduleEnabled("overtime")) return setSave("err", t("модуль выключен"));
  openOvertimeCreditModal(state.selected || todayKey());
}

$("taskDetailsClose")?.addEventListener("click", closeTaskDetails);
$("taskDetailsBackdrop")?.addEventListener("click", closeTaskDetails);
$("taskDetailsEdit")?.addEventListener("click", () => {
  const task = taskById(state.taskDetailsId);
  closeTaskDetails();
  if (task) editTask(task);
});
$("taskDetailsToggle")?.addEventListener("click", async () => {
  const task = taskById(state.taskDetailsId);
  if (!task) return;
  await toggleTask(task.id, !task.done);
  const refreshed = taskById(task.id);
  if (refreshed) renderTaskDetails(refreshed);
});
$("taskDetailsDelete")?.addEventListener("click", async () => {
  const id = state.taskDetailsId;
  if (id != null) await removeTask(id);
});

$("taskCreateForDay")?.addEventListener("click", () => openTaskCreate({ date:state.selected || todayKey() }));
$("taskBoardCreate")?.addEventListener("click", () => openTaskCreate({ date:state.selected || todayKey() }));
$("taskStatusFilter")?.addEventListener("change", () => { state.taskFilters.status = $("taskStatusFilter").value; renderTasks(); });
$("taskCategoryFilter")?.addEventListener("change", () => { state.taskFilters.category = $("taskCategoryFilter").value; renderTasks(); });
$("taskEditProject")?.addEventListener("blur", () => { $("taskEditProject").value = String($("taskEditProject").value || "").trim(); });
$("taskEditCategory")?.addEventListener("blur", () => { $("taskEditCategory").value = normalizeTaskCategory($("taskEditCategory").value); });
$("taskEditAllDay")?.addEventListener("change", () => updateTaskPlanningControls());
$("taskEditDate")?.addEventListener("change", () => updateTaskPlanningControls({ source:"duration" }));
$("taskEditStartTime")?.addEventListener("change", () => updateTaskPlanningControls({ source:"duration" }));
$("taskEditEndDate")?.addEventListener("change", () => updateTaskPlanningControls({ source:"end" }));
$("taskEditEndTime")?.addEventListener("change", () => updateTaskPlanningControls({ source:"end" }));
$("taskEditDuration")?.addEventListener("input", () => updateTaskPlanningControls({ source:"duration" }));
for (const button of document.querySelectorAll("[data-task-duration]")) button.addEventListener("click", () => {
  $("taskEditDuration").value = button.dataset.taskDuration || "";
  updateTaskPlanningControls({ source:"duration" });
});
$("taskEditDueDate")?.addEventListener("change", () => {
  if ($("taskEditDueDate").value && state.modulesLoaded && moduleEnabled("notifications")) $("taskEditReminderEnabled").checked = true;
  updateTaskReminderControls();
});
$("taskEditReminderEnabled")?.addEventListener("change", updateTaskReminderControls);
$("taskEditSubtaskAdd")?.addEventListener("click", () => {
  if ($("taskEditSubtasks")) $("taskEditSubtasks").open = true;
  addTaskSubtaskEditorRow({}, { focus:true });
});
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
$("inboxSearch")?.addEventListener("input", () => { state.inbox.q = $("inboxSearch").value.trim(); renderInbox(); });

$("taskBoardOpen")?.addEventListener("click", () => setTaskBoardQuickStatus("open"));
$("taskBoardOverdue")?.addEventListener("click", () => setTaskBoardQuickStatus("overdue"));
$("taskBoardAll")?.addEventListener("click", () => setTaskBoardQuickStatus("all"));
$("taskBoardThisMonth")?.addEventListener("click", () => { const range = monthFromTo(); state.taskBoard.filters.from = range.from; state.taskBoard.filters.to = range.to; resetTaskBoardPage(); loadTaskBoard(false); });
$("taskBoardClear")?.addEventListener("click", () => { state.taskBoard.filters = { status:"open", category:"all", project:"all", priority:"all", q:"", from:"", to:"" }; resetTaskBoardPage(); loadTaskBoard(false); });
$("taskBoardStatusFilter")?.addEventListener("change", () => { state.taskBoard.filters.status = $("taskBoardStatusFilter").value; resetTaskBoardPage(); loadTaskBoard(false); });
$("taskBoardCategory")?.addEventListener("change", () => { state.taskBoard.filters.category = $("taskBoardCategory").value; resetTaskBoardPage(); loadTaskBoard(false); });
$("taskBoardProject")?.addEventListener("change", () => { state.taskBoard.filters.project = $("taskBoardProject").value; resetTaskBoardPage(); loadTaskBoard(false); });
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


function renderShiftProjection(){
  const box = $("shiftProjection");
  if (!box) return;
  const segment = state.selected ? primaryShiftSegment(state.selected) : null;
  const occurrence = segment?.occurrence || null;
  const floatingEntry = state.selected ? state.days[state.selected] : null;
  const interval = floatingEntry?.shiftInterval || null;
  if (!occurrence && !interval) {
    box.hidden = true;
    box.innerHTML = "";
    return;
  }
  const durationLabel = minutes => {
    const safe = Math.max(0, Number(minutes || 0));
    if (!safe) return state.language === "en" ? "0m" : "0м";
    const hours = Math.floor(safe / 60);
    const minutesLeft = safe % 60;
    return `${hours ? `${hours}${state.language === "en" ? "h" : "ч"}` : ""}${hours && minutesLeft ? " " : ""}${minutesLeft ? `${minutesLeft}${state.language === "en" ? "m" : "м"}` : ""}`;
  };
  const netMinutes = occurrence?.netMinutes ?? interval?.netMinutes ?? 0;
  const breakMinutes = occurrence?.breakMinutes ?? interval?.breakMinutes ?? 0;
  const durationHint = `${t("Рабочее время смены")}: ${durationLabel(netMinutes)}${Number(breakMinutes) > 0 ? ` · ${t("Обед в смене")}: ${durationLabel(breakMinutes)}` : ""}`;

  if (occurrence) {
    const currentRange = segment?.range || displayDateTimeRange(occurrence.displayStart, occurrence.displayEnd);
    const sourceRange = displayDateTimeRange(occurrence.sourceStart, occurrence.sourceEnd);
    const movedDay = occurrence.sourceDate !== state.selected;
    box.innerHTML = `
      <div class="shiftProjectionRow primary">
        <span>${esc(t("Текущее отображение"))}</span>
        <strong>${esc(currentRange)}</strong>
        <code>${esc(occurrence.displayTimezone || "")}</code>
      </div>
      <div class="shiftProjectionRow">
        <span>${esc(t("Исходная смена"))}</span>
        <strong>${esc(sourceRange)}</strong>
        <code>${esc(occurrence.sourceTimezone || "")}</code>
      </div>
      ${movedDay ? `<div class="shiftProjectionHint">${esc(t("Смена назначена на исходную дату"))}: ${esc(occurrence.sourceDate)}</div>` : ""}
      ${occurrence.legacyLocal ? `<div class="shiftProjectionHint warn">⚠ ${esc(t("Старая смена ещё не привязана к часовому поясу"))}</div>` : ""}
      <div class="shiftProjectionHint">${esc(durationHint)}</div>
    `;
  } else {
    const work = shiftIntervalRange(interval, "work");
    box.innerHTML = `
      <div class="shiftProjectionRow primary">
        <span>${esc(t("Рабочая смена"))}</span>
        <strong>${esc(work)}</strong>
        <code>${esc(interval.workTimezone || "")}</code>
      </div>
      <div class="shiftProjectionHint">${esc(durationHint)}</div>
    `;
  }
  box.hidden = false;
}

function renderChips(){
  const box = $("chips");
  box.innerHTML = "";
  const projectedOccurrence = state.selected ? shiftOccurrenceForDate(state.selected) : null;
  const sourceDate = projectedOccurrence?.sourceDate || state.selected;
  const cur = projectedOccurrence?.shiftTypeId ?? state.days[sourceDate]?.shiftTypeId ?? null;
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
  renderShiftProjection();
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
  const selectedDate = state.selected;
  const k = shiftSourceDateForSelected();
  if (!selectedDate || !k) return;
  // Flush an independent note PATCH before changing the selected shift so the
  // save indicator and local snapshot settle in deterministic order. The note
  // request no longer carries a day/shift snapshot.
  await flushPendingSave();
  const cur = state.days[k] || {};
  const next = {
    shiftTypeId: cur.shiftTypeId === id ? null : id,
    shiftInterval: null,
    note: cur.note || null,
    dayEmoji: cur.dayEmoji || null,
    overtimeHours: numOr0(cur.overtimeHours),
    timeOffHours: numOr0(cur.timeOffHours),
  };
  applyLocal(k, next);
  updateAccSummaries();
  renderChips(); renderCalendar();
  await pushDaySnapshot(k, next);
  // The projected calendar date may differ from the source date. Always reload the
  // authoritative occurrence list after assigning or clearing a shift.
  await loadMonth({ fresh:true });
  if (selectedDate) selectDay(selectedDate);
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

/* Несколько заметок: независимые сущности, локальный optimistic UI и debounce PATCH */
let noteTimer = null;
let pendingNoteSave = null;

function noteLabel(note){
  const title = String(note?.title || "").trim();
  if (title) return title;
  const first = String(note?.content || "").trim().split("\n")[0].replace(/^#+\s*/, "");
  return first || t("Без названия");
}

function installDayNotes(date, notes, activeId = null){
  const day = state.days[date] || normalizeDay({ date, notes:[] });
  day.notes = sortDayNotes(notes);
  const primary = day.notes[0] || null;
  day.note = primary
    ? (primary.content.trim()
      ? primary.content
      : (String(primary.title || "").trim() ? `# ${String(primary.title || "").trim()}` : "# Без названия"))
    : null;
  state.days[date] = day;
  if (activeId != null) state.activeNoteByDate[date] = Number(activeId);
  else if (!day.notes.some(n => n.id === Number(state.activeNoteByDate[date]))) {
    state.activeNoteByDate[date] = day.notes[0]?.id ?? null;
  }
  nextDayLocalRevision(date);
}

async function persistNotesSnapshot(date){
  if (!state.offline?.cacheReady) return;
  const day = state.days[date] || normalizeDay({ date, notes:[] });
  await dataLayer.updateSnapshotDay(date, { ...day, date });
}

function renderDayNotes(){
  const date = state.selected;
  const list = $("noteList");
  const pane = $("noteEditorPane");
  if (!date || !list || !pane) return;
  const notes = notesOfDay(date);
  let active = activeDayNote(date);
  if (active && state.activeNoteByDate[date] !== active.id) state.activeNoteByDate[date] = active.id;

  list.innerHTML = "";
  if (!notes.length) {
    list.innerHTML = `<div class="dayNoteListEmpty">${esc(t("На этот день заметок пока нет."))}</div>`;
  } else {
    for (const note of notes) {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "dayNoteCard" + (active?.id === note.id ? " on" : "");
      button.setAttribute("aria-expanded", active?.id === note.id ? "true" : "false");
      const preview = String(note.content || "").trim().split("\n").slice(0,2).join(" ");
      button.innerHTML = `<span class="dayNoteCardPin">${note.pinned ? "📌" : ""}</span><span class="dayNoteCardText"><span class="dayNoteCardTitle">${esc(noteLabel(note))}</span><span class="dayNoteCardPreview">${esc(preview || t("Пустая заметка"))}</span></span><span class="dayNoteCardTime">${note.sortOrder + 1}</span>`;
      button.addEventListener("click", async () => {
        await flushPendingNoteSave();
        state.activeNoteByDate[date] = note.id;
        renderDayNotes();
        setTab(state.tab || "edit");
      });
      list.appendChild(button);
    }
  }

  active = activeDayNote(date);
  const online = navigator.onLine;
  pane.classList.toggle("is-empty", !active);
  $("noteAdd").disabled = !online;
  if ($("noteOfflineHint")) $("noteOfflineHint").textContent = online
    ? t("Несколько независимых заметок на один день.")
    : t("Редактирование доступно оффлайн и синхронизируется позже.");
  if (!active) {
    $("noteTitle").value = "";
    $("noteEdit").value = "";
    $("notePrev").innerHTML = "";
    return;
  }
  if (document.activeElement !== $("noteTitle")) $("noteTitle").value = active.title || "";
  if (document.activeElement !== $("noteEdit")) $("noteEdit").value = active.content || "";
  $("noteTitle").disabled = false;
  $("noteEdit").readOnly = false;
  $("notePin").disabled = !online;
  $("noteDelete").disabled = !online;
  $("notePin").classList.toggle("on", active.pinned);
  $("notePin").title = t(active.pinned ? "Открепить заметку" : "Закрепить заметку");
  const sameGroup = notes.filter(n => n.pinned === active.pinned);
  const idx = sameGroup.findIndex(n => n.id === active.id);
  $("noteMoveUp").disabled = !online || idx <= 0;
  $("noteMoveDown").disabled = !online || idx < 0 || idx >= sameGroup.length - 1;
  updateAccSummaries();
}

async function createDayNote(content = ""){
  const date = state.selected;
  if (!date || !navigator.onLine) return setSave("err", t("Создание заметки требует подключения к серверу"));
  await flushPendingNoteSave();
  try {
    setSave("saving");
    const created = normalizeDayNote(await api.createDayNote({ date, title:content ? null : t("Новая заметка"), content, pinned:false }));
    installDayNotes(date, [...notesOfDay(date), created], created.id);
    await persistNotesSnapshot(date);
    renderDayNotes();
    renderCalendar();
    setTab("edit");
    $("noteTitle")?.focus({ preventScroll:true });
    setSave("saved");
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}

function scheduleNoteSave(note, patch){
  clearTimeout(noteTimer);
  const sameNote = pendingNoteSave
    && pendingNoteSave.date === state.selected
    && Number(pendingNoteSave.noteId) === Number(note.id);
  pendingNoteSave = {
    date:state.selected,
    noteId:note.id,
    // Title and content inputs may fire inside the same debounce window. Merge
    // their patches so the later keystroke never silently drops the earlier one.
    patch:{ ...(sameNote ? pendingNoteSave.patch : {}), ...patch }
  };
  noteTimer = setTimeout(() => flushPendingNoteSave(), 600);
}

async function flushPendingNoteSave(){
  if (!pendingNoteSave) return;
  clearTimeout(noteTimer);
  noteTimer = null;
  const pending = pendingNoteSave;
  pendingNoteSave = null;
  try {
    setSave("saving");
    const result = await dataLayer.updateDayNote(pending.noteId, pending.patch, pending.date);
    if (result.note) {
      const updated = normalizeDayNote(result.note);
      const current = notesOfDay(pending.date).map(n => n.id === updated.id ? updated : n);
      installDayNotes(pending.date, current, updated.id);
    }
    await persistNotesSnapshot(pending.date);
    renderDayNotes();
    renderCalendar();
    setSave(result.queued ? "saved" : "saved", result.queued ? t("Оффлайн-правка поставлена в очередь") : undefined);
  } catch (err) {
    console.error(err);
    setSave("err", err.message);
  }
}

async function flushPendingSave(){ await flushPendingNoteSave(); }

$("noteAdd").addEventListener("click", () => createDayNote(""));
$("noteEdit").addEventListener("input", () => {
  if (!moduleEnabled("notes")) return;
  const date = state.selected;
  const active = activeDayNote(date);
  if (!date || !active) return;
  active.content = $("noteEdit").value;
  installDayNotes(date, notesOfDay(date).map(n => n.id === active.id ? active : n), active.id);
  updateAccSummaries();
  renderCalendar();
  scheduleNoteSave(active, { content:active.content });
});
$("noteTitle").addEventListener("input", () => {
  const date = state.selected;
  const active = activeDayNote(date);
  if (!date || !active) return;
  active.title = $("noteTitle").value;
  installDayNotes(date, notesOfDay(date).map(n => n.id === active.id ? active : n), active.id);
  renderDayNotes();
  scheduleNoteSave(active, { title:active.title });
});
$("notePin").addEventListener("click", async () => {
  const date = state.selected;
  const active = activeDayNote(date);
  if (!active || !navigator.onLine) return setSave("err", t("Закрепление требует подключения к серверу"));
  await flushPendingNoteSave();
  try {
    const updated = normalizeDayNote(await api.updateDayNote(active.id, { pinned:!active.pinned }));
    installDayNotes(date, notesOfDay(date).map(n => n.id === updated.id ? updated : n), updated.id);
    await persistNotesSnapshot(date);
    renderDayNotes(); renderCalendar(); setSave("saved");
  } catch (err) { console.error(err); setSave("err", err.message); }
});
async function moveActiveNote(direction){
  const date = state.selected;
  const active = activeDayNote(date);
  if (!active || !navigator.onLine) return setSave("err", t("Изменение порядка требует подключения к серверу"));
  await flushPendingNoteSave();
  try {
    const moved = (await api.moveDayNote(active.id, direction)).map(normalizeDayNote);
    installDayNotes(date, moved, active.id);
    await persistNotesSnapshot(date);
    renderDayNotes(); setSave("saved");
  } catch (err) { console.error(err); setSave("err", err.message); }
}
$("noteMoveUp").addEventListener("click", () => moveActiveNote("UP"));
$("noteMoveDown").addEventListener("click", () => moveActiveNote("DOWN"));
$("noteDelete").addEventListener("click", async () => {
  const date = state.selected;
  const active = activeDayNote(date);
  if (!active || !navigator.onLine) return setSave("err", t("Удаление требует подключения к серверу"));
  if (!confirm(t(`Удалить заметку «${noteLabel(active)}»?`))) return;
  await flushPendingNoteSave();
  try {
    await api.deleteDayNote(active.id);
    const remaining = notesOfDay(date).filter(n => n.id !== active.id);
    installDayNotes(date, remaining, remaining[0]?.id ?? null);
    await persistNotesSnapshot(date);
    renderDayNotes(); renderCalendar(); setSave("saved");
  } catch (err) { console.error(err); setSave("err", err.message); }
});

let noteSearchTimer = null;
async function runNoteSearch(){
  const input = $("noteSearch");
  const box = $("noteSearchResults");
  if (!input || !box) return;
  const q = input.value.trim();
  if (q.length < 2) { box.hidden = true; box.innerHTML = ""; return; }
  if (!navigator.onLine) {
    const local = Object.values(state.days || {}).flatMap(day => notesOfDay(day.date || "").map(note => ({ ...note, date:day.date })))
      .filter(note => `${note.title || ""} ${note.content || ""}`.toLowerCase().includes(q.toLowerCase())).slice(0,50);
    renderNoteSearchResults(local); return;
  }
  try { renderNoteSearchResults(await api.searchNotes(q, "", "", 50)); }
  catch (err) { console.error(err); box.hidden = false; box.innerHTML = `<div class="emptyLine">${esc(err.message)}</div>`; }
}
function renderNoteSearchResults(items){
  const box = $("noteSearchResults");
  if (!box) return;
  box.hidden = false; box.innerHTML = "";
  if (!items?.length) { box.innerHTML = `<div class="emptyLine">${esc(t("По заметкам ничего не найдено."))}</div>`; return; }
  for (const raw of items) {
    const note = normalizeDayNote(raw); note.date = raw.date;
    const btn = document.createElement("button"); btn.type = "button"; btn.className = "noteSearchResult";
    const preview = String(note.content || "").replace(/\s+/g," ").slice(0,180);
    btn.innerHTML = `<time>${esc(formatDateHuman(note.date))}</time><span><b>${esc(noteLabel(note))}</b><small>${esc(preview || t("Пустая заметка"))}</small></span>`;
    btn.addEventListener("click", async () => {
      await flushPendingNoteSave();
      const [y,m] = String(note.date).split("-").map(Number);
      await goto(y, m - 1); location.hash = "#calendar"; selectDay(note.date);
      state.activeNoteByDate[note.date] = note.id;
      const acc = $("accNote"); if (acc) acc.open = true;
      box.hidden = true; renderDayNotes(); $("noteEdit")?.focus({ preventScroll:true });
    });
    box.appendChild(btn);
  }
}
$("noteSearch")?.addEventListener("input", () => { clearTimeout(noteSearchTimer); noteSearchTimer = setTimeout(runNoteSearch, 250); });
$("noteSearch")?.addEventListener("keydown", e => { if (e.key === "Escape") { e.currentTarget.value = ""; $("noteSearchResults").hidden = true; } });

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
window.addEventListener("online", () => { if (state.selected) renderDayNotes(); });
window.addEventListener("offline", () => { if (state.selected) renderDayNotes(); });
$("pClose").addEventListener("click", () => selectDay(null));
