/* 55-calendar-sync.js — .ics range export and private read-only subscription UI. */
Object.assign(I18N_EN, {
  "Внешний календарь":"External calendar",
  ".ics и read-only подписка":".ics and read-only subscription",
  "Интеграции":"Integrations",
  "Разовый экспорт":"One-time export",
  "С даты":"From",
  "По дату":"To",
  "Скачать .ics":"Download .ics",
  "Приватная подписка":"Private subscription",
  "Ссылка ещё не создана.":"No subscription link has been created yet.",
  "Скопируйте ссылку сейчас":"Copy the link now",
  "Копировать":"Copy",
  "Создать подписку":"Create subscription",
  "Выпустить новую ссылку":"Rotate link",
  "Отозвать ссылку":"Revoke link",
  "не настроено":"not configured",
  "активна":"active",
  "Ссылка скопирована":"Link copied",
  "Новая ссылка создана. Скопируйте её сейчас.":"A new link was created. Copy it now.",
  "Подписка отозвана":"Subscription revoked",
  "Отозвать приватную календарную ссылку? Подключённые внешние календари перестанут обновляться.":"Revoke the private calendar link? Connected external calendars will stop updating.",
  "Укажите корректный диапазон дат.":"Choose a valid date range.",
  "Токен":"Token",
  "окно":"window",
  "дней назад":"days back",
  "дней вперёд":"days ahead"
});
Object.assign(I18N_RU, Object.fromEntries(Object.entries(I18N_EN).map(([ru,en]) => [en, ru])));

function calendarSyncDefaultRange(){
  const start = new Date(state.y, state.m, 1);
  const end = new Date(state.y, state.m + 1, 0);
  return { from:localDateKey(start), to:localDateKey(end) };
}

function initCalendarSyncRange(){
  const range = calendarSyncDefaultRange();
  if ($("calendarExportFrom") && !$("calendarExportFrom").value) $("calendarExportFrom").value = range.from;
  if ($("calendarExportTo") && !$("calendarExportTo").value) $("calendarExportTo").value = range.to;
}

function renderCalendarSync(){
  const data = state.calendarSync;
  const status = $("calendarSyncStatus");
  const summary = $("calendarSyncSummary");
  const issue = $("calendarSyncIssue");
  const revoke = $("calendarSyncRevoke");
  const secret = $("calendarSyncSecret");
  const input = $("calendarSyncUrl");
  if (!status || !summary || !issue || !revoke || !secret || !input) return;
  const active = !!data?.active;
  status.textContent = t(active ? "активна" : "не настроено");
  status.className = `status${active ? " good" : ""}`;
  summary.textContent = active
    ? `${t("Токен")}: ${data.tokenHint || "—"} · ${t("окно")}: ${data.feedPastDays} ${t("дней назад")}, ${data.feedFutureDays} ${t("дней вперёд")}`
    : t("Ссылка ещё не создана.");
  issue.textContent = t(active ? "Выпустить новую ссылку" : "Создать подписку");
  revoke.hidden = !active;
  secret.hidden = !state.calendarSyncIssuedUrl;
  input.value = state.calendarSyncIssuedUrl || "";
}

async function loadCalendarSyncStatus(force = false){
  if (!moduleEnabled("calendar_sync")) return;
  if (state.calendarSync && !force) return renderCalendarSync();
  try {
    state.calendarSync = await api.calendarSyncStatus();
    renderCalendarSync();
  } catch (err) {
    console.warn("calendar sync status unavailable", err);
    if ($("calendarSyncMsg")) setProfileMsg("calendarSyncMsg", err.message || t("ошибка"));
  }
}

$("calendarExportRange")?.addEventListener("click", () => {
  const from = $("calendarExportFrom")?.value;
  const to = $("calendarExportTo")?.value;
  if (!from || !to || to < from) return setProfileMsg("calendarSyncMsg", t("Укажите корректный диапазон дат."));
  setProfileMsg("calendarSyncMsg", "", true);
  window.location.href = `/api/calendar-sync/export?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`;
});

$("calendarSyncIssue")?.addEventListener("click", async () => {
  const button = $("calendarSyncIssue");
  button.disabled = true;
  setProfileMsg("calendarSyncMsg", t("сохраняю…"), true);
  try {
    const issued = await api.issueCalendarSubscription();
    state.calendarSync = issued;
    state.calendarSyncIssuedUrl = issued.subscriptionUrl;
    renderCalendarSync();
    setProfileMsg("calendarSyncMsg", t("Новая ссылка создана. Скопируйте её сейчас."), true);
  } catch (err) {
    setProfileMsg("calendarSyncMsg", err.message || t("ошибка"));
  } finally {
    button.disabled = false;
  }
});

$("calendarSyncRevoke")?.addEventListener("click", async () => {
  if (!confirm(t("Отозвать приватную календарную ссылку? Подключённые внешние календари перестанут обновляться."))) return;
  try {
    await api.revokeCalendarSubscription();
    state.calendarSync = { active:false, feedPastDays:state.calendarSync?.feedPastDays || 30, feedFutureDays:state.calendarSync?.feedFutureDays || 335, entities:[] };
    state.calendarSyncIssuedUrl = null;
    renderCalendarSync();
    setProfileMsg("calendarSyncMsg", t("Подписка отозвана"), true);
  } catch (err) {
    setProfileMsg("calendarSyncMsg", err.message || t("ошибка"));
  }
});

$("calendarSyncCopy")?.addEventListener("click", async () => {
  const value = $("calendarSyncUrl")?.value || "";
  if (!value) return;
  try {
    await navigator.clipboard.writeText(value);
    setProfileMsg("calendarSyncMsg", t("Ссылка скопирована"), true);
  } catch (_) {
    $("calendarSyncUrl")?.select();
    document.execCommand("copy");
    setProfileMsg("calendarSyncMsg", t("Ссылка скопирована"), true);
  }
});

$("importantDetailsExportIcs")?.addEventListener("click", event => {
  event.preventDefault();
  event.stopPropagation();
  if (!state.viewingImportantDayId) return;
  window.location.href = `/api/calendar-sync/events/${encodeURIComponent(state.viewingImportantDayId)}.ics`;
});

initCalendarSyncRange();
renderCalendarSync();
ensureTranslationObserver();
