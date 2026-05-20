import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./app/**/*.{ts,tsx}", "./hooks/**/*.{ts,tsx}", "./lib/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        bg:       "var(--bg)",
        surface:  "var(--surface)",
        surface2: "var(--surface-2)",
        border:   "var(--border)",
        text:     "var(--text)",
        muted:    "var(--muted)",
        brand:  { DEFAULT: "#6366f1", dark: "#4f46e5" },
        pass:   "#22c55e",
        fail:   "#ef4444",
        waiting:"#f59e0b",
      },
    },
  },
  plugins: [],
};

export default config;
