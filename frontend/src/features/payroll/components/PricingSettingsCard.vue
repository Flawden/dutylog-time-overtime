<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { usePayrollStore } from "../stores/payrollStore";
import type { PayPricingRule, PayPricingTerm, PayPricingTermInput } from "../types/domain";

defineProps<{ language: string }>();

const store = usePayrollStore();
const { pricingTerms, loading } = storeToRefs(store);

const MANAGED_CODES = new Set([
  "NIGHT",
  "HOLIDAY",
  "OT_TIER_1",
  "OT_TIER_2",
]);

const effectiveFrom = ref(todayKey());

const nightEnabled = ref(false);
const nightPercent = ref("20");

const holidayEnabled = ref(false);
const holidayPercent = ref("100");

const overtimeEnabled = ref(false);
const overtimeTierHours = ref("2");
const overtimeTier1Percent = ref("50");
const overtimeTier2Percent = ref("100");

const message = ref("");
const messageOk = ref(false);

const lang = computed(() =>
  document.documentElement.lang === "en" ? "en" : "ru"
);

const text = computed(() => lang.value === "en" ? {
  eyebrow: "Pricing",
  title: "Pay rules",
  hint: "Choose which classified work receives an additive premium. Nothing is enabled until you save an explicit rule version.",
  effective: "Effective from",
  night: "Night work",
  holiday: "Holiday work",
  overtime: "Overtime cash settlement",
  enabled: "Enable",
  premium: "Premium, %",
  firstHours: "First tier, hours",
  then: "Then, %",
  save: "Save pricing rules",
  remove: "Delete version",
  history: "Pricing history",
  baseOnly: "base only",
  noHistory: "No pricing rules have been configured yet.",
  advanced: "Additional advanced rules are preserved unchanged.",
  settlementHint: "Overtime pricing applies only when banked overtime is explicitly settled for cash. Banked overtime itself does not increase Payroll.",
  saved: "Pricing rules saved",
  deleted: "Pricing version deleted",
  invalidDate: "Choose an effective date",
  invalidPercent: "Premium must be between 0 and 100000%",
  invalidTier: "First overtime tier must be greater than 0 hours",
  deleteConfirm: "Delete this pricing version? An earlier version may become effective again.",
} : {
  eyebrow: "Pricing",
  title: "Правила оплаты",
  hint: "Укажи, за какую классифицированную работу начисляется дополнительная оплата. Пока версия явно не сохранена, DutyLog ничего не включает сам.",
  effective: "Действует с",
  night: "Ночная работа",
  holiday: "Работа в праздник",
  overtime: "Денежная выплата переработки",
  enabled: "Включить",
  premium: "Доплата, %",
  firstHours: "Первая ступень, ч",
  then: "Далее, %",
  save: "Сохранить правила",
  remove: "Удалить версию",
  history: "История правил",
  baseOnly: "только базовая оплата",
  noHistory: "Правила оплаты ещё не настроены.",
  advanced: "Дополнительные расширенные правила будут сохранены без изменений.",
  settlementHint: "Коэффициенты переработки применяются только при явной денежной выплате часов из банка. Сама переработка в банке Payroll не увеличивает.",
  saved: "Правила оплаты сохранены",
  deleted: "Версия правил удалена",
  invalidDate: "Укажи дату начала действия",
  invalidPercent: "Доплата должна быть от 0 до 100000%",
  invalidTier: "Первая ступень переработки должна быть больше 0 часов",
  deleteConfirm: "Удалить эту версию правил? Тогда снова может начать действовать предыдущая версия.",
});

const sortedTerms = computed(() =>
  [...pricingTerms.value].sort((a, b) =>
    b.effectiveFrom.localeCompare(a.effectiveFrom)
  )
);

const exactTerm = computed(() =>
  sortedTerms.value.find(item => item.effectiveFrom === effectiveFrom.value) ?? null
);

const sourceTerm = computed(() => termAt(effectiveFrom.value));

const advancedRuleCount = computed(() =>
  (sourceTerm.value?.rules ?? [])
    .filter(rule => !MANAGED_CODES.has(rule.code))
    .length
);

