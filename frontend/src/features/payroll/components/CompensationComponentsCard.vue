<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { usePayrollStore } from "../stores/payrollStore";
import type {
  PayrollCompensationComponentVersion,
  PayrollCompensationComponentVersionInput,
} from "../types/domain";

type CalculationType =
  | "FIXED_AMOUNT"
  | "PERCENT_OF_BASE";

type CalculationBase =
  | "NOMINAL_SALARY"
  | "EARNED_BASE_PAY";

interface ComponentGroup {
  componentId: number;
  versions: PayrollCompensationComponentVersion[];
}

const props = defineProps<{
  language: string;
  month: string;
  currencyCode: string;
}>();

const store = usePayrollStore();

const {
  compensationComponentHistory,
  compensationComponentsLoading,
  compensationComponentsError,
  loading,
} = storeToRefs(store);

const MONTH_RE = /^\d{4}-(0[1-9]|1[0-2])$/;

const editingComponentId = ref<number | null>(null);

const effectiveMonth = ref(
  MONTH_RE.test(props.month)
    ? props.month
    : currentMonth(),
);

const displayName = ref("");
const calculationType = ref<CalculationType>("PERCENT_OF_BASE");
const calculationBase = ref<CalculationBase>("EARNED_BASE_PAY");
const percentValue = ref("10");
const fixedAmount = ref("");
const fixedCurrency = ref(
  normalizeCurrency(props.currencyCode),
);
const enabled = ref(true);

const message = ref("");
const messageOk = ref(false);

const lang = computed<"ru" | "en">(() =>
  props.language === "en"
    ? "en"
    : "ru"
);

const busy = computed(() =>
  compensationComponentsLoading.value
  || loading.value
);

const text = computed(() =>
  lang.value === "en"
    ? {
        eyebrow: "Compensation",
        title: "Allowances and bonuses",
        hint: "Add recurring earnings using your own names. A name is only a label; the calculation rule is configured separately.",
        add: "New component",
        newTitle: "New earning component",
        editTitle: "Component version",
        name: "Name",
        namePlaceholder: "For example: night-shift survival bonus",
        effective: "Effective from month",
        type: "Calculation",
        fixed: "Fixed amount",
        percent: "Percent of base",
        base: "Calculation base",
        nominal: "Monthly salary",
        earned: "Earned base pay",
        nominalHint: "Monthly salary is available only in salary mode.",
        percentValue: "Percent, %",
        amount: "Amount",
        currency: "Currency",
        enabled: "Enabled",
        enabledHint: "Turning a component off creates historical state. Previous versions are not deleted.",
        save: "Save version",
        create: "Create component",
        cancel: "Clear form",
        configured: "Configured components",
        history: "Version history",
        noComponents: "No additional earning components yet.",
        active: "active",
        disabled: "disabled",
        notEffective: "not effective yet",
        edit: "New version",
        created: "Component created",
        saved: "Component version saved",
        invalidName: "Enter a component name",
        invalidMonth: "Choose a valid effective month",
        invalidPercent: "Percent must be between 0.01 and 100000",
        invalidAmount: "Amount must be positive",
        invalidCurrency: "Currency must contain three letters",
      }
    : {
        eyebrow: "Compensation",
        title: "Доплаты и надбавки",
        hint: "Добавляй регулярные начисления со своими названиями. Название — только подпись; правило расчёта задаётся отдельно.",
        add: "Новый компонент",
        newTitle: "Новое начисление",
        editTitle: "Версия компонента",
        name: "Название",
        namePlaceholder: "Например: премия за выживание после ночной смены",
        effective: "Действует с месяца",
        type: "Расчёт",
        fixed: "Фиксированная сумма",
        percent: "Процент от базы",
        base: "База расчёта",
        nominal: "Оклад",
        earned: "Фактически начисленная базовая оплата",
        nominalHint: "Оклад как база доступен только при окладной системе оплаты.",
        percentValue: "Процент, %",
        amount: "Сумма",
        currency: "Валюта",
        enabled: "Включено",
        enabledHint: "Выключение создаёт историческое состояние. Предыдущие версии компонента не удаляются.",
        save: "Сохранить версию",
        create: "Создать компонент",
        cancel: "Очистить форму",
        configured: "Настроенные компоненты",
        history: "История версий",
        noComponents: "Дополнительных начислений пока нет.",
        active: "действует",
        disabled: "выключено",
        notEffective: "ещё не действует",
        edit: "Новая версия",
        created: "Компонент создан",
        saved: "Версия компонента сохранена",
        invalidName: "Укажи название компонента",
        invalidMonth: "Укажи корректный месяц начала действия",
        invalidPercent: "Процент должен быть от 0,01 до 100000",
        invalidAmount: "Сумма должна быть положительной",
        invalidCurrency: "Код валюты должен состоять из трёх букв",
      },
);

