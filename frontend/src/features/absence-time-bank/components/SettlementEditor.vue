<script setup lang="ts">
import { computed } from "vue";
import { storeToRefs } from "pinia";
import UiButton from "@/shared/ui/UiButton.vue";
import UiModal from "@/shared/overlays/UiModal.vue";
import { useAbsenceTimeBankStore } from "../stores/absenceTimeBankStore";
import {
  fifoForecastForSettlement,
  formatMinutes,
} from "../types/model";

const store = useAbsenceTimeBankStore();

const {
  settlementModalOpen,
  settlementDraft,
  account,
  mutationPending,
  error,
  conflict,
} = storeToRefs(store);

const editing =
  computed(
    () => Boolean(settlementDraft.value.id)
  );

const title =
  computed(
    () =>
      editing.value
        ? "Изменить «к оплате»"
        : "Отправить переработку к оплате"
  );

const safeAccount =
  computed(
    () =>
      account.value ?? {
        totalEarnedHours: 0,
        totalUsedHours: 0,
        balanceHours: 0,
        credits: [],
        usages: [],
      }
  );

const forecast =
  computed(() =>
    fifoForecastForSettlement(
      safeAccount.value,
      Math.max(
        0,
        Math.round(
          Number(
            settlementDraft.value.minutes ?? 0
          )
        )
      ),
      settlementDraft.value.id
    )
  );

const settlementHours =
  computed({
    get: () =>
      Math.round(
        (
          Number(
            settlementDraft.value.minutes ?? 0
          ) / 60
        ) * 100
      ) / 100,

    set: (value: number) => {
      settlementDraft.value.minutes =
        Math.max(
          0,
          Math.round(
            Number(value || 0) * 60
          )
        );
    },
  });
</script>

<template>
  <UiModal
    :open="settlementModalOpen"
    :title="title"
    description="Явное решение списать минуты из единого банка для будущей денежной выплаты. Стоимость пока не рассчитывается."
    @close="store.closeSettlementEditor()"
  >
    <div
      id="overtimeSettlementModal"
      class="domain-modal-body"
      data-vue-settlement-editor
    >
      <h3 class="domain-visually-hidden">
        {{ title }}
      </h3>

      <div
        v-if="conflict"
        class="domain-alert domain-alert--warning"
        role="alert"
      >
        {{ conflict }}
      </div>

      <div
        v-if="error"
        class="domain-alert domain-alert--danger"
        role="alert"
      >
        {{ error }}
      </div>

      <form
        id="overtimeSettlementForm"
        class="domain-form"
        @submit.prevent="store.saveSettlement()"
      >
        <label>
          Дата
          <input
            id="settlementDate"
            v-model="settlementDraft.settlementDate"
            type="date"
            required
          />
        </label>

        <label>
          Часы к оплате
          <input
            id="settlementHours"
            v-model.number="settlementHours"
            type="number"
            min="0.01"
            max="100"
            step="0.01"
            required
          />
          <small>
            Точно:
            {{ formatMinutes(settlementDraft.minutes) }}
          </small>
        </label>

        <label class="domain-form__wide">
          Комментарий
          <textarea
            id="settlementReason"
            v-model="settlementDraft.reason"
            maxlength="1000"
            rows="3"
            placeholder="Например: выплатить с зарплатой"
          ></textarea>
        </label>
      </form>

      <section class="domain-context-card">
        <strong>
          Одна операция — один способ погашения.
        </strong>
        <br />
        <span class="domain-muted">
          Эти минуты сразу уйдут из свободного
          остатка банка по тому же FIFO, что и
          отгул. Денежная сумма появится позже
          в Pricing / Payroll.
        </span>
      </section>

      <section
        id="settlementFifoForecast"
        class="domain-fifo-forecast"
        aria-live="polite"
      >
        <h3>FIFO-прогноз</h3>

        <p v-if="!forecast.requestedMinutes">
          Укажите объём — DutyLog покажет
          источники списания.
        </p>

        <div
          v-for="item in forecast.allocations"
          :key="`${item.creditId}-${item.minutes}`"
          class="fifo-forecast-row"
        >
          <span>
            {{ item.workedDate }} · {{ item.reason }}
          </span>
          <b>
            −{{ formatMinutes(item.minutes) }}
          </b>
        </div>

        <p
          v-if="forecast.shortageMinutes"
          class="domain-preview-card__warning"
        >
          Недостаточно свободного времени:
          {{ formatMinutes(forecast.shortageMinutes) }}
        </p>

        <p v-else-if="forecast.requestedMinutes">
          После этого останется
          {{ formatMinutes(forecast.freeAfterMinutes) }}
        </p>
      </section>
    </div>

    <template #footer>
      <UiButton
        variant="ghost"
        :disabled="mutationPending"
        @click="store.closeSettlementEditor()"
      >
        Отмена
      </UiButton>

      <UiButton
        v-if="editing && settlementDraft.id"
        variant="danger"
        :disabled="mutationPending"
        :data-delete-settlement="settlementDraft.id"
        @click="
          store.deleteSettlement(
            Number(settlementDraft.id)
          )
        "
      >
        Удалить и вернуть в банк
      </UiButton>

      <UiButton
        id="settlementSave"
        variant="primary"
        :disabled="mutationPending"
        @click="store.saveSettlement()"
      >
        {{
          mutationPending
            ? "Сохраняем…"
            : editing
              ? "Сохранить"
              : "Отправить к оплате"
        }}
      </UiButton>
    </template>
  </UiModal>
</template>
