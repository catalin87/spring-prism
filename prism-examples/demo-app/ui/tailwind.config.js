/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,jsx}"],
  theme: {
    extend: {
      colors: {
        ink: "#020617",
        panel: "#0f172a",
        line: "#1e293b",
        glow: "#22d3ee",
        accent: "#38bdf8",
      },
      boxShadow: {
        panel: "0 0 0 1px rgba(148,163,184,0.18), 0 18px 50px rgba(2,6,23,0.35)",
      },
    },
  },
  plugins: [],
};
