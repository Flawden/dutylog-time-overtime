import { createGeneratedDutyLogApiClient } from "@/platform/api/generatedClient";
import type { DutyLogApiSchemas } from "@/generated/dutylog-api";
import type { CalendarMode, CalendarRangeBundle } from "../types/domain";
import { calendarLoadRange, normalizeCalendarBundle, todayIso, validDate } from "../types/model";

const client = createGeneratedDutyLogApiClient();

function requireResponse<T>(value: T | null, operation: string): T {
  if (value === null) throw new Error(`DutyLog API returned an empty response for ${operation}`);
  return value;
}

export const calendarTimelineApi = Object.freeze({
  async load(focusDate: string, preferWorkDate = false): Promise<{ bundle: CalendarRangeBundle; workDate: string; focusDate: string }> {
    let workDate = validDate(focusDate, todayIso());
    try {
      const context = await client.request("getTimeContext");
      if (context?.workDate) workDate = validDate(context.workDate, workDate);
    } catch {
      // Calendar remains usable when the optional time-context read fails.
    }
    const rangeFocus = preferWorkDate ? workDate : validDate(focusDate, workDate);
    const range = calendarLoadRange(rangeFocus);
    const response = await client.request("calendarRange", { query: range });
    return { bundle: normalizeCalendarBundle(response, range.from, range.to), workDate, focusDate: rangeFocus };
  },
  async setLayerVisibility(id: number, visible: boolean): Promise<void> {
    await client.request("updateCalendarLayer", { path: { id }, body: { visible } });
  },
  async saveLayerOverride(id: number, date: string, body: DutyLogApiSchemas.CalendarLayerOverrideInput): Promise<void> {
    await client.request("upsertCalendarLayerOverride", { path: { id, date }, body });
  },
  async deleteLayerOverride(id: number, date: string): Promise<void> {
    await client.request("deleteCalendarLayerOverride", { path: { id, date } });
  },
  async workdayTruth(date: string): Promise<DutyLogApiSchemas.WorkdayTruth> {
    return requireResponse(await client.request("workdayTruth", { path: { date } }), "workdayTruth");
  },
  async productionCalendarMonth(month: string): Promise<DutyLogApiSchemas.ProductionCalendarMonth> {
    return requireResponse(await client.request("productionCalendarMonth", { path: { month } }), "productionCalendarMonth");
  },
  async saveProductionDay(date: string, body: DutyLogApiSchemas.ProductionCalendarDayInput): Promise<DutyLogApiSchemas.ProductionCalendarDay> {
    return requireResponse(await client.request("upsertProductionCalendarDay", { path: { date }, body }), "upsertProductionCalendarDay");
  },
  async deleteProductionDay(date: string): Promise<void> {
    await client.request("deleteProductionCalendarDayOverride", { path: { date } });
  },
  async createActualWork(body: DutyLogApiSchemas.ActualWorkIntervalInput): Promise<DutyLogApiSchemas.ActualWorkInterval> {
    return requireResponse(await client.request("createActualWorkInterval", { body }), "createActualWorkInterval");
  },
  async updateActualWork(id: number, body: DutyLogApiSchemas.ActualWorkIntervalInput): Promise<DutyLogApiSchemas.ActualWorkInterval> {
    return requireResponse(await client.request("updateActualWorkInterval", { path: { id }, body }), "updateActualWorkInterval");
  },
  async deleteActualWork(id: number): Promise<void> {
    await client.request("deleteActualWorkInterval", { path: { id } });
  },
  async listScheduleTemplates(): Promise<DutyLogApiSchemas.ScheduleTemplate[]> {
    return (await client.request("listScheduleTemplates")) ?? [];
  },
  async previewScheduleTemplate(id: number, body: DutyLogApiSchemas.ScheduleTemplateApplyRequest): Promise<DutyLogApiSchemas.ScheduleTemplatePreview> {
    return (await client.request("previewScheduleTemplate", { path: { id }, body })) ?? {};
  },
  async applyScheduleTemplate(id: number, body: DutyLogApiSchemas.ScheduleTemplateApplyRequest): Promise<DutyLogApiSchemas.ScheduleTemplateApplyResult> {
    return (await client.request("applyScheduleTemplate", { path: { id }, body })) ?? {};
  },
});

export type CalendarTimelineApi = typeof calendarTimelineApi;
export type { CalendarMode };
