<script setup lang="ts">
import { computed, ref, watch } from "vue";
import type { LegacyBridge } from "@/platform/bridge/legacyBridge";
import UiButton from "@/shared/ui/UiButton.vue";
import UiModal from "@/shared/overlays/UiModal.vue";

const props = defineProps<{
  bridge: LegacyBridge;
  open: boolean;
  language: "ru" | "en";
  status: DutyLogOfflineSyncStatusSnapshot;
}>();
const emit = defineEmits<{ close: [] }>();

const details = ref<DutyLogOfflineSyncDetailsSnapshot | null>(null);
const loading = ref(false);
const syncing = ref(false);
const feedback = ref<{ message: string; tone: "ok" | "warn" | "err" | "busy" } | null>(null);
let refreshRevision = 0;

const text = computed(() => props.language === "en" ? {
  title: "Data sync",
  description: "Local queue, failed operations and offline diagnostics. DutyLog keeps one sync executor in the existing data layer.",
  close: "Close",
  online: "Online",
  offline: "Offline",
  lastSync: "Last sync",
  neverSynced: "No local copy yet",
  stale: "Local data is older than one day. Check it after reconnecting to the server.",
  pending: "Pending upload",
  noPending: "No pending changes.",
  failed: "Failed operations",
  noFailed: "No failed sync operations.",
  diagnostics: "Offline diagnostics",
  connection: "Connection",
  indexedDb: "IndexedDB",
  available: "available",
  unavailable: "unavailable",
  snapshotAge: "Snapshot age",
  queue: "Queue",
  syncLock: "Sync lock",
  none: "none",
  expired: "expired",
  thisTab: "active in this tab",
  otherTab: "active in another tab",
  started: "Lock started",
  expires: "Lock expires",
  sync: "Sync",
  syncing: "Syncing…",
  retryAll: "Retry failed operations",
  export: "Download local data",
  clearFailed: "Clear failed operations",
  copyDiagnostics: "Copy diagnostics",
  retry: "Retry operation",
  remove: "Remove from list",
  noConnection: "No network connection",
  alreadySyncing: "Sync is already running",
  lockedOtherTab: "Sync is running in another tab",
  notAllSent: "Not all changes were sent",
  syncComplete: "Sync completed",
  noChanges: "No changes",
  copied: "Offline diagnostics copied",
  copyFailed: "Failed to copy diagnostics",
  exportFailed: "Failed to export local data",
  operationFailed: "Operation failed",
  attempts: "attempts",
  task: "Task",
  inbox: "Inbox",
  note: "Note",
  shift: "shift",
  day: "day",
  date: "date",
  done: "done",
  open: "open",
  justNow: "just now",
  minuteAgo: "min ago",
  hourAgo: "h ago",
  dayAgo: "d ago",
} : {
  title: "Синхронизация данных",
  description: "Локальная очередь, неудачные операции и диагностика оффлайна. Исполнитель синхронизации остаётся единственным — существующий dataLayer.",
  close: "Закрыть",
  online: "Онлайн",
  offline: "Оффлайн",
  lastSync: "Последняя синхронизация",
  neverSynced: "Локальной копии пока нет",
  stale: "Локальные данные старше суток. Проверьте их после подключения к серверу.",
  pending: "Ожидают отправки",
  noPending: "Нет изменений, ожидающих отправки.",
  failed: "Неудачные операции",
  noFailed: "Неудачных операций синхронизации нет.",
  diagnostics: "Диагностика оффлайна",
  connection: "Подключение",
  indexedDb: "IndexedDB",
  available: "доступна",
  unavailable: "недоступна",
  snapshotAge: "Возраст snapshot",
  queue: "Очередь",
  syncLock: "Sync lock",
  none: "нет",
  expired: "протух",
  thisTab: "активен в этой вкладке",
  otherTab: "активен в другой вкладке",
  started: "Lock запущен",
  expires: "Lock истекает",
  sync: "Синхронизировать",
  syncing: "Синхронизация…",
  retryAll: "Повторить неудачные операции",
  export: "Скачать локальные данные",
  clearFailed: "Очистить неудачные операции",
  copyDiagnostics: "Скопировать диагностику",
  retry: "Повторить операцию",
  remove: "Убрать из списка",
  noConnection: "Нет подключения к сети",
  alreadySyncing: "Синхронизация уже выполняется",
  lockedOtherTab: "Синхронизация выполняется в другой вкладке",
  notAllSent: "Не все изменения отправлены",
  syncComplete: "Синхронизация завершена",
  noChanges: "Нет изменений",
  copied: "Диагностика оффлайна скопирована",
  copyFailed: "Не удалось скопировать диагностику",
  exportFailed: "Не удалось скачать локальные данные",
  operationFailed: "Операция не выполнена",
  attempts: "попыток",
  task: "Задача",
  inbox: "Входящие",
  note: "Заметка",
  shift: "смена",
  day: "день",
  date: "дата",
  done: "выполнена",
  open: "открыта",
  justNow: "только что",
  minuteAgo: "мин назад",
  hourAgo: "ч назад",
  dayAgo: "дн назад",
});

