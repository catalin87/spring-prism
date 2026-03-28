import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  base: "/lab/",
  plugins: [react()],
  build: {
    outDir: "../src/main/resources/static/lab",
    emptyOutDir: true,
  },
});
