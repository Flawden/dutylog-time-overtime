/*
 * 38-schedule-layers.js — Schedule Templates & Calendar Layers
 *
 * Reusable cycles are authoritative backend resources. Companion layers are
 * read-only calendar projections and never mutate the owner's own dated shifts.
 */

"use strict";

Object.assign(I18N_EN, {
  "Шаблоны графика":"Schedule templates",
  "Слои календаря":"Calendar layers",
  "будет записано":"will be written",
  "без изменений":"unchanged",
  "пропущено":"skipped",
  "конфликтов":"conflicts",
  "записать":"write",
  "заменить":"replace",
  "уже совпадает":"already matches",
  "пропустить":"skip",
  "проверяю график…":"Checking schedule…",
  "предпросмотр готов":"Preview ready",
  "предпросмотр готов — есть конфликты":"Preview ready — conflicts found",
  "заполняю график…":"Applying schedule…",
  "цикл от выбранной даты":"cycle from selected date",
  "привязка к дням недели":"weekday-aligned",
  "Занятые дни по умолчанию не перезаписываются.":"Occupied days are skipped by default.",
  "Шаблоны графика ещё не загружены.":"Schedule templates are not loaded yet.",
  "Шаблон":"Template",
  "Слой":"Layer",
  "виден":"visible",
  "скрыт":"hidden",
  "только чтение":"read only",
  "шаблон сохранён":"template saved",
  "слой сохранён":"layer saved",
  "шаблон удалён":"template deleted",
  "слой удалён":"layer deleted",
  "нужен хотя бы один элемент цикла":"add at least one cycle step",
  "Выберите смену":"Select shift",
  "И ещё":"And",
  "весь день":"all day",
  "Встроенный шаблон откроется как пользовательская копия.":"The built-in preset will open as a user-owned copy.",
  "Добавьте первый слой близкого человека.":"Add the first companion calendar layer.",
  "Удалить календарный слой?":"Delete this calendar layer?",
  "Удалить пользовательский шаблон?":"Delete this user template?",
  "Слои календаря":"Calendar layers",
  "Шаблон графика":"Schedule template",
  "Загрузка шаблонов…":"Loading templates…",
  "перезаписывать занятые дни":"overwrite occupied days",
  "Предпросмотр":"Preview",
  "Применить безопасно":"Apply safely",
  "По умолчанию занятые дни пропускаются. Перед применением DutyLog покажет конфликты.":"Occupied days are skipped by default. DutyLog shows conflicts before applying.",
  "Графики":"Schedules",
  "циклы и календарные слои":"cycles and calendar layers",
  "Шаблоны и календарные слои":"Templates and calendar layers",
  "Соберите повторяющийся цикл, сначала просмотрите результат, затем заполните диапазон. Слои близких отображаются рядом с вашим графиком и остаются только для чтения.":"Build a repeating cycle, preview it, then fill a date range. Companion layers appear beside your schedule and stay read-only.",
  "До 64 элементов в цикле.":"Up to 64 steps per cycle.",
  "＋ Новый":"＋ New",
  "Привязка":"Alignment",
  "от опорной даты":"from anchor date",
  "по дням недели (7 шагов)":"by weekday (7 steps)",
  "Добавить смену":"Add shift",
  "＋ В цикл":"＋ Add to cycle",
  "Сохранить шаблон":"Save template",
  "Например: мама, отец, напарник.":"For example: mother, father, teammate.",
  "Опорная дата":"Anchor date",
  "Начало показа":"Show from",
  "Остановить после":"Stop after",
  "показывать слой":"show layer",
  "Сохранить слой":"Save layer"
});
Object.assign(I18N_RU, Object.fromEntries(Object.entries(I18N_EN).map(([ru,en]) => [en, ru])));

let scheduleTemplateDraftSteps = [];

