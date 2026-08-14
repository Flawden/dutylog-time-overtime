<script setup lang="ts">
import { storeToRefs } from "pinia";
import { useShellStore } from "@/app/shellStore";

const shell = useShellStore();
const { language } = storeToRefs(shell);
</script>

<template>
  <section class="vue-async-workspace-loading" data-vue-async-workspace-loading role="status" aria-live="polite">
    <span class="vue-async-workspace-loading__dot" aria-hidden="true"></span>
    {{ language === "en" ? "Loading section…" : "Загружаю раздел…" }}
  </section>
</template>