function currentMonth(): string {
  const now = new Date();

  return [
    now.getFullYear(),
    String(now.getMonth() + 1).padStart(2, "0"),
  ].join("-");
}

function normalizeCurrency(value: string | null | undefined): string {
  const normalized =
    String(value ?? "")
      .trim()
      .toUpperCase();

  return /^[A-Z]{3}$/.test(normalized)
    ? normalized
    : "RUB";
}

function versionMonth(
  version: PayrollCompensationComponentVersion,
): string {
  return String(
    version.effectiveMonth ?? "",
  );
}

function versionId(
  version: PayrollCompensationComponentVersion,
): number {
  const value = Number(
    version.versionId ?? 0,
  );

  return Number.isFinite(value)
    ? value
    : 0;
}

const groups = computed<ComponentGroup[]>(() => {
  const grouped =
    new Map<
      number,
      PayrollCompensationComponentVersion[]
    >();

  for (
    const version
    of compensationComponentHistory.value
  ) {
    const componentId =
      Number(
        version.componentId ?? 0,
      );

    const month =
      versionMonth(
        version,
      );

    if (
      !Number.isInteger(componentId)
      || componentId < 1
      || !MONTH_RE.test(month)
    ) {
      continue;
    }

    const versions =
      grouped.get(componentId)
      ?? [];

    versions.push(version);
    grouped.set(componentId, versions);
  }

  return [...grouped.entries()]
    .map(([componentId, versions]) => ({
      componentId,
      versions: [...versions].sort(
        (a, b) =>
          versionMonth(b).localeCompare(versionMonth(a))
          || versionId(b) - versionId(a),
      ),
    }))
    .sort(
      (a, b) =>
        groupName(a).localeCompare(
          groupName(b),
          lang.value === "en"
            ? "en"
            : "ru",
        ),
    );
});

function versionAt(
  group: ComponentGroup,
  month: string,
): PayrollCompensationComponentVersion | null {
  if (!MONTH_RE.test(month)) {
    return null;
  }

  return (
    group.versions.find(
      version =>
        versionMonth(version) <= month,
    )
    ?? null
  );
}

function displayVersion(
  group: ComponentGroup,
): PayrollCompensationComponentVersion | null {
  return (
    versionAt(
      group,
      props.month,
    )
    ?? group.versions[0]
    ?? null
  );
}

function groupName(
  group: ComponentGroup,
): string {
  return String(
    displayVersion(group)?.displayName
    ?? "",
  );
}

function formatPercent(
  bps: number | null | undefined,
): string {
  const value =
    Number(
      bps ?? 0,
    ) / 100;

  return `${
    Number.isInteger(value)
      ? value
      : value.toFixed(2)
  }%`;
}