function todayKey(): string {
  const now = new Date();
  return [
    now.getFullYear(),
    String(now.getMonth() + 1).padStart(2, "0"),
    String(now.getDate()).padStart(2, "0"),
  ].join("-");
}

function termAt(date: string): PayPricingTerm | null {
  if (!date) return null;
  return sortedTerms.value.find(item => item.effectiveFrom <= date) ?? null;
}

function managedRule(term: PayPricingTerm | null, code: string): PayPricingRule | null {
  return term?.rules.find(rule => rule.code === code) ?? null;
}

function percent(rule: PayPricingRule | null, fallback: number): string {
  return rule ? String(rule.premiumBps / 100) : String(fallback);
}

function synchronizeDraft(): void {
  const term = sourceTerm.value;

  const night = managedRule(term, "NIGHT");
  const holiday = managedRule(term, "HOLIDAY");
  const tier1 = managedRule(term, "OT_TIER_1");
  const tier2 = managedRule(term, "OT_TIER_2");

  nightEnabled.value = night != null;
  nightPercent.value = percent(night, 20);

  holidayEnabled.value = holiday != null;
  holidayPercent.value = percent(holiday, 100);

  overtimeEnabled.value = tier1 != null || tier2 != null;

  const boundary =
    tier1?.toMinuteExclusive
    ?? tier2?.fromMinute
    ?? 120;

  overtimeTierHours.value =
    String(Math.round((boundary / 60) * 100) / 100);

  overtimeTier1Percent.value = percent(tier1, 50);
  overtimeTier2Percent.value = percent(tier2, 100);
}

watch(
  [pricingTerms, effectiveFrom],
  synchronizeDraft,
  { immediate: true, deep: true },
);

function parsePercent(raw: string): number {
  const value = Number(String(raw).replace(",", "."));
  if (!Number.isFinite(value) || value < 0 || value > 100000) {
    throw new Error(text.value.invalidPercent);
  }
  return Math.round(value * 100);
}

function parseTierMinutes(): number {
  const hours = Number(String(overtimeTierHours.value).replace(",", "."));
  if (!Number.isFinite(hours) || hours <= 0 || hours > 168) {
    throw new Error(text.value.invalidTier);
  }
  return Math.max(1, Math.round(hours * 60));
}

function rule(
  code: string,
  dimension: "NIGHT" | "HOLIDAY" | "OVERTIME",
  premiumBps: number,
  fromMinute = 0,
  toMinuteExclusive: number | null = null,
): PayPricingRule {
  return {
    code,
    dimension,
    premiumBps,
    fromMinute,
    toMinuteExclusive,
    exclusiveGroup: null,
  };
}

function payload(): PayPricingTermInput {
  if (!effectiveFrom.value) {
    throw new Error(text.value.invalidDate);
  }

  /*
   * A term is a complete effective-dated policy snapshot.
   * When a new date is created, inherit the policy active on that date.
   * Rules outside this simple UI remain byte-for-byte semantically intact.
   */
  const preservedRules =
    (sourceTerm.value?.rules ?? [])
      .filter(rule => !MANAGED_CODES.has(rule.code))
      .map(rule => ({ ...rule }));

  const rules: PayPricingRule[] = [...preservedRules];

  if (nightEnabled.value) {
    rules.push(
      rule(
        "NIGHT",
        "NIGHT",
        parsePercent(nightPercent.value),
      ),
    );
  }

  if (holidayEnabled.value) {
    rules.push(
      rule(
        "HOLIDAY",
        "HOLIDAY",
        parsePercent(holidayPercent.value),
      ),
    );
  }

  if (overtimeEnabled.value) {
    const boundary = parseTierMinutes();

    rules.push(
      rule(
        "OT_TIER_1",
        "OVERTIME",
        parsePercent(overtimeTier1Percent.value),
        0,
        boundary,
      ),
    );

    rules.push(
      rule(
        "OT_TIER_2",
        "OVERTIME",
        parsePercent(overtimeTier2Percent.value),
        boundary,
        null,
      ),
    );
  }

  return { rules };
}

