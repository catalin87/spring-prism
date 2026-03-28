import { create } from "zustand";

const DEFAULT_CONFIG = {
  integration: "spring-ai",
  failureMode: "FAIL_SAFE",
  nlpMode: "HEURISTIC",
  routeMode: "AUTO",
  message: "",
  rulePacks: [],
};

export const useLabStore = create((set, get) => ({
  bootstrap: null,
  nodes: [],
  metrics: null,
  result: null,
  pollingHistory: [],
  loading: false,
  error: "",
  config: DEFAULT_CONFIG,
  setBootstrap(bootstrap) {
    const firstPrompt = bootstrap?.promptPresets?.[0]?.text ?? "";
    const defaults = bootstrap?.defaultRulePacks ?? [];
    set((state) => ({
      bootstrap,
      nodes: bootstrap?.nodes ?? [],
      config: {
        ...state.config,
        message: state.config.message || firstPrompt,
        rulePacks: state.config.rulePacks.length ? state.config.rulePacks : defaults,
        failureMode: bootstrap?.defaultFailureMode ?? state.config.failureMode,
        nlpMode: bootstrap?.defaultNlpMode ?? state.config.nlpMode,
        routeMode: bootstrap?.defaultRouteMode ?? state.config.routeMode,
      },
    }));
  },
  setNodes(nodes) {
    set({ nodes });
  },
  setMetrics(metrics) {
    set((state) => ({
      metrics,
      pollingHistory: [
        ...state.pollingHistory.slice(-19),
        {
          time: new Date().toLocaleTimeString(),
          blockedRequests: metrics?.blockedRequestCount ?? 0,
          blockedResponses: metrics?.blockedResponseCount ?? 0,
          totalActiveRules: metrics?.totalActiveRules ?? 0,
        },
      ],
    }));
  },
  setResult(result) {
    set({ result });
  },
  setLoading(loading) {
    set({ loading });
  },
  setError(error) {
    set({ error });
  },
  updateConfig(patch) {
    set((state) => ({ config: { ...state.config, ...patch } }));
  },
  toggleRulePack(rulePack) {
    const current = get().config.rulePacks;
    set((state) => ({
      config: {
        ...state.config,
        rulePacks: current.includes(rulePack)
          ? current.filter((item) => item !== rulePack)
          : [...current, rulePack],
      },
    }));
  },
  selectPrompt(promptText) {
    set((state) => ({ config: { ...state.config, message: promptText } }));
  },
  resetOutcomes() {
    set({ result: null, error: "" });
  },
}));