function calendarLayerEntriesForDate(key){
  return state.calendarLayerEntriesByDate?.[key] || [];
}
function calendarLayerTime(entry, field){
  return String(entry?.[field] || "").slice(11, 16);
}
function calendarLayerSegmentMinute(entry, field, key, boundary){
  const value = String(entry?.[field] || "");
  if (!value) return null;
  const date = value.slice(0, 10);
  if (date < key) return 0;
  if (date > key) return boundary === "end" ? 1440 : null;
  return calendarExperienceTimeMinutes(value.slice(11, 16));
}
function calendarLayerRange(entry){
  if (!entry?.timed) return t("весь день");
  const key = entry.date;
  const startMinute = calendarLayerSegmentMinute(entry, "displayStart", key, "start");
  const endMinute = calendarLayerSegmentMinute(entry, "displayEnd", key, "end");
  const label = minute => minute === 1440 ? "24:00" : `${String(Math.floor(minute / 60)).padStart(2,"0")}:${String(minute % 60).padStart(2,"0")}`;
  return [startMinute, endMinute].filter(value => value != null).map(label).join("–");
}
function renderCalendarLayerBar(){
  const bar = $("calendarLayerBar");
  if (!bar) return;
  const layers = state.calendarLayers || [];
  bar.hidden = !layers.length;
  bar.classList.toggle("hasMany", layers.length > 1);
  bar.dataset.label = t("Слои");
  bar.innerHTML = "";
  for (const layer of layers) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "calendarLayerToggle";
    button.classList.toggle("isHidden", !layer.visible);
    button.setAttribute("aria-pressed", String(!!layer.visible));
    button.setAttribute("aria-label", `${layer.name}: ${layer.visible ? t("виден") : t("скрыт")}`);
    button.style.setProperty("--layer-color", layer.color || "#7AB8FF");
    button.innerHTML = `<i></i><span>${esc(layer.name)}</span><small aria-hidden="true">${layer.visible ? "●" : "○"}</small>`;
    button.title = `${layer.name} · ${layer.visible ? t("виден") : t("скрыт")} · ${layer.templateName || ""} · ${layer.timezone || ""} · ${t("только чтение")}`;
    button.addEventListener("click", async () => {
      button.disabled = true;
      try {
        await api.updateCalendarLayer(layer.id, { visible:!layer.visible });
        await refreshScheduleLayers({ freshCalendar:true });
      } catch (error) {
        console.error(error);
        setSave("err", error.message);
      } finally { button.disabled = false; }
    });
    bar.appendChild(button);
  }
}
function renderCalendarLayerMonthChips(){
  document.querySelectorAll("#grid [data-date]").forEach(cell => {
    cell.querySelectorAll(":scope > .calendarLayerStack").forEach(node => node.remove());
    const entries = calendarLayerEntriesForDate(cell.dataset.date);
    if (!entries.length) return;
    const stack = document.createElement("span");
    stack.className = "calendarLayerStack";
    for (const entry of entries.slice(0, 3)) {
      const chip = document.createElement("span");
      chip.className = "calendarLayerChip";
      chip.style.setProperty("--layer-color", entry.layerColor || entry.layer?.color || "#7AB8FF");
      chip.title = `${entry.layerName}: ${entry.shiftTypeName}${entry.timed ? ` · ${calendarLayerRange(entry)}` : ""}`;
      chip.innerHTML = `<i></i><b>${esc(entry.layerName)}</b><em>${esc(entry.shiftTypeName)}</em>`;
      stack.appendChild(chip);
    }
    if (entries.length > 3) {
      const more = document.createElement("span");
      more.className = "calendarLayerMore";
      more.textContent = `+${entries.length - 3}`;
      stack.appendChild(more);
    }
    cell.appendChild(stack);
  });
}

