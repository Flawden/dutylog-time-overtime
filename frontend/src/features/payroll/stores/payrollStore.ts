import { defineStore } from "pinia";
import { createPayrollApi, type PayrollApi } from "../api/payrollApi";
import type {
  PayPricingTerm,
  PayPricingTermInput,
  PayrollAdjustmentInput,
  PayrollCompensationTermInput,
  PayrollPeriod,
  PayrollSettingsInput,
  ProductionCalendarDayInput,
} from "../types/domain";

let api: PayrollApi = createPayrollApi(); let loadSequence = 0;
function errorMessage(error: unknown): string { return error instanceof Error && error.message ? error.message : "Не удалось выполнить запрос"; }

export const usePayrollStore = defineStore("dutylog-payroll", {
  state: () => ({
    month: "",
    period: null as PayrollPeriod | null,
    pricingTerms: [] as PayPricingTerm[],
    loading: false,
    loaded: false,
    error: "",
  }),
  actions: {
    async load(month: string): Promise<void> {
      const sequence = ++loadSequence; this.month = month; this.loading = true; this.error = "";
      try {
        const [period, pricingTerms] = await Promise.all([
          api.period(month),
          api.pricingTerms(),
        ]);
        if (sequence !== loadSequence) return;
        this.period = period;
        this.pricingTerms = Array.isArray(pricingTerms) ? pricingTerms : [];
        this.loaded = period != null;
      }
      catch (error) { if (sequence === loadSequence) { this.loaded = false; this.error = errorMessage(error); } throw error; }
      finally { if (sequence === loadSequence) this.loading = false; }
    },
    async saveSettings(body: PayrollSettingsInput): Promise<void> { await api.updateSettings(body); await this.load(this.month); },
    async saveCompensationTerm(month: string, body: PayrollCompensationTermInput): Promise<void> { await api.upsertCompensationTerm(month, body); await this.load(this.month); },
    async deleteCompensationTerm(month: string): Promise<void> { await api.deleteCompensationTerm(month); await this.load(this.month); },
    async savePricingTerm(effectiveFrom: string, body: PayPricingTermInput): Promise<void> {
      await api.upsertPricingTerm(effectiveFrom, body);
      await this.load(this.month);
    },
    async deletePricingTerm(effectiveFrom: string): Promise<void> {
      await api.deletePricingTerm(effectiveFrom);
      await this.load(this.month);
    },
    async addAdjustment(body: PayrollAdjustmentInput): Promise<void> { await api.addAdjustment(body); await this.load(this.month); },
    async calculate(): Promise<void> { await api.calculate(this.month); await this.load(this.month); },
    async saveProductionDay(date: string, body: ProductionCalendarDayInput): Promise<void> { await api.upsertProductionDay(date, body); await this.load(this.month); },
    async deleteProductionDay(date: string): Promise<void> { await api.deleteProductionDay(date); await this.load(this.month); },
  },
});
