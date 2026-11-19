import { bindings, defineConfig, triggers } from "cf/config";

export default defineConfig({
  accountId: "4d926dc2e98294788bd48ef13e1a49bf",
  worker: {
    name: "pabi-flip-diagnostics",
    entrypoint: "./worker.js",
    compatibilityDate: "2026-11-19",
    workersDev: true,
    previewUrls: false,
    env: {
      DB: bindings.d1({
        id: "e66c7fa0-17d4-4f62-8601-b61f02d6543f",
        name: "pabi-flip-diagnostics"
      })
    },
    triggers: [triggers.scheduled({ schedule: "0 3 * * *" })]
  }
});