const scheduleLayersBaseTimelineEvents = calendarExperienceTimelineEvents;
calendarExperienceTimelineEvents = function calendarExperienceTimelineEventsWithLayers(key){
  const events = scheduleLayersBaseTimelineEvents(key);
  for (const entry of calendarLayerEntriesForDate(key).filter(item => item.timed)) {
    const start = calendarLayerSegmentMinute(entry, "displayStart", key, "start");
    const end = calendarLayerSegmentMinute(entry, "displayEnd", key, "end");
    if (start == null) continue;
    events.push({
      type:"layer",
      start,
      end:end != null && end > start ? end : Math.min(1440, start + 60),
      color:entry.layerColor || "#7AB8FF",
      title:`${entry.layerName}: ${entry.shiftTypeName}`,
      meta:`${calendarLayerRange(entry)} · ${entry.sourceTimezone}`,
      layerEntry:entry,
    });
  }
  return events.sort((a,b) => a.start - b.start || a.end - b.end);
};

const scheduleLayersBaseAllDay = calendarExperienceRenderAllDay;
calendarExperienceRenderAllDay = function calendarExperienceRenderAllDayWithLayers(key){
  scheduleLayersBaseAllDay(key);
  const entries = calendarLayerEntriesForDate(key).filter(item => !item.timed || item.dayOff);
  if (!entries.length) return;
  const box = $("calendarAllDay");
  if (!box) return;
  box.hidden = false;
  let list = box.querySelector(".calendarAllDayItems");
  if (!list) {
    box.innerHTML = `<div class="calendarAllDayHead"><span>${esc(state.language === "en" ? "All day" : "Весь день")}</span><small>0</small></div><div class="calendarAllDayItems"></div>`;
    list = box.querySelector(".calendarAllDayItems");
  }
  for (const entry of entries) {
    const item = document.createElement("span");
    item.className = "calendarAllDayItem layer";
    item.style.setProperty("--event-color", entry.layerColor || "#7AB8FF");
    item.innerHTML = `<span aria-hidden="true">◫</span><b>${esc(`${entry.layerName}: ${entry.shiftTypeName}`)}</b>`;
    item.title = `${entry.sourceTimezone} · ${t("только чтение")}`;
    list.appendChild(item);
  }
  const count = box.querySelector(".calendarAllDayHead small");
  if (count) count.textContent = String(list.children.length);
};

const scheduleLayersBaseWeek = calendarExperienceRenderWeek;
calendarExperienceRenderWeek = function calendarExperienceRenderWeekWithLayers(){
  scheduleLayersBaseWeek();
  const focus = calendarExperienceFocusDate();
  const list = $("calendarWeekAgenda")?.querySelector(".calendarWeekAgendaList");
  if (!list) return;
  for (const entry of calendarLayerEntriesForDate(focus)) {
    const row = document.createElement("div");
    row.className = "calendarAgendaRow calendarLayerAgendaRow";
    row.style.setProperty("--event-color", entry.layerColor || "#7AB8FF");
    row.innerHTML = `<span>◫</span><span><b>${esc(`${entry.layerName}: ${entry.shiftTypeName}`)}</b><small>${esc(`${calendarLayerRange(entry)} · ${entry.sourceTimezone}`)}</small></span><i>${esc(t("только чтение"))}</i>`;
    list.appendChild(row);
  }
};

const scheduleLayersBaseRenderCalendar = renderCalendar;
renderCalendar = function renderCalendarWithScheduleLayers(){
  scheduleLayersBaseRenderCalendar();
  if (document.documentElement.dataset.vueCalendarTimeline === "ready") return;
  renderCalendarLayerBar();
  if (state.calendarExperience?.mode === "month") renderCalendarLayerMonthChips();
};

