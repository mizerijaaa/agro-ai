import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    // Default 5173; if that port is busy, Vite uses 5174, 5175, … (backend CORS now allows all localhost ports)
    port: 5173,
    strictPort: false,
  },
});