function formatMoneyMinor(
  minor: number | null | undefined,
  currency: string | null | undefined,
): string {
  const value =
    Number(
      minor ?? 0,
    ) / 100;

  const code =
    normalizeCurrency(
      currency,
    );

  try {
    return new Intl.NumberFormat(
      lang.value === "en"
        ? "en-US"
        : "ru-RU",
      {
        style: "currency",
        currency: code,
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      },
    ).format(value);
  }
  catch {
    return `${value.toFixed(2)} ${code}`;
  }
}

function baseLabel(
  base: string | null | undefined,
): string {
  return base === "NOMINAL_SALARY"
    ? text.value.nominal
    : text.value.earned;
}

function summary(
  version: PayrollCompensationComponentVersion,
): string {
  if (
    version.calculationType
    === "FIXED_AMOUNT"
  ) {
    return formatMoneyMinor(
      version.amountMinor,
      version.currencyCode,
    );
  }

  return [
    formatPercent(
      version.rateBps,
    ),
    baseLabel(
      version.calculationBase,
    ),
  ].join(" · ");
}

function statusText(
  group: ComponentGroup,
): string {
  const effective =
    versionAt(
      group,
      props.month,
    );

  if (!effective) {
    return text.value.notEffective;
  }

  return effective.enabled === false
    ? text.value.disabled
    : text.value.active;
}

function resetDraft(
  clearMessage = true,
): void {
  editingComponentId.value = null;

  effectiveMonth.value =
    MONTH_RE.test(props.month)
      ? props.month
      : currentMonth();

  displayName.value = "";
  calculationType.value = "PERCENT_OF_BASE";
  calculationBase.value = "EARNED_BASE_PAY";
  percentValue.value = "10";
  fixedAmount.value = "";

  fixedCurrency.value =
    normalizeCurrency(
      props.currencyCode,
    );

  enabled.value = true;

  if (clearMessage) {
    message.value = "";
  }
}

function editVersion(
  version: PayrollCompensationComponentVersion,
): void {
  const componentId =
    Number(
      version.componentId ?? 0,
    );

  if (
    !Number.isInteger(componentId)
    || componentId < 1
  ) {
    return;
  }

  editingComponentId.value = componentId;

  const month =
    versionMonth(
      version,
    );

  effectiveMonth.value =
    MONTH_RE.test(month)
      ? month
      : (
          MONTH_RE.test(props.month)
            ? props.month
            : currentMonth()
        );

  displayName.value =
    String(
      version.displayName ?? "",
    );

  calculationType.value =
    version.calculationType === "FIXED_AMOUNT"
      ? "FIXED_AMOUNT"
      : "PERCENT_OF_BASE";

  calculationBase.value =
    version.calculationBase === "NOMINAL_SALARY"
      ? "NOMINAL_SALARY"
      : "EARNED_BASE_PAY";

  percentValue.value =
    version.rateBps == null
      ? "10"
      : String(
          Number(version.rateBps) / 100,
        );

  fixedAmount.value =
    version.amountMinor == null
      ? ""
      : String(
          Number(version.amountMinor) / 100,
        );

  fixedCurrency.value =
    normalizeCurrency(
      version.currencyCode
      ?? props.currencyCode,
    );

  enabled.value =
    version.enabled !== false;

  message.value = "";
}

function editGroup(
  group: ComponentGroup,
): void {
  const effective =
    versionAt(
      group,
      props.month,
    );

  const source =
    effective
    ?? group.versions[0];

  if (!source) {
    return;
  }

  editVersion(source);

  /*
   * Editing from the current Payroll month creates a new effective
   * version for that month while inheriting the prior formula.
   */
  if (
    effective
    && MONTH_RE.test(props.month)
  ) {
    effectiveMonth.value =
      props.month;
  }
}

function numeric(
  raw: string,
): number {
  return Number(
    String(raw)
      .replace(",", "."),
  );
}

