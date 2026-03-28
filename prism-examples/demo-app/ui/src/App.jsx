import Editor from "@monaco-editor/react";
import { motion } from "framer-motion";
import {
  Activity,
  AlertTriangle,
  ArrowUpRight,
  Bot,
  Brain,
  DatabaseZap,
  Gauge,
  Globe,
  Play,
  Radar,
  ServerCog,
  Shield,
  ShieldAlert,
  SlidersHorizontal,
} from "lucide-react";
import { useEffect, useMemo } from "react";
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { useLabStore } from "./lib/store";

async function fetchJson(url, options = {}) {
  const response = await fetch(url, options);
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(payload.message || "Enterprise Lab request failed.");
  }
  return payload;
}

function App() {
  const {
    bootstrap,
    nodes,
    metrics,
    result,
    pollingHistory,
    loading,
    error,
    config,
    setBootstrap,
    setNodes,
    setMetrics,
    setResult,
    setLoading,
    setError,
    updateConfig,
    toggleRulePack,
    selectPrompt,
    resetOutcomes,
  } = useLabStore();

  useEffect(() => {
    let cancelled = false;

    async function bootstrapLab() {
      try {
        const [bootstrapPayload, metricsPayload, nodesPayload] = await Promise.all([
          fetchJson("/lab/api/bootstrap"),
          fetchJson("/lab/api/metrics"),
          fetchJson("/lab/api/nodes"),
        ]);
        if (!cancelled) {
          setBootstrap(bootstrapPayload);
          setMetrics(metricsPayload);
          setNodes(nodesPayload);
        }
      } catch (exception) {
        if (!cancelled) {
          setError(exception.message);
        }
      }
    }

    bootstrapLab();
    const timer = window.setInterval(async () => {
      try {
        const [metricsPayload, nodesPayload] = await Promise.all([
          fetchJson("/lab/api/metrics"),
          fetchJson("/lab/api/nodes"),
        ]);
        if (!cancelled) {
          setMetrics(metricsPayload);
          setNodes(nodesPayload);
        }
      } catch (exception) {
        if (!cancelled) {
          setError(exception.message);
        }
      }
    }, 5000);

    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [setBootstrap, setError, setMetrics, setNodes]);

  const clusterHealthy = nodes.every((node) => node.reachable);
  const allRulePacks = bootstrap?.availableRulePacks ?? [];
  const presets = bootstrap?.promptPresets ?? [];
  const activeNodeCards = useMemo(
    () =>
      nodes.map((node) => ({
        ...node,
        isBusy:
          result &&
          (result.tokenizeNodeId === node.nodeId ||
            result.restoreNodeId === node.nodeId ||
            (result.traceEvents || []).some((event) => event.nodeId === node.nodeId)),
      })),
    [nodes, result]
  );

  const postureIcon = config.failureMode === "FAIL_CLOSED" ? ShieldAlert : Shield;
  const PostureIcon = postureIcon;
  const privacyScore = computePrivacyScore(metrics, nodes);
  const outageSimulated = nodes.some((node) => node.redisOutageSimulated);
  async function runTrace() {
    setLoading(true);
    setError("");
    setResult(null);
    try {
      const payload = await fetchJson("/lab/api/run", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(config),
      });
      setResult(payload);
      const [metricsPayload, nodesPayload] = await Promise.all([
        fetchJson("/lab/api/metrics"),
        fetchJson("/lab/api/nodes"),
      ]);
      setMetrics(metricsPayload);
      setNodes(nodesPayload);
    } catch (exception) {
      setResult(null);
      setError(exception.message);
    } finally {
      setLoading(false);
    }
  }

  async function simulateOutage(active) {
    setLoading(true);
    setError("");
    try {
      await fetchJson(active ? "/lab/api/simulators/redis-outage" : "/lab/api/simulators/redis-recover", {
        method: "POST",
      });
      const [metricsPayload, nodesPayload] = await Promise.all([
        fetchJson("/lab/api/metrics"),
        fetchJson("/lab/api/nodes"),
      ]);
      setMetrics(metricsPayload);
      setNodes(nodesPayload);
    } catch (exception) {
      setError(exception.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="command-shell">
      <aside className="command-sidebar">
        <div className="brand-block">
          <div className="brand-mark">
            <Radar className="h-5 w-5 text-cyan-200" />
          </div>
          <div>
            <div className="brand-name">Spring Prism</div>
            <div className="brand-subtitle">Enterprise Lab Sandbox</div>
          </div>
        </div>

        <div className="sidebar-section">
          <SectionHeading title="Posture" />
          <div className="control-stack">
            <label className="control-field">
              <span>Failure Mode</span>
              <select value={config.failureMode} onChange={(event) => updateConfig({ failureMode: event.target.value })}>
                {bootstrap?.availableFailureModes?.map((mode) => (
                  <option key={mode} value={mode}>
                    {mode}
                  </option>
                ))}
              </select>
            </label>

            <label className="control-field">
              <span>NLP Mode</span>
              <select value={config.nlpMode} onChange={(event) => updateConfig({ nlpMode: event.target.value })}>
                {bootstrap?.availableNlpModes?.map((mode) => (
                  <option key={mode} value={mode}>
                    {mode}
                  </option>
                ))}
              </select>
            </label>

            <label className="control-field">
              <span>Integration</span>
              <select value={config.integration} onChange={(event) => updateConfig({ integration: event.target.value })}>
                {bootstrap?.integrations?.map((integration) => (
                  <option key={integration} value={integration}>
                    {integration}
                  </option>
                ))}
              </select>
            </label>

            <label className="control-field">
              <span>Route Strategy</span>
              <select value={config.routeMode} onChange={(event) => updateConfig({ routeMode: event.target.value })}>
                {bootstrap?.availableRouteModes?.map((mode) => (
                  <option key={mode} value={mode}>
                    {mode}
                  </option>
                ))}
              </select>
            </label>
          </div>
        </div>

        <div className="sidebar-section">
          <SectionHeading title="Rulepack Grid" />
          <div className="rulepack-grid">
            {allRulePacks.map((rulePack) => {
              const selected = config.rulePacks.includes(rulePack.id);
              return (
                <button
                  key={rulePack.id}
                  type="button"
                  onClick={() => toggleRulePack(rulePack.id)}
                  className={`rulepack-chip ${selected ? "rulepack-chip-active" : ""}`}
                >
                  <span className="text-lg">{rulePack.flag}</span>
                  <span className="rulepack-code">{rulePack.id}</span>
                </button>
              );
            })}
          </div>
        </div>

        <div className="sidebar-section">
          <SectionHeading title="Cluster Status" />
          <div className="sidebar-footer sidebar-footer-tight">
            <StatusModule
              title="Nodes"
              status={clusterHealthy ? "Healthy" : "Degraded"}
              detail={clusterHealthy ? "All nodes reachable" : "A node is currently unreachable"}
              ok={clusterHealthy}
            />
            <StatusModule
              title="Redis Shared Vault"
              status={metrics?.sharedVaultReady ? "Ready" : "Warming Up"}
              detail="Shared restore path enabled"
              ok={Boolean(metrics?.sharedVaultReady)}
            />
          </div>
        </div>

        <div className="sidebar-section">
          <SectionHeading title="Shortcuts" />
          <div className="shortcut-list shortcut-list-sidebar">
            <Shortcut icon={Gauge} label="Embedded Dashboard" href={bootstrap?.dashboardUrl} />
            <Shortcut icon={Brain} label="Grafana" href={bootstrap?.grafanaUrl} />
            <Shortcut icon={ArrowUpRight} label="Actuator Metrics JSON" href={bootstrap?.metricsUrl} />
          </div>
        </div>

        <div className="sidebar-spacer" />
        <div className="sidebar-footer">
          <StatusModule
            title="Lab Mode"
            status="Single Page"
            detail="Controls, tracing and metrics live in one workspace."
            ok
          />
        </div>
      </aside>

      <main className="command-main">
        <header className="command-header">
          <div className="header-title-block">
            <h1>Distributed Privacy Command Center</h1>
          </div>

          <div className="header-actions">
            <div className="posture-badge">
              <span className="posture-label">Posture Mode:</span>
              <span className={`posture-value ${config.failureMode === "FAIL_CLOSED" ? "text-amber-300" : "text-cyan-300"}`}>
                {config.failureMode}
              </span>
              <div className={`posture-toggle ${config.failureMode === "FAIL_CLOSED" ? "posture-toggle-closed" : ""}`}>
                <div className="posture-toggle-thumb" />
              </div>
            </div>

            <div className="lab-badge">Enterprise Lab</div>

            <div className="score-card">
              <div className="score-topline">
                <span>Global Privacy Score:</span>
                <MiniTrend />
              </div>
              <div className="score-mainline">
                <span className="score-value">{privacyScore}%</span>
                <div className="score-bar">
                  <div className="score-bar-fill" style={{ width: `${privacyScore}%` }} />
                </div>
              </div>
            </div>
          </div>
        </header>

        {error ? (
          <div className="top-alert-wrap">
            <ErrorBanner message={error} />
          </div>
        ) : null}

        <div className="content-grid">
          <section className="workspace-column">
            <PanelFrame title="Workspace">
              <div className="workspace-control-shell">
                <div className="workspace-control-shell-header">Raw Input Controls</div>
                <div className="workspace-control-shell-body">
                  <PanelActions
                    loading={loading}
                    outageSimulated={outageSimulated}
                    onRun={runTrace}
                    onToggleOutage={() => simulateOutage(!outageSimulated)}
                  />
                  <div className="workspace-preset-card">
                    <div className="workspace-preset-label">Preset Payloads</div>
                    <div className="workspace-preset-list">
                      {presets.map((preset) => (
                        <button
                          key={preset.id}
                          type="button"
                          className="preset-button preset-button-inline"
                          onClick={() => selectPrompt(preset.text)}
                        >
                          {preset.label}
                        </button>
                      ))}
                    </div>
                  </div>
                </div>
              </div>

              <div className="workspace-grid">
                <WorkspacePanel title="Raw Input">
                  <Editor
                    height="230px"
                    theme="vs-dark"
                    language="plaintext"
                    value={config.message}
                    onChange={(value) => updateConfig({ message: value || "" })}
                    options={editorOptions(false)}
                  />
                </WorkspacePanel>

                <WorkspacePanel
                  title="Restored Output"
                >
                  <Editor
                    height="230px"
                    theme="vs-dark"
                    language="plaintext"
                    value={result?.restoredResponse ?? ""}
                    options={editorOptions(true)}
                  />
                </WorkspacePanel>

                <WorkspacePanel
                  title="Sanitized Outbound"
                >
                  <Editor
                    height="230px"
                    theme="vs-dark"
                    language={config.integration === "spring-ai" ? "plaintext" : "json"}
                    value={result?.sanitizedOutbound ?? ""}
                    options={editorOptions(true)}
                  />
                </WorkspacePanel>

                <WorkspacePanel
                  title="Mock Model Response"
                >
                  <Editor
                    height="230px"
                    theme="vs-dark"
                    language="json"
                    value={result?.mockModelResponse ?? ""}
                    options={editorOptions(true)}
                  />
                </WorkspacePanel>
              </div>
            </PanelFrame>

            <PanelFrame
              title="Privacy Trace Flow"
              action={
                <button type="button" className="ghost-button" onClick={resetOutcomes}>
                  CLEAR
                </button>
              }
            >
              <div className="trace-rail">
                <FlowNode
                  icon={Globe}
                  title="Raw Input"
                  subtitle="Trusted application boundary"
                  active={Boolean(result)}
                />
                <FlowNode
                  icon={Activity}
                  title={result?.tokenizeNodeId ?? "Node A"}
                  subtitle="Tokenize"
                  active={Boolean(result?.tokenizeNodeId)}
                />
                <FlowNode
                  icon={DatabaseZap}
                  title="Redis Vault"
                  subtitle={metrics?.sharedVaultReady ? "Shared restore path ready" : "Warming up"}
                  active={Boolean(metrics?.sharedVaultReady)}
                />
                <FlowNode
                  icon={Shield}
                  title="Masked Payload"
                  subtitle={config.integration}
                  active={Boolean(result?.sanitizedOutbound)}
                />
                <FlowNode
                  icon={Bot}
                  title={result?.restoreNodeId ?? "Node B"}
                  subtitle="Restore"
                  active={Boolean(result?.restoreNodeId)}
                />
              </div>
            </PanelFrame>
          </section>

          <aside className="metrics-column">
            <PanelFrame title="Runtime Metrics">
              <div className="metric-tile-grid">
                <MetricTile
                  icon={Shield}
                  value={metrics?.tokenizedCount ?? 0}
                  label="Protected Fields"
                  tone="cyan"
                />
                <MetricTile icon={ServerCog} value={metrics?.totalActiveRules ?? 0} label="Active Rules" tone="cyan" />
                <MetricTile
                  icon={DatabaseZap}
                  value={metrics?.sharedVaultReady ? "READY" : "WARMING"}
                  label="Shared Vault"
                  tone="green"
                />
                <MetricTile
                  icon={AlertTriangle}
                  value={metrics?.blockedRequestCount ?? 0}
                  label="Request Blocks"
                  tone="red"
                />
                <MetricTile
                  icon={ShieldAlert}
                  value={metrics?.blockedResponseCount ?? 0}
                  label="Response Blocks"
                  tone="red"
                />
              </div>
            </PanelFrame>

            <PanelFrame title="Metrics History">
              <div className="chart-wrap">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={pollingHistory}>
                    <CartesianGrid strokeDasharray="4 4" stroke="#2d3748" />
                    <XAxis dataKey="time" stroke="#7b8798" tick={{ fill: "#94a3b8", fontSize: 11 }} />
                    <YAxis stroke="#7b8798" tick={{ fill: "#94a3b8", fontSize: 11 }} />
                    <Tooltip
                      contentStyle={{
                        backgroundColor: "#111827",
                        borderColor: "#334155",
                        borderRadius: "12px",
                        color: "#e5edf7",
                      }}
                    />
                    <Area type="monotone" dataKey="totalActiveRules" stroke="#22d3ee" fill="#22d3ee22" strokeWidth={2.5} />
                    <Area type="monotone" dataKey="blockedRequests" stroke="#ef4444" fill="#ef444422" strokeWidth={2} />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            </PanelFrame>

            <PanelFrame title="Trace Timeline">
              <div className="timeline-list">
                {(result?.traceEvents ?? []).length ? (
                  result.traceEvents.map((event, index) => (
                    <div key={`${event.timestamp}-${index}`} className="timeline-event">
                      <div className="timeline-copy">
                        <div className="timeline-title">{event.title}</div>
                        <div className="timeline-detail">{event.detail}</div>
                      </div>
                      <div className="timeline-meta">
                        <span>{event.nodeId}</span>
                        <span>{event.phase}</span>
                      </div>
                    </div>
                  ))
                ) : (
                  <div className="timeline-empty">
                    Run a trace to inspect node-level events, selected rulepacks, masking, and restore flow.
                  </div>
                )}
              </div>
            </PanelFrame>
          </aside>
        </div>
      </main>
    </div>
  );
}

function SectionHeading({ title }) {
  return <div className="section-heading">{title}</div>;
}

function StatusModule({ title, status, detail, ok }) {
  return (
    <div className="status-module">
      <div>
        <div className="status-title">{title}</div>
        <div className={`status-value ${ok ? "text-emerald-300" : "text-rose-300"}`}>{status}</div>
        <div className="status-detail">{detail}</div>
      </div>
      <div className={`status-dot ${ok ? "status-dot-ok" : "status-dot-bad"}`} />
    </div>
  );
}

function PanelFrame({ title, action, children }) {
  return (
    <section className="command-panel">
      <div className="panel-header">
        <h3>{title}</h3>
        {action}
      </div>
      {children}
    </section>
  );
}

function PanelActions({ loading, outageSimulated, onRun, onToggleOutage }) {
  return (
    <div className="workspace-actions">
      <button type="button" className="mini-action mini-action-cyan" onClick={onRun} disabled={loading}>
        <Play className="h-3.5 w-3.5" />
        {loading ? "RUNNING" : "RUN TRACE"}
      </button>
      <label className={`outage-toggle ${outageSimulated ? "outage-toggle-active" : ""}`}>
        <input type="checkbox" checked={outageSimulated} onChange={onToggleOutage} disabled={loading} />
        <span className="outage-toggle-switch">
          <span className="outage-toggle-thumb" />
        </span>
        <span className="outage-toggle-copy">
          <span className="outage-toggle-title">Simulate Redis outage</span>
          <span className="outage-toggle-detail">
            {outageSimulated ? "Outage armed. Next run executes under degraded vault conditions." : "Disabled. Run trace uses the healthy shared vault."}
          </span>
        </span>
      </label>
    </div>
  );
}

function WorkspacePanel({ title, actions, children }) {
  return (
    <div className="workspace-panel">
      <div className="workspace-panel-header">
        <h4>{title}</h4>
        {actions}
      </div>
      <div className="workspace-panel-body">{children}</div>
    </div>
  );
}

function MetricTile({ icon: Icon, value, label, tone }) {
  return (
    <div className="metric-tile">
      <div className={`metric-icon metric-icon-${tone}`}>
        <Icon className="h-4 w-4" />
      </div>
      <div className={`metric-value metric-value-${tone}`}>{value}</div>
      <div className="metric-label">{label}</div>
    </div>
  );
}

function FlowNode({ icon: Icon, title, subtitle, active }) {
  return (
    <motion.div
      className={`flow-node ${active ? "flow-node-active" : ""}`}
      animate={{
        boxShadow: active ? "0 0 0 1px rgba(34,211,238,0.35), 0 0 16px rgba(34,211,238,0.16)" : "none",
      }}
    >
      <div className={`flow-icon-wrap ${active ? "flow-icon-active" : ""}`}>
        <Icon className="h-4 w-4" />
      </div>
      <div className="flow-copy">
        <div className="flow-title">{title}</div>
        <div className="flow-subtitle">{subtitle}</div>
      </div>
    </motion.div>
  );
}

function Shortcut({ icon: Icon, label, href }) {
  return (
    <a href={href} target="_blank" rel="noreferrer" className="shortcut-link">
      <span className="shortcut-copy">
        <Icon className="h-4 w-4 text-cyan-300" />
        {label}
      </span>
      <ArrowUpRight className="h-4 w-4 text-slate-500" />
    </a>
  );
}

function ErrorBanner({ message }) {
  return <div className="error-banner">{message}</div>;
}

function MiniTrend() {
  return (
    <svg viewBox="0 0 48 18" className="h-4 w-12 text-cyan-300" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M1 13 C 8 13, 10 3, 17 3 S 27 15, 34 12 S 40 2, 47 2" strokeLinecap="round" />
    </svg>
  );
}

function computePrivacyScore(metrics, nodes) {
  if (!metrics || !nodes?.length) {
    return 98;
  }
  let score = 98;
  if (!metrics.sharedVaultReady) {
    score -= 12;
  }
  if (!nodes.every((node) => node.reachable)) {
    score -= 18;
  }
  score -= Math.min(metrics.blockedRequestCount ?? 0, 12);
  score -= Math.min(metrics.blockedResponseCount ?? 0, 8);
  return Math.max(62, score);
}

function editorOptions(readOnly) {
  return {
    readOnly,
    minimap: { enabled: false },
    fontSize: 12,
    wordWrap: "on",
    scrollBeyondLastLine: false,
    lineNumbers: "off",
    glyphMargin: false,
    folding: false,
    renderLineHighlight: "none",
    padding: { top: 14, bottom: 14 },
  };
}

export default App;
