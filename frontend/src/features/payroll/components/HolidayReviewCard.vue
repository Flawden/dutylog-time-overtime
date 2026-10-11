<script setup lang="ts">
import { computed, ref, watch } from "vue";
import type { DutyLogApiSchemas as S } from "@/generated/dutylog-api";
import { createPayrollApi } from "../api/payrollApi";
import { reviewHundredths } from "../validation/holidayReviewInput";
import HolidaySourceFields, { type SourceDraft } from "./HolidaySourceFields.vue";
const props = defineProps<{ month: string; language: string }>();
const emit = defineEmits<{ saved: [] }>();
const api = createPayrollApi();
const english = computed(() => props.language === "en");
const open = ref(false), busy = ref(false), message = ref("");
const state = ref<S.HolidayReviewWorkspace | null>(null);
const freshSource = (): SourceDraft => ({ kind: "", reference: "", revision: "", basis: "" });
const system = ref(freshSource()), rest = ref(freshSource()), local = ref(freshSource());
const restRule = ref<S.HolidayReview["localRestDayRule"] | "">("");
type Draft = { component: S.HolidayReviewComponent; decision: "INCLUDE" | "EXCLUDE" | ""; source: SourceDraft; ruleSource: SourceDraft; period: "HOUR" | "PAYROLL_MONTH" | ""; amount: string; minutes: string; enhanced: string; rest: string };
const rows = ref<Draft[]>([]);
let generation = 0;
function money(minor: number): string { return (minor / 100).toFixed(2); }
async function load() {
  const ticket = ++generation;
  busy.value = true; message.value = ""; state.value = null;
  try {
    const data = await api.holidayReview(props.month);
    if (ticket !== generation || !data) return;
    state.value = data;
    system.value = data.savedReview ? { ...data.savedReview.completeSystemSource } : freshSource();
    rest.value = data.savedReview ? { ...data.savedReview.localRestDaySource } : freshSource();
    local.value = data.localSource ? { ...data.localSource, basis: "" } : freshSource();
    restRule.value = data.savedReview?.localRestDayRule ?? "";
    rows.value = data.components.map(component => {
      const rule = data.savedReview?.rules.find(r => r.versionId === component.versionId && r.componentFingerprint === component.fingerprint);
      return { component, decision: component.decision === 'UNCLASSIFIED' ? "" : component.decision ?? "", source: component.source ? { ...component.source } : freshSource(), ruleSource: rule ? { ...rule.source } : freshSource(), period: rule?.period ?? "", amount: rule ? money(rule.periodAmountMinor) : "", minutes: rule ? String(rule.periodMinutes) : "", enhanced: rule ? money(rule.enhancedAdditionalBps) : "", rest: rule ? money(rule.restDayAdditionalBps) : "" };
    });
  } catch (e) { if (ticket === generation) message.value = e instanceof Error ? e.message : String(e); }
  finally { if (ticket === generation) busy.value = false; }
}
watch(() => props.month, () => { if (open.value) void load(); });
function source(s: SourceDraft): S.HolidayReviewSource {
  if (!s.kind || !s.reference.trim() || !s.revision.trim() || !s.basis.trim()) throw new Error(english.value ? 'Complete every source field' : 'Заполни все поля источника');
  return { ...s, kind: s.kind };
}
function hundredths(value: string, max: number, positive = false): number {
  try { return reviewHundredths(value, max, positive); }
  catch { throw new Error(english.value ? 'Enter a valid amount with up to two decimals within the allowed range' : 'Введи допустимую сумму с точностью до двух знаков'); }
}
function localSource(): S.HolidayReviewLocalSource {
  const s = local.value;
  if (!s.kind || !s.reference.trim() || !s.revision.trim()) throw new Error(english.value ? 'Complete the local tariff source' : 'Заполни источник местной ставки');
  return { kind: s.kind, reference: s.reference, revision: s.revision };
}
async function save() {
  if (!state.value || busy.value) return;
  const data = state.value, month = props.month, ticket = generation;
  try {
    const components: S.HolidayReviewComponentInput[] = rows.value.map(r => {
      if (!r.decision) throw new Error(english.value ? 'Classify every component' : 'Выбери решение для каждой доплаты');
      return { versionId: r.component.versionId, fingerprint: r.component.fingerprint, decision: r.decision, source: source(r.source) };
    });
    const rules: S.HolidayReviewRule[] = rows.value.filter(r => r.decision === 'INCLUDE').map(r => {
      if (!r.period || !/^\d+$/.test(r.minutes) || Number(r.minutes) < 1 || Number(r.minutes) > 46080) throw new Error(english.value ? 'Specify the reviewed period and minutes' : 'Укажи проверенный период и число минут');
      return { versionId: r.component.versionId, componentFingerprint: r.component.fingerprint, currencyCode: data.currencyCode!, period: r.period, periodAmountMinor: hundredths(r.amount, 1e12, true), periodMinutes: r.period === 'HOUR' ? 60 : Number(r.minutes), enhancedAdditionalBps: hundredths(r.enhanced, 1e7), restDayAdditionalBps: hundredths(r.rest, 1e7), source: source(r.ruleSource) };
    });
    if (!restRule.value || !data.currencyCode) throw new Error(english.value ? 'Complete pay terms and rest-day rule' : 'Заполни условия оплаты и правило для другого дня отдыха');
    const body: S.HolidayReviewSubmission = { configurationToken: data.configurationToken, expectedRevision: data.revision, localSource: data.localSource ?? (data.localSourceRequired ? localSource() : null), components, review: { currencyCode: data.currencyCode, completeSystemSource: source(system.value), localRestDaySource: source(rest.value), localRestDayRule: restRule.value, rules } };
    busy.value = true;
    await api.saveHolidayReview(month, body);
    if (ticket !== generation) return;
    const reloadTicket = generation + 1;
    await load();
    if (month !== props.month || generation !== reloadTicket) return;
    message.value = english.value ? 'Sources saved. Recalculate the month to create a new payroll revision.' : 'Основания сохранены. Пересчитай месяц, чтобы создать новую расчётную ревизию.';
    emit('saved');
  } catch (e) { if (ticket === generation) message.value = e instanceof Error ? e.message : String(e); }
  finally { if (ticket === generation) busy.value = false; }
}
function expand() { open.value = !open.value; if (open.value) void load(); }
</script>
<template>
  <section class="holiday-review-card" data-testid="holiday-review">
    <h3>{{ english ? 'Holiday pay sources' : 'Основания праздничной оплаты' }}</h3>
    <p>{{ english ? 'Record sources you have checked for this month. This does not select a rest day for a work event.' : 'Укажи проверенные по документам основания для этого месяца. Выбор другого дня отдыха для конкретной работы оформляется отдельно.' }}</p>
    <button type="button" :aria-expanded="open" :disabled="busy" @click="expand">{{ open ? (english ? 'Collapse' : 'Свернуть') : (english ? 'Review sources' : 'Проверить основания') }}</button>
    <template v-if="open">
      <p v-if="busy" role="status">{{ english ? 'Loading / saving…' : 'Загрузка / сохранение…' }}</p>
      <p v-if="message" role="status">{{ message }}</p>
      <form v-if="state" @submit.prevent="save">
        <p>{{ month }} · {{ state.currencyCode }} · {{ english ? 'Review revision' : 'Ревизия оснований' }} {{ state.revision }}</p>
        <p>{{ state.ready ? (english ? 'Sources match the configuration at the start of the month.' : 'Основания соответствуют настройкам на начало месяца.') : (english ? 'Source review is incomplete or settings changed.' : 'Проверка оснований не завершена или настройки изменились.') }}</p>
        <button type="button" :disabled="busy" @click="load">{{ english ? 'Reload settings' : 'Обновить настройки' }}</button>
        <fieldset :disabled="busy">
          <legend>{{ english ? 'Complete pay system' : 'Проверка всей системы оплаты' }}</legend>
          <p>{{ english ? 'Confirm the tariff and all supplements against a document, including when the component list is empty.' : 'Подтверди по документу тариф и все дополнения, в том числе при пустом списке доплат.' }}</p>
          <HolidaySourceFields :source="system" :english="english" data-testid="holiday-system-source" />
          <template v-if="state.localSourceRequired">
            <h4>{{ english ? 'Local holiday tariff source' : 'Источник местной праздничной ставки' }}</h4>
            <HolidaySourceFields :source="local" :english="english" :include-basis="false" :disabled="!!state.localSource" />
          </template>
          <h4>{{ english ? 'Local tariff when another rest day is chosen' : 'Местная ставка при выборе другого дня отдыха' }}</h4>
          <label>{{ english ? 'Documented rule' : 'Правило по документу' }}
            <select v-model="restRule" required><option value="">{{ english ? 'Choose' : 'Выбери' }}</option><option value="NOT_APPLICABLE">{{ english ? 'Does not apply' : 'Не применяется' }}</option><option value="APPLIES">{{ english ? 'Applies' : 'Применяется' }}</option></select>
          </label>
          <HolidaySourceFields :source="rest" :english="english" data-testid="holiday-rest-source" />
          <article v-for="r in rows" :key="r.component.versionId" class="component-review">
            <h4>{{ r.component.name }} · {{ r.component.currencyCode }}</h4>
            <label>{{ english ? 'Include in holiday remuneration' : 'Учитывать в праздничной оплате' }}
              <select v-model="r.decision" :disabled="r.component.locked" required><option value="">{{ english ? 'Choose' : 'Выбери' }}</option><option value="INCLUDE">{{ english ? 'Include' : 'Учитывать' }}</option><option value="EXCLUDE">{{ english ? 'Exclude' : 'Исключить' }}</option></select>
            </label>
            <HolidaySourceFields :source="r.source" :english="english" data-testid="holiday-component-source" :disabled="r.component.locked" />
            <p v-if="r.component.locked">{{ english ? 'To change a saved classification, create a new component version.' : 'Для изменения сохранённой классификации создай новую версию доплаты.' }}</p>
            <template v-if="r.decision === 'INCLUDE'">
              <label>{{ english ? 'Amount for the period, in currency units' : 'Сумма за период в единицах валюты' }}<input v-model="r.amount" inputmode="decimal" required /></label>
              <label>{{ english ? 'Period' : 'Период' }}<select v-model="r.period" required @change="r.period === 'HOUR' && (r.minutes = '60')"><option value="">{{ english ? 'Choose' : 'Выбери' }}</option><option value="HOUR">{{ english ? 'Hour' : 'Час' }}</option><option value="PAYROLL_MONTH">{{ english ? 'Payroll month' : 'Расчётный месяц' }}</option></select></label>
              <label>{{ english ? 'Minutes in the period, from the source' : 'Минут в периоде по источнику' }}<input v-model="r.minutes" :disabled="r.period === 'HOUR'" inputmode="numeric" required /></label>
              <p>{{ english ? 'Additional percentages: 100 means +100%, not the total multiplier.' : 'Дополнительные проценты: 100 означает +100%, а не полную кратность оплаты.' }}</p>
              <label>{{ english ? 'Enhanced pay addition, %' : 'Дополнительно при усиленной оплате, %' }}<input v-model="r.enhanced" inputmode="decimal" required /></label>
              <label>{{ english ? 'Addition when another rest day is chosen, %' : 'Дополнительно при выборе другого дня отдыха, %' }}<input v-model="r.rest" inputmode="decimal" required /></label>
              <HolidaySourceFields :source="r.ruleSource" :english="english" data-testid="holiday-rule-source" />
            </template>
          </article>
          <button type="submit" :disabled="!state.currencyCode || !state.pricingEffectiveFrom">{{ english ? 'Save reviewed sources' : 'Сохранить проверенные основания' }}</button>
        </fieldset>
      </form>
    </template>
  </section>
</template>
<style scoped>
.holiday-review-card { padding: 1rem; border: 1px solid var(--border-color, #888); border-radius: .75rem; margin-block: 1rem; }
fieldset { border: 0; padding: .5rem 0; display: grid; gap: .75rem; }
.component-review { border-top: 1px solid var(--border-color, #888); padding-top: .75rem; display: grid; gap: .75rem; }
label { display: grid; gap: .25rem; }
input, select { width: 100%; box-sizing: border-box; }
</style>