function setScheduleSettingsStatus(text = "", tone = ""){
  const node = $("scheduleSettingsStatus");
  if (!node) return;
  node.textContent = text || "—";
  node.className = `status ${tone}`.trim();
}
function scheduleStepLabel(step){
  const shift = (state.shiftTypes || []).find(item => Number(item.id) === Number(step.shiftTypeId));
  return shift ? shiftDisplayName(shift) : (step.shiftTypeName || `#${step.shiftTypeId}`);
}
function renderScheduleStepList(){
  const list = $("scheduleStepList");
  if (!list) return;
  list.innerHTML = "";
  scheduleTemplateDraftSteps.forEach((step, index) => {
    const shift = (state.shiftTypes || []).find(item => Number(item.id) === Number(step.shiftTypeId));
    const row = document.createElement("div");
    row.className = "scheduleStepRow";
    row.innerHTML = `<i style="background:${esc(shift?.color || step.shiftColor || "#7AB8FF")}"></i><b>${index + 1}</b><span>${esc(scheduleStepLabel(step))}</span><div><button type="button" data-move="up" ${index === 0 ? "disabled" : ""}>↑</button><button type="button" data-move="down" ${index === scheduleTemplateDraftSteps.length - 1 ? "disabled" : ""}>↓</button><button type="button" data-remove="true">×</button></div>`;
    row.querySelector('[data-move="up"]')?.addEventListener("click", () => {
      [scheduleTemplateDraftSteps[index - 1], scheduleTemplateDraftSteps[index]] = [scheduleTemplateDraftSteps[index], scheduleTemplateDraftSteps[index - 1]];
      renderScheduleStepList();
    });
    row.querySelector('[data-move="down"]')?.addEventListener("click", () => {
      [scheduleTemplateDraftSteps[index + 1], scheduleTemplateDraftSteps[index]] = [scheduleTemplateDraftSteps[index], scheduleTemplateDraftSteps[index + 1]];
      renderScheduleStepList();
    });
    row.querySelector('[data-remove="true"]')?.addEventListener("click", () => {
      scheduleTemplateDraftSteps.splice(index, 1);
      renderScheduleStepList();
    });
    list.appendChild(row);
  });
  if (!scheduleTemplateDraftSteps.length) list.innerHTML = `<div class="scheduleEmpty">${esc(t("нужен хотя бы один элемент цикла"))}</div>`;
}
function populateScheduleSelects(){
  const shiftSelect = $("scheduleStepShift");
  if (shiftSelect) {
    const current = shiftSelect.value;
    shiftSelect.innerHTML = `<option value="">${esc(t("Выберите смену"))}</option>`;
    for (const shift of state.shiftTypes || []) {
      const option = document.createElement("option");
      option.value = shift.id;
      option.textContent = shiftDisplayName(shift);
      shiftSelect.appendChild(option);
    }
    if (current) shiftSelect.value = current;
  }
  const templateSelect = $("calendarLayerTemplate");
  if (templateSelect) {
    const current = templateSelect.value;
    templateSelect.innerHTML = "";
    for (const template of state.scheduleTemplates || []) {
      const option = document.createElement("option");
      option.value = template.id;
      option.textContent = template.name;
      templateSelect.appendChild(option);
    }
    if (current && [...templateSelect.options].some(option => option.value === current)) templateSelect.value = current;
  }
  if (typeof populateTimeZoneSelect === "function") {
    populateTimeZoneSelect("calendarLayerTimezone", $("calendarLayerTimezone")?.value || state.timeSettings?.workTimezone || browserTimeZone());
  }
}
function renderScheduleTemplateList(){
  const list = $("scheduleTemplateList");
  if (!list) return;
  list.innerHTML = "";
  for (const template of state.scheduleTemplates || []) {
    const card = document.createElement("button");
    card.type = "button";
    card.className = "scheduleTemplateCard";
    card.innerHTML = `<span><b>${esc(template.name)}</b><small>${esc(template.description || (template.alignmentMode === "WEEKDAY" ? t("привязка к дням недели") : t("цикл от выбранной даты")))}</small></span><em>${template.steps?.length || 0}</em><i>${template.systemPreset ? "preset" : "edit"}</i>`;
    card.addEventListener("click", () => openScheduleTemplateEditor(template));
    list.appendChild(card);
  }
}
function renderCalendarLayerList(){
  const list = $("calendarLayerList");
  if (!list) return;
  list.innerHTML = "";
  for (const layer of state.calendarLayers || []) {
    const card = document.createElement("div");
    card.className = "calendarLayerCard";
    card.style.setProperty("--layer-color", layer.color || "#7AB8FF");
    card.innerHTML = `<i></i><button type="button" class="calendarLayerCardMain"><b>${esc(layer.name)}</b><small>${esc(`${layer.templateName} · ${layer.timezone}`)}</small></button><label><input type="checkbox" ${layer.visible ? "checked" : ""}/> ${esc(layer.visible ? t("виден") : t("скрыт"))}</label>`;
    card.querySelector(".calendarLayerCardMain")?.addEventListener("click", () => openCalendarLayerEditor(layer));
    card.querySelector("input")?.addEventListener("change", async event => {
      try {
        await api.updateCalendarLayer(layer.id, { visible:event.target.checked });
        await refreshScheduleLayers({ freshCalendar:true });
      } catch (error) { setSave("err", error.message); }
    });
    list.appendChild(card);
  }
  if (!state.calendarLayers?.length) list.innerHTML = `<div class="scheduleEmpty">${esc(t("Добавьте первый слой близкого человека."))}</div>`;
}
function renderScheduleLayerSettings(){
  populateScheduleSelects();
  renderScheduleTemplateList();
  renderCalendarLayerList();
  renderScheduleControls();
}
function openScheduleTemplateEditor(template = null){
  const form = $("scheduleTemplateForm");
  if (!form) return;
  const copyPreset = !!template?.systemPreset;
  $("scheduleTemplateId").value = copyPreset ? "" : (template?.id || "");
  $("scheduleTemplateName").value = template ? `${copyPreset ? `${state.language === "en" ? "Copy" : "Копия"}: ` : ""}${template.name}` : "";
  $("scheduleTemplateDescription").value = template?.description || "";
  $("scheduleTemplateAlignment").value = template?.alignmentMode || "CYCLE_START";
  scheduleTemplateDraftSteps = (template?.steps || []).map(step => ({ shiftTypeId:step.shiftTypeId, shiftTypeName:step.shiftTypeName, shiftColor:step.shiftColor }));
  $("scheduleTemplateDelete").hidden = !template || copyPreset;
  $("scheduleTemplateMessage").textContent = copyPreset ? t("Встроенный шаблон откроется как пользовательская копия.") : "";
  form.hidden = false;
  renderScheduleStepList();
  $("scheduleTemplateName").focus();
}
function closeScheduleTemplateEditor(){
  $("scheduleTemplateForm").hidden = true;
  scheduleTemplateDraftSteps = [];
}
function openCalendarLayerEditor(layer = null){
  const form = $("calendarLayerForm");
  if (!form) return;
  populateScheduleSelects();
  $("calendarLayerId").value = layer?.id || "";
  $("calendarLayerName").value = layer?.name || "";
  $("calendarLayerColor").value = layer?.color || "#7AB8FF";
  if (typeof populateTimeZoneSelect === "function") populateTimeZoneSelect("calendarLayerTimezone", layer?.timezone || state.timeSettings?.workTimezone || browserTimeZone());
  $("calendarLayerTemplate").value = layer?.templateId || state.scheduleTemplates?.[0]?.id || "";
  const today = todayKey();
  $("calendarLayerAnchor").value = layer?.anchorDate || today;
  $("calendarLayerStart").value = layer?.startDate || today;
  $("calendarLayerEnd").value = layer?.endDate || "";
  $("calendarLayerVisible").checked = layer?.visible ?? true;
  $("calendarLayerDelete").hidden = !layer;
  $("calendarLayerMessage").textContent = "";
  form.hidden = false;
  $("calendarLayerName").focus();
}
function closeCalendarLayerEditor(){ $("calendarLayerForm").hidden = true; }