const queue = computed(() => details.value?.queue ?? []);
const failed = computed(() => details.value?.failed ?? []);

function asRecord(value: unknown): Record<string, unknown> {
  return value != null && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
}
function asText(value: unknown): string { return typeof value === "string" ? value : value == null ? "" : String(value); }
function asBoolean(value: unknown): boolean { return value === true; }

function formatInstant(iso: string | null): string {
  if (!iso) return "—";
  const instant = new Date(iso);
  if (!Number.isFinite(instant.getTime())) return iso;
  return new Intl.DateTimeFormat(props.language === "en" ? "en-GB" : "ru-RU", {
    day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit",
  }).format(instant);
}
function formatAge(iso: string | null): string {
  if (!iso) return "—";
  const instant = new Date(iso).getTime();
  if (!Number.isFinite(instant)) return "—";
  const age = Math.max(0, Date.now() - instant);
  const minutes = Math.floor(age / 60_000);
  if (minutes < 1) return text.value.justNow;
  if (minutes < 60) return `${minutes} ${text.value.minuteAgo}`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} ${text.value.hourAgo}`;
  return `${Math.floor(hours / 24)} ${text.value.dayAgo}`;
}

function describe(item: DutyLogOfflineQueueItemSnapshot): string {
  const payload = asRecord(item.payload);
  if (item.type === "putDay") {
    const day = asRecord(payload.day);
    const parts: string[] = [];
    if (day.shiftTypeId != null) parts.push(text.value.shift);
    if (asText(day.note).trim()) parts.push(text.value.note.toLocaleLowerCase());
    if (asText(day.dayEmoji).trim()) parts.push(`emoji ${asText(day.dayEmoji)}`);
    if (!parts.length) parts.push(text.value.day);
    return `${asText(payload.date) || text.value.date}: ${parts.join(" + ")}`;
  }
  if (item.type === "toggleTask") return `${text.value.task} #${asText(payload.taskId)}: ${asBoolean(payload.done) ? text.value.done : text.value.open}`;
  if (item.type === "captureInbox") return `${text.value.inbox}: ${asText(payload.text).slice(0, 80)}`;
  if (item.type === "updateNote") return `${text.value.note} #${asText(payload.noteId)}: ${asText(payload.date) || text.value.date}`;
  return item.type || "Operation";
}

function lockLabel(): string {
  const lock = details.value?.lock;
  if (!lock?.active) return lock?.expired ? text.value.expired : text.value.none;
  return lock.mine ? text.value.thisTab : text.value.otherTab;
}

