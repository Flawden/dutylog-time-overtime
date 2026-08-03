<script setup lang="ts">
import type { NavigationItem } from "@/app/navigation";

defineProps<{ name: NavigationItem["icon"] | "menu" | "close" | "check" | "info" }>();
</script>

<template>
  <svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true">
    <template v-if="name === 'today'">
      <path d="m3 11 9-7 9 7"/><path d="M5.5 9.5V20h13V9.5M9.5 20v-6h5v6"/>
    </template>
    <template v-else-if="name === 'calendar'">
      <path d="M4 5.5h16v14H4z"/><path d="M8 3v5M16 3v5M4 9.5h16"/>
    </template>
    <template v-else-if="name === 'vacation'">
      <path d="M4 18h16"/><path d="M6 18c1-5 3-8 6-12 3 4 5 7 6 12"/><path d="M9 10h6M8 14h8"/>
    </template>
    <template v-else-if="name === 'overtime'">
      <circle cx="12" cy="12" r="8"/><path d="M12 7v5l3 2M7 4.5 5 2.8M17 4.5l2-1.7"/>
    </template>
    <template v-else-if="name === 'payroll'">
      <path d="M5 3h14v18H5z"/><path d="M8 7h8M8 11h8M8 15h3M14 15h2"/>
    </template>
    <template v-else-if="name === 'tasks'">
      <path d="M5 4h14v16H5z"/><path d="m8 9 1.5 1.5L12 8M13.5 10H16M8 15h8"/>
    </template>
    <template v-else-if="name === 'important'">
      <path d="m12 3 2.7 5.5 6.1.9-4.4 4.3 1 6.1-5.4-2.9-5.4 2.9 1-6.1-4.4-4.3 6.1-.9Z"/>
    </template>
    <template v-else-if="name === 'settings'">
      <circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1A1.7 1.7 0 0 0 9 4.6 1.7 1.7 0 0 0 10 3V2.8h4V3a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v4H21a1.7 1.7 0 0 0-1.6 1Z"/>
    </template>
    <template v-else-if="name === 'admin'">
      <path d="M12 3 5 6v5c0 4.6 2.8 8.3 7 10 4.2-1.7 7-5.4 7-10V6Z"/><path d="m9 12 2 2 4-4"/>
    </template>
    <template v-else-if="name === 'menu'">
      <path d="M5 7h14M5 12h14M5 17h14"/>
    </template>
    <template v-else-if="name === 'close'">
      <path d="m6 6 12 12M18 6 6 18"/>
    </template>
    <template v-else-if="name === 'check'">
      <path d="m5 12 4 4L19 6"/>
    </template>
    <template v-else>
      <circle cx="12" cy="12" r="9"/><path d="M12 10v6M12 7h.01"/>
    </template>
  </svg>
</template>
