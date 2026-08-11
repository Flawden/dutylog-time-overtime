<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useSettingsWorkspaceStore } from "../stores/settingsWorkspaceStore";
import SettingsCard from "./SettingsCard.vue";

const props = defineProps<{ active: boolean }>();
const emit = defineEmits<{ open: [] }>();
const settings = useSettingsWorkspaceStore();
const { notificationSettings, notificationPreview } = storeToRefs(settings);

const browser = ref(false);
const shift = ref(false);
const shiftBefore = ref(60);
const digest = ref(false);
const digestTime = ref("19:00");
const tasks = ref(false);
const taskTime = ref("09:00");
const important = ref(false);
const importantDays = ref(1);
const importantTime = ref("09:00");

const language = computed(() => settings.language);
const text = computed(() => language.value === "en" ? {
  eyebrow: "Notifications", title: "Browser reminders and schedules", hint: "The server calculates reminder times; the browser/PWA displays them while DutyLog is running.", browser: "Browser notifications", shift: "before shift", before: "min before", digest: "evening digest", tasks: "tasks of the day", important: "important days", daysBefore: "days before", save: "Save settings", permission: "Allow in browser", test: "Test", month: "Current month", tomorrow: "Tomorrow", listMonth: "Reminders for current month", listTomorrow: "Reminders for tomorrow", empty: "No reminders in this range.", items: "items",
} : {
  eyebrow: "Уведомления", title: "Браузер и расписания", hint: "Сервер рассчитывает напоминания, а браузер/PWA показывает их, пока DutyLog запущен.", browser: "Уведомления браузера", shift: "перед сменой", before: "мин", digest: "вечерний дайджест", tasks: "задачи дня", important: "важные дни", daysBefore: "дн.", save: "Сохранить настройки", permission: "Разрешить в браузере", test: "Проверить", month: "Текущий месяц", tomorrow: "Завтра", listMonth: "Напоминания текущего месяца", listTomorrow: "Напоминания на завтра", empty: "На выбранный период напоминаний нет.", items: "шт",
});

const permissionLabel = computed(() => {
  if (!("Notification" in window)) return language.value === "en" ? "unsupported" : "нет API";
  return Notification.permission;
});
const listTitle = computed(() => settings.notificationPreviewMode === "tomorrow" ? text.value.listTomorrow : text.value.listMonth);

watch(notificationSettings, value => {
  if (!value) return;
  browser.value = value.browserNotificationsEnabled;
  shift.value = value.shiftRemindersEnabled;
  shiftBefore.value = value.shiftReminderMinutesBefore;
  digest.value = value.tomorrowDigestEnabled;
  digestTime.value = value.tomorrowDigestTime || "19:00";
  tasks.value = value.taskRemindersEnabled;
  taskTime.value = value.taskReminderTime || "09:00";
  important.value = value.importantDayRemindersEnabled;
  importantDays.value = value.importantDayDaysBefore;
  importantTime.value = value.importantDayReminderTime || "09:00";
}, { immediate: true, deep: true });

function reminderWhen(value: string): string {
  if (!value) return "—";
  const match = value.match(/T(\d{2}:\d{2})/);
  return match?.[1] ?? value;
}
async function save(): Promise<void> {
  await settings.saveNotificationSettings({
    browserNotificationsEnabled: browser.value,
    shiftRemindersEnabled: shift.value,
    shiftReminderMinutesBefore: Number(shiftBefore.value || 0),
    tomorrowDigestEnabled: digest.value,
    tomorrowDigestTime: digestTime.value || "19:00",
    taskRemindersEnabled: tasks.value,
    taskReminderTime: taskTime.value || "09:00",
    importantDayRemindersEnabled: important.value,
    importantDayDaysBefore: Number(importantDays.value || 0),
    importantDayReminderTime: importantTime.value || "09:00",
  });
}
async function requestPermission(): Promise<void> {
  if (!("Notification" in window)) return;
  const permission = await Notification.requestPermission();
  browser.value = permission === "granted";
  await save();
}
function testNotification(): void {
  if (!("Notification" in window) || Notification.permission !== "granted") return;
  new Notification("DutyLog: Time & Overtime", { body: language.value === "en" ? "Test notification sent." : "Тестовое уведомление отправлено." });
}
</script>