async function save(): Promise<void> {
  try {
    await store.savePricingTerm(
      effectiveFrom.value,
      payload(),
    );

    message.value = text.value.saved;
    messageOk.value = true;
    synchronizeDraft();
  } catch (error) {
    message.value =
      error instanceof Error
        ? error.message
        : String(error);
    messageOk.value = false;
  }
}

async function remove(): Promise<void> {
  if (!exactTerm.value) return;

  if (!window.confirm(text.value.deleteConfirm)) {
    return;
  }

  try {
    await store.deletePricingTerm(
      exactTerm.value.effectiveFrom,
    );

    message.value = text.value.deleted;
    messageOk.value = true;
    synchronizeDraft();
  } catch (error) {
    message.value =
      error instanceof Error
        ? error.message
        : String(error);
    messageOk.value = false;
  }
}

function edit(term: PayPricingTerm): void {
  effectiveFrom.value = term.effectiveFrom;
  synchronizeDraft();
}

function formatPercent(bps: number): string {
  const value = bps / 100;
  return `${Number.isInteger(value) ? value : value.toFixed(2)}%`;
}

function summary(term: PayPricingTerm): string {
  if (!term.rules.length) {
    return text.value.baseOnly;
  }

  return term.rules
    .map(rule => {
      const premium = `+${formatPercent(rule.premiumBps)}`;

      if (rule.dimension !== "OVERTIME") {
        return `${rule.dimension} ${premium}`;
      }

      if (rule.toMinuteExclusive != null) {
        return `OT 0–${rule.toMinuteExclusive / 60}h ${premium}`;
      }

      return `OT ${rule.fromMinute / 60}h+ ${premium}`;
    })
    .join(" · ");
}
</script>

<template>
  <section
    id="payrollPricingCard"
    class="card payrollPricingCard"
    data-native-pay-pricing
  >
    <div class="pricingHead">
      <div>
        <div class="eyebrow">{{ text.eyebrow }}</div>
        <h3>{{ text.title }}</h3>
        <p>{{ text.hint }}</p>
      </div>

      <label class="pricingEffective">
        {{ text.effective }}
        <input
          id="pricingEffectiveFrom"
          v-model="effectiveFrom"
          type="date"
          required
        />
      </label>
    </div>

    <div class="pricingRulesGrid">
      <section class="pricingRuleCard">
        <label class="pricingToggle">
          <input
            id="pricingNightEnabled"
            v-model="nightEnabled"
            type="checkbox"
          />
          <span>
            <b>{{ text.night }}</b>
            <small>{{ text.enabled }}</small>
          </span>
        </label>

        <label>
          {{ text.premium }}
          <input
            id="pricingNightPercent"
            v-model="nightPercent"
            type="number"
            min="0"
            max="100000"
            step="0.01"
            :disabled="!nightEnabled"
          />
        </label>
      </section>

      <section class="pricingRuleCard">
        <label class="pricingToggle">
          <input
            id="pricingHolidayEnabled"
            v-model="holidayEnabled"
            type="checkbox"
          />
          <span>
            <b>{{ text.holiday }}</b>
            <small>{{ text.enabled }}</small>
          </span>
        </label>

        <label>
          {{ text.premium }}
          <input
            id="pricingHolidayPercent"
            v-model="holidayPercent"
            type="number"
            min="0"
            max="100000"
            step="0.01"
            :disabled="!holidayEnabled"
          />
        </label>
      </section>

      <section class="pricingRuleCard pricingRuleCardWide">
        <label class="pricingToggle">
          <input
            id="pricingOvertimeEnabled"
            v-model="overtimeEnabled"
            type="checkbox"
          />
          <span>
            <b>{{ text.overtime }}</b>
            <small>{{ text.enabled }}</small>
          </span>
        </label>

        <div class="pricingOvertimeGrid">
          <label>
            {{ text.firstHours }}
            <input
              id="pricingOvertimeTierHours"
              v-model="overtimeTierHours"
              type="number"
              min="0.01"
              max="168"
              step="0.25"
              :disabled="!overtimeEnabled"
            />
          </label>

          <label>
            {{ text.premium }}
            <input
              id="pricingOvertimeTier1Percent"
              v-model="overtimeTier1Percent"
              type="number"
              min="0"
              max="100000"
              step="0.01"
              :disabled="!overtimeEnabled"
            />
          </label>

          <label>
            {{ text.then }}
            <input
              id="pricingOvertimeTier2Percent"
              v-model="overtimeTier2Percent"
              type="number"
              min="0"
              max="100000"
              step="0.01"
              :disabled="!overtimeEnabled"
            />
          </label>
        </div>

        <p class="pricingRuleHint">
          {{ text.settlementHint }}
        </p>
      </section>
    </div>

    <p
      v-if="advancedRuleCount"
      id="pricingAdvancedRuleNotice"
      class="pricingAdvancedNotice"
    >
      {{ text.advanced }}
      ({{ advancedRuleCount }})
    </p>

    <div class="pricingActions">
      <button
        id="pricingSave"
        class="primary"
        type="button"
        :disabled="loading || !effectiveFrom"
        @click="save"
      >
        {{ text.save }}
      </button>

      <button
        v-if="exactTerm"
        id="pricingDelete"
        class="dangerGhost"
        type="button"
        :disabled="loading"
        @click="remove"
      >
        {{ text.remove }}
      </button>
    </div>

    <div
      id="pricingMessage"
      class="profileMsg"
      :class="{ ok: messageOk }"
      role="status"
    >
      {{ message }}
    </div>

    <div class="pricingHistory">
      <div class="eyebrow">{{ text.history }}</div>

      <button
        v-for="term in sortedTerms"
        :key="term.id"
        class="pricingHistoryRow"
        :class="{ active: term.effectiveFrom === effectiveFrom }"
        type="button"
        @click="edit(term)"
      >
        <span>
          <b>{{ term.effectiveFrom }}</b>
          <small>{{ summary(term) }}</small>
        </span>
        <span>›</span>
      </button>

      <div
        v-if="!sortedTerms.length"
        id="pricingHistoryEmpty"
        class="payrollEmpty"
      >
        {{ text.noHistory }}
      </div>
    </div>
  </section>
