import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./app/**/*.{ts,tsx}", "./hooks/**/*.{ts,tsx}", "./lib/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        brand: { DEFAULT: "#6366f1", dark: "#4f46e5" },
        pass: "#22c55e",
        fail: "#ef4444",
        waiting: "#f59e0b",
        skipped: "#94a3b8",
      },
    },
  },
  plugins: [],
};

export default config;
