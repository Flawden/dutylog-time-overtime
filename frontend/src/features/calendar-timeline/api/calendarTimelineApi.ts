import { createGeneratedDutyLogApiClient } from "@/platform/api/generatedClient";
import type { CalendarMode, CalendarRangeBundle } from "../types/domain";
import { calendarLoadRange, normalizeCalendarBundle, todayIso, validDate } from "../types/model";

const client = createGeneratedDutyLogApiClient();

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
});

export type CalendarTimelineApi = typeof calendarTimelineApi;
export type { CalendarMode };