function versionPayload():
PayrollCompensationComponentVersionInput {
  const name =
    displayName.value.trim();

  if (
    !name
    || name.length > 120
  ) {
    throw new Error(
      text.value.invalidName,
    );
  }

  if (
    calculationType.value
    === "PERCENT_OF_BASE"
  ) {
    const value =
      numeric(
        percentValue.value,
      );

    if (
      !Number.isFinite(value)
      || value < 0.01
      || value > 100000
    ) {
      throw new Error(
        text.value.invalidPercent,
      );
    }

    return {
      displayName: name,
      calculationType: "PERCENT_OF_BASE",
      calculationBase:
        calculationBase.value,
      rateBps:
        Math.round(value * 100),
      enabled:
        enabled.value,
    };
  }

  const value =
    numeric(
      fixedAmount.value,
    );

  if (
    !Number.isFinite(value)
    || value <= 0
    || value > 10_000_000_000
  ) {
    throw new Error(
      text.value.invalidAmount,
    );
  }

  const currency =
    String(
      fixedCurrency.value ?? "",
    )
      .trim()
      .toUpperCase();

  if (
    !/^[A-Z]{3}$/.test(currency)
  ) {
    throw new Error(
      text.value.invalidCurrency,
    );
  }

  return {
    displayName: name,
    calculationType: "FIXED_AMOUNT",
    amountMinor:
      Math.round(value * 100),
    currencyCode:
      currency,
    enabled:
      enabled.value,
  };
}

async function save(): Promise<void> {
  try {
    if (
      !MONTH_RE.test(
        effectiveMonth.value,
      )
    ) {
      throw new Error(
        text.value.invalidMonth,
      );
    }

    const version =
      versionPayload();

    const existingId =
      editingComponentId.value;

    if (existingId == null) {
      await store.createCompensationComponent({
        effectiveMonth:
          effectiveMonth.value,
        version,
      });

      resetDraft(false);

      message.value =
        text.value.created;
    }
    else {
      await store.saveCompensationComponentVersion(
        existingId,
        effectiveMonth.value,
        version,
      );

      message.value =
        text.value.saved;
    }

    messageOk.value = true;
  }
  catch (error) {
    message.value =
      error instanceof Error
        ? error.message
        : String(error);

    messageOk.value = false;
  }
}

watch(
  () => props.month,
  month => {
    if (
      editingComponentId.value == null
      && !displayName.value.trim()
      && MONTH_RE.test(month)
    ) {
      effectiveMonth.value =
        month;
    }
  },
);

watch(
  () => props.currencyCode,
  currency => {
    if (
      editingComponentId.value == null
      && !fixedAmount.value
    ) {
      fixedCurrency.value =
        normalizeCurrency(
          currency,
        );
    }
  },
);

onMounted(() => {
  void store
    .loadCompensationComponents()
    .catch(() => {
      // Store exposes the user-visible failure state.
    });
});
</script>