</template>

<style scoped>
.payrollPricingCard {
  padding: 20px;
  margin-bottom: 16px;
}

.pricingHead {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 16px;
}

.pricingHead h3 {
  margin: 2px 0 6px;
}

.pricingHead p,
.pricingRuleHint,
.pricingAdvancedNotice {
  margin: 0;
  color: var(--dim);
  font-size: 12px;
  line-height: 1.45;
}

.pricingEffective,
.pricingRuleCard label {
  display: grid;
  gap: 5px;
  color: var(--dim);
  font-size: 12px;
  font-weight: 800;
}

.pricingEffective {
  min-width: 175px;
}

.pricingRulesGrid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.pricingRuleCard {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 14px;
}

.pricingRuleCardWide {
  grid-column: 1 / -1;
}

.pricingToggle {
  display: flex !important;
  align-items: center;
  gap: 10px !important;
  color: var(--text) !important;
}

.pricingToggle span {
  display: grid;
  gap: 2px;
}

.pricingToggle small {
  color: var(--dim);
  font-weight: 600;
}

.pricingOvertimeGrid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.pricingActions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.pricingAdvancedNotice {
  margin-top: 12px;
}

.pricingHistory {
  display: grid;
  gap: 8px;
  margin-top: 18px;
}

.pricingHistoryRow {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  text-align: left;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: transparent;
}

.pricingHistoryRow.active {
  border-color: var(--accent);
  box-shadow: inset 0 0 0 1px var(--accent);
}

.pricingHistoryRow span:first-child {
  display: grid;
  gap: 3px;
}

.pricingHistoryRow small {
  color: var(--dim);
}

@media (max-width: 720px) {
  .pricingHead {
    flex-direction: column;
  }

  .pricingEffective {
    width: 100%;
  }

  .pricingRulesGrid,
  .pricingOvertimeGrid {
    grid-template-columns: 1fr;
  }

  .pricingRuleCardWide {
    grid-column: auto;
  }
}
</style>