<template>
  <SettingsCard id="notifyCard" section="notifications" :active="props.active" :eyebrow="text.eyebrow" :title="text.title" :hint="text.hint" @open="emit('open')">
    <template #status><div id="notifyStatus" class="status notifyStatusChips"><span class="statusChip statusChipPrimary notifyCountChip"><b>{{ notificationPreview.length }}</b> {{ text.items }}</span><span class="statusChip"><b>Notification:</b> {{ permissionLabel }}</span></div></template>
    <div class="notifyGrid" data-vue-settings-native-section="notifications">
      <label class="notifyOpt"><input id="notifBrowser" v-model="browser" type="checkbox" /> <b>{{ text.browser }}</b></label>
      <label class="notifyOpt"><input id="notifShift" v-model="shift" type="checkbox" /> <b>{{ text.shift }}</b> <input id="notifShiftBefore" v-model.number="shiftBefore" max="1440" min="0" step="5" type="number" /> {{ text.before }}</label>
      <label class="notifyOpt"><input id="notifDigest" v-model="digest" type="checkbox" /> <b>{{ text.digest }}</b> <input id="notifDigestTime" v-model="digestTime" type="time" /></label>
      <label class="notifyOpt"><input id="notifTasks" v-model="tasks" type="checkbox" /> <b>{{ text.tasks }}</b> <input id="notifTaskTime" v-model="taskTime" type="time" /></label>
      <label class="notifyOpt"><input id="notifImportant" v-model="important" type="checkbox" /> <b>{{ text.important }}</b> <input id="notifImportantDays" v-model.number="importantDays" max="366" min="0" step="1" type="number" /> {{ text.daysBefore }} <input id="notifImportantTime" v-model="importantTime" type="time" /></label>
    </div>
    <div class="notifyPresets"><span>{{ text.shift }}:</span><button v-for="minutes in [15,30,60,90,120]" :key="minutes" :data-notif-shift-before="minutes" type="button" @click="shiftBefore = minutes">{{ minutes < 60 ? `${minutes} мин` : `${minutes / 60} ч` }}</button></div>
    <div class="notifyActions"><button id="notifSave" class="primary" type="button" @click="save">{{ text.save }}</button><button id="notifPermission" type="button" @click="requestPermission">{{ text.permission }}</button><button id="notifTest" type="button" @click="testNotification">{{ text.test }}</button><button id="notifRefresh" type="button" @click="settings.loadMonthNotifications()">{{ text.month }}</button><button id="notifTomorrow" type="button" @click="settings.loadTomorrowNotifications()">{{ text.tomorrow }}</button></div>
    <div id="notifyListTitle" class="notifyListTitle">{{ listTitle }}</div>
    <div id="notifyList" class="notifyList">
      <div v-if="!notificationPreview.length" class="notifyItem"><span class="notifyWhen">—</span><span class="notifyType">—</span><span class="notifyTitle"><span class="notifyDetails">{{ text.empty }}</span></span></div>
      <div v-for="reminder in notificationPreview.slice(0,24)" :key="reminder.id" class="notifyItem"><span class="notifyWhen">{{ reminderWhen(reminder.displayAt || reminder.remindAt) }}</span><span class="notifyType">{{ reminder.type }}</span><span class="notifyTitle">{{ reminder.title }}<span class="notifyDetails">{{ reminder.details || '' }} {{ reminder.displayAt || reminder.remindAt }} {{ reminder.displayTimezone || reminder.workTimezone || '' }}</span></span></div>
    </div>
    <div class="profileMsg" :class="{ ok: settings.notificationMessageOk }">{{ settings.notificationMessage }}</div>
  </SettingsCard>
</template>