<template>
  <section
    id="payrollCompensationComponentsCard"
    class="card compensationComponentsCard"
    data-generic-compensation-components
  >
    <div class="componentHead">
      <div>
        <div class="eyebrow">
          {{ text.eyebrow }}
        </div>

        <h3>
          {{ text.title }}
        </h3>

        <p>
          {{ text.hint }}
        </p>
      </div>

      <button
        id="compensationComponentNew"
        type="button"
        :disabled="busy"
        @click="resetDraft()"
      >
        {{ text.add }}
      </button>
    </div>

    <div class="componentWorkspace">
      <form
        id="compensationComponentForm"
        class="componentEditor"
        @submit.prevent="save"
      >
        <div class="componentEditorTitle">
          <b>
            {{
              editingComponentId == null
                ? text.newTitle
                : text.editTitle
            }}
          </b>

        </div>

        <label>
          {{ text.name }}

          <input
            id="compensationComponentName"
            v-model="displayName"
            type="text"
            maxlength="120"
            :placeholder="text.namePlaceholder"
            required
          />
        </label>

        <label>
          {{ text.effective }}

          <input
            id="compensationComponentEffectiveMonth"
            v-model="effectiveMonth"
            type="month"
            required
          />
        </label>

        <div class="componentField">
          <span>
            {{ text.type }}
          </span>

          <div class="componentTypeSwitch">
            <button
              id="compensationComponentPercentType"
              type="button"
              :class="{
                active:
                  calculationType
                  === 'PERCENT_OF_BASE'
              }"
              @click="
                calculationType =
                  'PERCENT_OF_BASE'
              "
            >
              {{ text.percent }}
            </button>

            <button
              id="compensationComponentFixedType"
              type="button"
              :class="{
                active:
                  calculationType
                  === 'FIXED_AMOUNT'
              }"
              @click="
                calculationType =
                  'FIXED_AMOUNT'
              "
            >
              {{ text.fixed }}
            </button>
          </div>
        </div>

        <template
          v-if="
            calculationType
            === 'PERCENT_OF_BASE'
          "
        >
          <label>
            {{ text.base }}

            <select
              id="compensationComponentBase"
              v-model="calculationBase"
            >
              <option value="EARNED_BASE_PAY">
                {{ text.earned }}
              </option>

              <option value="NOMINAL_SALARY">
                {{ text.nominal }}
              </option>
            </select>
          </label>

          <p
            v-if="
              calculationBase
              === 'NOMINAL_SALARY'
            "
            class="componentHint"
          >
            {{ text.nominalHint }}
          </p>

          <label>
            {{ text.percentValue }}

            <input
              id="compensationComponentPercent"
              v-model="percentValue"
              type="number"
              min="0.01"
              max="100000"
              step="0.01"
              required
            />
          </label>
        </template>

        <template v-else>
          <div class="componentMoneyGrid">
            <label>
              {{ text.amount }}

              <input
                id="compensationComponentAmount"
                v-model="fixedAmount"
                type="number"
                min="0.01"
                max="10000000000"
                step="0.01"
                required
              />
            </label>

            <label>
              {{ text.currency }}

              <input
                id="compensationComponentCurrency"
                v-model="fixedCurrency"
                type="text"
                maxlength="3"
                pattern="[A-Za-z]{3}"
                required
              />
            </label>
          </div>
        </template>

        <label class="componentToggle">
          <input
            id="compensationComponentEnabled"
            v-model="enabled"
            type="checkbox"
          />

          <span>
            <b>
              {{ text.enabled }}
            </b>

            <small>
              {{ text.enabledHint }}
            </small>
          </span>
        </label>

        <div class="componentActions">
          <button
            id="compensationComponentSave"
            class="primary"
            type="submit"
            :disabled="busy"
          >
            {{
              editingComponentId == null
                ? text.create
                : text.save
            }}
          </button>

          <button
            type="button"
            :disabled="busy"
            @click="resetDraft()"
          >
            {{ text.cancel }}
          </button>
        </div>

        <div
          id="compensationComponentMessage"
          class="componentMessage"
          :class="{
            ok: message && messageOk,
            err: message && !messageOk,
          }"
          role="status"
          aria-live="polite"
        >
          {{
            message
            || compensationComponentsError
          }}
        </div>
      </form>

      <div class="componentList">
        <div class="eyebrow">
          {{ text.configured }}
        </div>

        <article
          v-for="group in groups"
          :key="group.componentId"
          class="componentRow"
        >
          <div class="componentRowHead">
            <span>
              <b>
                {{
                  displayVersion(group)
                    ?.displayName
                  || '—'
                }}
              </b>

              <small>
                {{
                  displayVersion(group)
                    ? summary(
                        displayVersion(group)!
                      )
                    : '—'
                }}
              </small>
            </span>

            <span
              class="componentStatus"
              :class="{
                off:
                  versionAt(
                    group,
                    month
                  )?.enabled
                  === false,
              }"
            >
              {{ statusText(group) }}
            </span>
          </div>

          <button
            type="button"
            :disabled="busy"
            @click="editGroup(group)"
          >
            {{ text.edit }}
          </button>

          <details class="componentHistory">
            <summary>
              {{ text.history }}
              ·
              {{ group.versions.length }}
            </summary>

            <button
              v-for="version in group.versions"
              :key="
                version.versionId
                ?? `${group.componentId}-${version.effectiveMonth}`
              "
              type="button"
              class="componentHistoryRow"
              :disabled="busy"
              @click="editVersion(version)"
            >
              <span>
                <b>
                  {{ version.effectiveMonth }}
                </b>

                <small>
                  {{ summary(version) }}
                </small>
              </span>

              <span>
                {{
                  version.enabled === false
                    ? text.disabled
                    : text.active
                }}
              </span>
            </button>
          </details>
        </article>

        <div
          v-if="
            !groups.length
            && !compensationComponentsLoading
          "
          id="compensationComponentsEmpty"
          class="payrollEmpty"
        >
          {{ text.noComponents }}
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.compensationComponentsCard {
  padding: 20px;
  margin-bottom: 16px;
}