async function refreshScheduleLayers({ freshCalendar = false } = {}){
  const [templates, layers] = await Promise.all([api.scheduleTemplates(), api.calendarLayers()]);
  state.scheduleTemplates = templates || [];
  state.calendarLayers = layers || [];
  renderScheduleLayerSettings();
  if (freshCalendar) await loadMonth({ fresh:true });
  else renderCalendar();
}

function initScheduleLayerEvents(){
  $("scheduleTemplateNew")?.addEventListener("click", () => openScheduleTemplateEditor());
  $("scheduleTemplateCancel")?.addEventListener("click", closeScheduleTemplateEditor);
  $("scheduleStepAdd")?.addEventListener("click", () => {
    const id = Number($("scheduleStepShift").value || 0);
    if (!id) return;
    const shift = (state.shiftTypes || []).find(item => Number(item.id) === id);
    scheduleTemplateDraftSteps.push({ shiftTypeId:id, shiftTypeName:shift?.name, shiftColor:shift?.color });
    renderScheduleStepList();
  });
  $("scheduleTemplateForm")?.addEventListener("submit", async event => {
    event.preventDefault();
    if (!scheduleTemplateDraftSteps.length) return $("scheduleTemplateMessage").textContent = t("нужен хотя бы один элемент цикла");
    const id = Number($("scheduleTemplateId").value || 0);
    const payload = {
      name:$("scheduleTemplateName").value.trim(),
      description:$("scheduleTemplateDescription").value.trim(),
      alignmentMode:$("scheduleTemplateAlignment").value,
      shiftTypeIds:scheduleTemplateDraftSteps.map(step => step.shiftTypeId),
    };
    try {
      if (id) await api.updateScheduleTemplate(id, payload);
      else await api.createScheduleTemplate(payload);
      closeScheduleTemplateEditor();
      await refreshScheduleLayers({ freshCalendar:true });
      setScheduleSettingsStatus(t("шаблон сохранён"), "saved");
    } catch (error) { $("scheduleTemplateMessage").textContent = error.message; }
  });
  $("scheduleTemplateDelete")?.addEventListener("click", async () => {
    const id = Number($("scheduleTemplateId").value || 0);
    if (!id || !window.confirm(t("Удалить пользовательский шаблон?"))) return;
    try {
      await api.deleteScheduleTemplate(id);
      closeScheduleTemplateEditor();
      await refreshScheduleLayers({ freshCalendar:true });
      setScheduleSettingsStatus(t("шаблон удалён"), "saved");
    } catch (error) { $("scheduleTemplateMessage").textContent = error.message; }
  });

  $("calendarLayerNew")?.addEventListener("click", () => openCalendarLayerEditor());
  $("calendarLayerCancel")?.addEventListener("click", closeCalendarLayerEditor);
  $("calendarLayerForm")?.addEventListener("submit", async event => {
    event.preventDefault();
    const id = Number($("calendarLayerId").value || 0);
    const payload = {
      name:$("calendarLayerName").value.trim(),
      color:$("calendarLayerColor").value,
      timezone:$("calendarLayerTimezone").value,
      visible:$("calendarLayerVisible").checked,
      templateId:Number($("calendarLayerTemplate").value),
      anchorDate:$("calendarLayerAnchor").value,
      startDate:$("calendarLayerStart").value,
      endDate:$("calendarLayerEnd").value,
      clearEndDate:id > 0 && !$("calendarLayerEnd").value,
    };
    try {
      if (id) await api.updateCalendarLayer(id, payload);
      else await api.createCalendarLayer(payload);
      closeCalendarLayerEditor();
      await refreshScheduleLayers({ freshCalendar:true });
      setScheduleSettingsStatus(t("слой сохранён"), "saved");
    } catch (error) { $("calendarLayerMessage").textContent = error.message; }
  });
  $("calendarLayerDelete")?.addEventListener("click", async () => {
    const id = Number($("calendarLayerId").value || 0);
    if (!id || !window.confirm(t("Удалить календарный слой?"))) return;
    try {
      await api.deleteCalendarLayer(id);
      closeCalendarLayerEditor();
      await refreshScheduleLayers({ freshCalendar:true });
      setScheduleSettingsStatus(t("слой удалён"), "saved");
    } catch (error) { $("calendarLayerMessage").textContent = error.message; }
  });
}

initScheduleLayerEvents();