const diagnosticsRows = computed(() => {
  const rows = [
    { label:text.value.connection, value:props.status.online ? text.value.online : text.value.offline, ok:props.status.online },
    { label:text.value.indexedDb, value:props.status.cacheReady ? text.value.available : text.value.unavailable, ok:props.status.cacheReady },
    { label:text.value.lastSync, value:props.status.lastSyncAt ? formatInstant(props.status.lastSyncAt) : text.value.neverSynced, ok:props.status.lastSyncAt != null },
    { label:text.value.snapshotAge, value:props.status.lastSyncAt ? formatAge(props.status.lastSyncAt) : text.value.neverSynced, ok:props.status.lastSyncAt != null && !props.status.stale },
    { label:text.value.queue, value:String(props.status.pending), ok:props.status.pending === 0 },
    { label:text.value.failed, value:String(props.status.failed), ok:props.status.failed === 0 },
    { label:text.value.syncLock, value:lockLabel(), ok:!details.value?.lock.active || details.value?.lock.mine === true },
  ];
  if (details.value?.lock.startedAt) rows.push({ label:text.value.started, value:formatInstant(details.value.lock.startedAt), ok:!details.value.lock.expired });
  if (details.value?.lock.expiresAt) rows.push({ label:text.value.expires, value:formatInstant(details.value.lock.expiresAt), ok:!details.value.lock.expired });
  return rows;
});

function setFeedback(message = "", tone: "ok" | "warn" | "err" | "busy" = "ok"): void {
  feedback.value = message ? { message, tone } : null;
}

async function refresh(): Promise<void> {
  const revision = ++refreshRevision;
  loading.value = true;
  try {
    const next = await props.bridge.offlineSyncDetails();
    if (revision === refreshRevision) details.value = next;
  } catch {
    if (revision === refreshRevision) {
      details.value = null;
      setFeedback(text.value.operationFailed, "err");
    }
  } finally {
    if (revision === refreshRevision) loading.value = false;
  }
}

watch(() => props.open, open => {
  if (open) { setFeedback(); void refresh(); }
});
watch(() => [props.status.pending, props.status.failed, props.status.syncing, props.status.syncLockedByOther] as const, () => {
  if (props.open) void refresh();
});

async function synchronize(): Promise<void> {
  const beforePending = props.status.pending;
  const beforeFailed = props.status.failed;
  if (props.status.syncing || syncing.value) { setFeedback(text.value.alreadySyncing, "warn"); return; }
  if (!props.status.online) { setFeedback(text.value.noConnection, "err"); return; }
  syncing.value = true;
  setFeedback(text.value.syncing, "busy");
  try {
    await props.bridge.offlineSync();
    await refresh();
    if (props.status.syncLockedByOther) setFeedback(text.value.lockedOtherTab, "warn");
    else if (props.status.pending > 0 || props.status.failed > beforeFailed) setFeedback(text.value.notAllSent, "err");
    else if (beforePending > 0) setFeedback(text.value.syncComplete, "ok");
    else setFeedback(text.value.noChanges, "ok");
  } catch {
    setFeedback(text.value.operationFailed, "err");
  } finally {
    syncing.value = false;
  }
}

async function run(action: () => Promise<void>, failure = text.value.operationFailed): Promise<void> {
  try { await action(); await refresh(); }
  catch { setFeedback(failure, "err"); }
}
async function copyDiagnostics(): Promise<void> {
  try {
    if (!details.value) await refresh();
    await navigator.clipboard.writeText(details.value?.diagnosticsReport ?? "");
    setFeedback(text.value.copied, "ok");
  } catch { setFeedback(text.value.copyFailed, "err"); }
}
</script>