.componentHead {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 16px;
}

.componentHead h3 {
  margin: 2px 0 6px;
}

.componentHead p,
.componentHint,
.componentMessage {
  margin: 0;
  color: var(--dim);
  font-size: 12px;
  line-height: 1.45;
}

.componentWorkspace {
  display: grid;
  grid-template-columns:
    minmax(280px, 0.9fr)
    minmax(320px, 1.1fr);
  gap: 16px;
  align-items: start;
}

.componentEditor,
.componentList {
  display: grid;
  gap: 12px;
}

.componentEditor {
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 14px;
}

.componentEditor label,
.componentField {
  display: grid;
  gap: 5px;
  color: var(--dim);
  font-size: 12px;
  font-weight: 800;
}

.componentEditorTitle {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.componentTypeSwitch {
  display: grid;
  grid-template-columns:
    repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.componentTypeSwitch button.active {
  border-color: var(--accent);
  box-shadow:
    inset 0 0 0 1px var(--accent);
}

.componentMoneyGrid {
  display: grid;
  grid-template-columns:
    minmax(0, 1fr)
    110px;
  gap: 10px;
}

.componentToggle {
  display: flex !important;
  align-items: flex-start;
  gap: 10px !important;
  color: var(--text) !important;
}

.componentToggle span {
  display: grid;
  gap: 3px;
}

.componentToggle small {
  color: var(--dim);
  font-weight: 600;
  line-height: 1.4;
}

.componentActions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.componentMessage {
  min-height: 18px;
}

.componentMessage.ok {
  color: var(--ok);
}

.componentMessage.err {
  color: var(--danger);
}

.componentRow {
  display: grid;
  gap: 9px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 13px;
}

.componentRowHead {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.componentRowHead > span:first-child {
  display: grid;
  gap: 3px;
}

.componentRowHead small {
  color: var(--dim);
}

.componentStatus {
  flex: none;
  font-size: 11px;
  font-weight: 800;
  color: var(--ok);
}

.componentStatus.off {
  color: var(--dim);
}

.componentHistory {
  margin-top: 2px;
}

.componentHistory summary {
  cursor: pointer;
  color: var(--dim);
  font-size: 12px;
}

.componentHistoryRow {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 7px;
  padding: 9px 10px;
  text-align: left;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: transparent;
}

.componentHistoryRow span:first-child {
  display: grid;
  gap: 2px;
}

.componentHistoryRow small {
  color: var(--dim);
}

@media (max-width: 820px) {
  .componentHead {
    flex-direction: column;
  }

  .componentWorkspace {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 520px) {
  .componentMoneyGrid,
  .componentTypeSwitch {
    grid-template-columns: 1fr;
  }

  .componentRowHead {
    flex-direction: column;
  }
}
</style>
