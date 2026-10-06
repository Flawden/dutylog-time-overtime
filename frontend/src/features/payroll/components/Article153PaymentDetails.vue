<script setup lang="ts">
import { computed } from "vue";
import type { DutyLogApiSchemas } from "@/generated/dutylog-api";
import { ordinaryPremiumMessage } from "../presentation/ordinaryPremiumMessage";
const props = defineProps<{ summary: DutyLogApiSchemas.PayrollArticle153 | undefined; currency: string; language: string }>();
const explanation = computed(() => {
  const s=props.summary, en=props.language==='en';
  if (!s) return '';
  if (s.status==='REVIEW_BLOCKED') return ordinaryPremiumMessage(s.blockingReason,props.language);
  if (s.status==='LEGACY_UNREVIEWED') return en ? 'Configured rules; holiday grounds unreviewed.' : 'По настроенным правилам; основания праздничной оплаты не подтверждены.';
  const money=new Intl.NumberFormat(en?'en-US':'ru-RU',{style:'currency',currency:props.currency});
  const labels=en?['Grounds reviewed; already included.','Qualified minutes','Holiday tariff','Holiday components','Night premium']:['Основания подтверждены; уже учтено.','Подтверждённые минуты','Праздничная: тариф','Праздничная: компоненты','Ночная'];
  return [labels[0], `${labels[1]}: ${s.qualifiedMinutes}`, ...[s.tariffPremiumMinor,s.componentPremiumMinor,s.preservedNightPremiumMinor].map((amount,i)=>`${labels[i+2]}: ${money.format(Number(amount??0)/100)}`)].join('\n');
});
</script>
<template>
  <small v-if="summary" :data-testid="`article153-${summary.status.toLowerCase()}`" style="white-space: pre-line">{{ explanation }}</small>
</template>
