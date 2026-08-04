<script setup lang="ts">
import { onErrorCaptured } from "vue";
import {
  captureFrontendFailure,
  clearFrontendFailure,
  frontendDiagnostics,
} from "@/platform/diagnostics/frontendDiagnostics";

onErrorCaptured(error => {
  captureFrontendFailure(error, "vue");
  return false;
});

function reload(): void {
  globalThis.location.reload();
}

function returnToday(): void {
  clearFrontendFailure();
  globalThis.location.hash = "#today";
  globalThis.location.reload();
}
</script>

<template>
  <section
    v-if="frontendDiagnostics.fatal"
    class="vue-recovery"
    role="alert"
    aria-live="assertive"
    data-vue-recovery-ui
  >
    <div class="vue-recovery__card">
      <p class="vue-recovery__eyebrow">DutyLog recovery</p>
      <h1>Интерфейс не смог продолжить работу</h1>
      <p>Данные не удалены. Перезагрузите приложение или вернитесь на экран «Сегодня».</p>
      <dl>
        <div><dt>Версия</dt><dd>{{ frontendDiagnostics.fatal.releaseVersion }}</dd></div>
        <div><dt>Маршрут</dt><dd>{{ frontendDiagnostics.fatal.route }}</dd></div>
        <div v-if="frontendDiagnostics.fatal.requestId">
          <dt>Request ID</dt><dd data-vue-recovery-request-id>{{ frontendDiagnostics.fatal.requestId }}</dd>
        </div>
      </dl>
      <div class="vue-recovery__actions">
        <button type="button" class="ui-button ui-button--primary" data-vue-recovery-reload @click="reload">Перезагрузить</button>
        <button type="button" class="ui-button ui-button--secondary" data-vue-recovery-today @click="returnToday">На «Сегодня»</button>
      </div>
    </div>
  </section>
  <slot v-else />
</template>
