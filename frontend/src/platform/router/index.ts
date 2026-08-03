import { defineComponent, h } from "vue";
import { createMemoryHistory, createRouter } from "vue-router";

const LegacyHostRoute = defineComponent({
  name: "LegacyHostRoute",
  setup: () => () => h("span", { hidden: true, "data-vue-route": "legacy-host" }, "legacy-host"),
});

// Memory history is intentional during strangler migration. Vue Router must not
// compete with the released legacy hash router until the app-shell migration.
export const platformRouter = createRouter({
  history: createMemoryHistory("/"),
  routes: [{ path: "/", name: "legacy-host", component: LegacyHostRoute }],
});
