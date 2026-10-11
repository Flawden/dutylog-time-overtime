<script setup lang="ts">
import type { DutyLogApiSchemas } from "@/generated/dutylog-api";
export type SourceDraft = Omit<DutyLogApiSchemas.HolidayReviewSource, "kind"> & { kind: DutyLogApiSchemas.HolidayReviewSource["kind"] | "" };
withDefaults(defineProps<{ source: SourceDraft; disabled?: boolean; english: boolean; includeBasis?: boolean }>(), { includeBasis: true, disabled: false });
</script>
<template>
  <fieldset :disabled="disabled ?? false" class="source-fields">
    <label>{{ english ? 'Document type' : 'Тип документа' }}
      <select v-model="source.kind" required>
        <option value="">{{ english ? 'Choose' : 'Выбери' }}</option>
        <option value="COLLECTIVE_AGREEMENT">{{ english ? 'Collective agreement' : 'Коллективный договор' }}</option>
        <option value="LOCAL_NORMATIVE_ACT">{{ english ? 'Local normative act' : 'Локальный нормативный акт' }}</option>
        <option value="EMPLOYMENT_CONTRACT">{{ english ? 'Employment contract' : 'Трудовой договор' }}</option>
      </select>
    </label>
    <label>{{ english ? 'Document and section' : 'Документ и пункт' }}<input v-model="source.reference" required maxlength="500" /></label>
    <label>{{ english ? 'Document revision/date' : 'Редакция / дата документа' }}<input v-model="source.revision" required maxlength="160" /></label>
    <label v-if="includeBasis !== false">{{ english ? 'Grounds for this decision' : 'Основание решения' }}<textarea v-model="source.basis" required maxlength="2000" /></label>
  </fieldset>
</template>
<style scoped>
.source-fields { border: 0; padding: 0; display: grid; gap: .75rem; }
label { display: grid; gap: .25rem; }
input, select, textarea { width: 100%; box-sizing: border-box; }
</style>