<template>
  <UiModal :open="open" :title="text.title" :description="text.description" :close-label="text.close" @close="emit('close')">
    <div id="offlineSyncDialog" class="vue-offline-sync" data-vue-offline-sync-dialog>
      <div id="offlineSyncMeta" class="vue-offline-sync__meta">
        <strong>{{ status.online ? text.online : text.offline }}</strong>
        <span>{{ text.lastSync }}: {{ status.lastSyncAt ? `${formatInstant(status.lastSyncAt)} · ${formatAge(status.lastSyncAt)}` : text.neverSynced }}</span>
        <p v-if="status.stale" class="vue-offline-sync__warning">{{ text.stale }}</p>
      </div>

      <section class="vue-offline-sync__section">
        <h3>{{ text.pending }}</h3>
        <div id="offlinePendingList" class="vue-offline-sync__list" :aria-busy="loading">
          <article v-for="item in queue" :key="item.id" class="vue-offline-sync__item">
            <div><strong>{{ describe(item) }}</strong><span>{{ formatInstant(item.createdAt) }}<template v-if="item.attempts"> · {{ text.attempts }}: {{ item.attempts }}</template></span></div>
            <small v-if="item.lastError">{{ item.lastError }}</small>
          </article>
          <span v-if="!queue.length" class="vue-offline-sync__empty">{{ text.noPending }}</span>
        </div>
      </section>

      <section class="vue-offline-sync__section">
        <h3>{{ text.failed }}</h3>
        <div id="offlineFailedList" class="vue-offline-sync__list" :aria-busy="loading">
          <article v-for="(item, index) in failed" :key="`${item.id}-${index}`" class="vue-offline-sync__item is-failed">
            <div><strong>{{ describe(item) }}</strong><span>{{ formatInstant(item.failedAt ?? item.createdAt) }}</span></div>
            <small>{{ item.lastError || text.operationFailed }}</small>
            <div class="vue-offline-sync__item-actions">
              <UiButton size="sm" :data-failed-retry="index" @click="run(() => bridge.offlineRetryFailed(index))">{{ text.retry }}</UiButton>
              <UiButton size="sm" variant="ghost" :data-failed-remove="index" @click="run(() => bridge.offlineRemoveFailed(index))">{{ text.remove }}</UiButton>
            </div>
          </article>
          <span v-if="!failed.length" class="vue-offline-sync__empty">{{ text.noFailed }}</span>
        </div>
      </section>

      <section class="vue-offline-sync__section">
        <h3>{{ text.diagnostics }}</h3>
        <div id="offlineDiagnosticsList" class="vue-offline-sync__diagnostics">
          <div v-for="row in diagnosticsRows" :key="row.label" :class="{ 'is-ok':row.ok, 'is-warn':!row.ok }">
            <span>{{ row.label }}</span><strong>{{ row.value }}</strong>
          </div>
        </div>
        <UiButton id="offlineDiagnosticsCopy" size="sm" variant="ghost" @click="copyDiagnostics">{{ text.copyDiagnostics }}</UiButton>
      </section>

      <div id="offlineSyncFeedback" class="vue-offline-sync__feedback" :class="feedback ? `is-${feedback.tone}` : ''" role="status" aria-live="polite">
        {{ feedback?.message ?? "" }}
      </div>
    </div>

    <template #footer>
      <UiButton id="offlineSyncNow" variant="primary" :disabled="syncing || status.syncing" :aria-busy="syncing || status.syncing" @click="synchronize">
        {{ syncing || status.syncing ? text.syncing : text.sync }}
      </UiButton>
      <UiButton id="offlineFailedRetryAll" :disabled="failed.length === 0" @click="run(() => bridge.offlineRetryAllFailed())">{{ text.retryAll }}</UiButton>
      <UiButton id="offlineExport" @click="run(() => bridge.offlineExport(), text.exportFailed)">{{ text.export }}</UiButton>
      <UiButton id="offlineFailedClear" variant="danger" :disabled="failed.length === 0" @click="run(() => bridge.offlineClearFailed())">{{ text.clearFailed }}</UiButton>
      <UiButton id="offlineSyncClose" variant="ghost" @click="emit('close')">{{ text.close }}</UiButton>
    </template>
  </UiModal>
</template>
