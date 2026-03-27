const liveEndpoints = ["/actuator/prism", "/prism/metrics"];
const demoEndpoint = "./demo-metrics.json";
const searchParams = new URLSearchParams(window.location.search);
const fallbackPollingSeconds = 30;
const maxRenderableHistoryEntries = 120;

const statusPanel = document.getElementById("status-panel");
const cardsGrid = document.getElementById("cards-grid");
const analyticsGrid = document.getElementById("analytics-grid");
const historyPanel = document.getElementById("history-panel");
const refreshButton = document.getElementById("refresh-button");
const demoButton = document.getElementById("demo-button");
const exportButton = document.getElementById("export-button");
const exportCsvButton = document.getElementById("export-csv-button");
const exportSummaryButton = document.getElementById("export-summary-button");
const pollingToggleButton = document.getElementById("polling-toggle");
const pollingIntervalSelect = document.getElementById("polling-interval");
const historyLimitSelect = document.getElementById("history-limit");
const historyWindowSelect = document.getElementById("history-window");
const pollingStatus = document.getElementById("polling-status");
const filterStatus = document.getElementById("filter-status");
const modePill = document.getElementById("mode-pill");
const integrationFilter = document.getElementById("integration-filter");
const rulePackFilter = document.getElementById("rule-pack-filter");
const entityFilter = document.getElementById("entity-filter");
const auditActionFilter = document.getElementById("audit-action-filter");
const auditSourceFilter = document.getElementById("audit-source-filter");
const auditLimitFilter = document.getElementById("audit-limit-filter");
const auditRetentionNote = document.getElementById("audit-retention-note");
const historyRetentionNote = document.getElementById("history-retention-note");
const privacyScoreValue = document.getElementById("privacy-score-value");
const privacyScoreRing = document.getElementById("privacy-score-ring");
const privacyScoreWindow = document.getElementById("privacy-score-window");
const privacyScoreExplanation = document.getElementById("privacy-score-explanation");
const privacyCoverageScore = document.getElementById("privacy-coverage-score");
const privacyCoverageBar = document.getElementById("privacy-coverage-bar");
const privacyCoverageDetail = document.getElementById("privacy-coverage-detail");
const privacyReliabilityScore = document.getElementById("privacy-reliability-score");
const privacyReliabilityBar = document.getElementById("privacy-reliability-bar");
const privacyReliabilityDetail = document.getElementById("privacy-reliability-detail");
const privacyPostureScore = document.getElementById("privacy-posture-score");
const privacyPostureBar = document.getElementById("privacy-posture-bar");
const privacyPostureDetail = document.getElementById("privacy-posture-detail");

let currentMetrics = null;
let currentEndpoint = null;
let demoMode = searchParams.get("demo") === "1";
let pollingSeconds = Number(searchParams.get("poll") ?? "-1");
let pollingTimer = null;
let serverPollingInitialized = pollingSeconds >= 0;

async function fetchJson(endpoint) {
  const response = await fetch(endpoint, {
    headers: {"Accept": "application/json"}
  });
  if (!response.ok) {
    throw new Error(`Request to ${endpoint} returned ${response.status}`);
  }
  return response.json();
}

async function fetchMetrics() {
  if (demoMode) {
    return {endpoint: demoEndpoint, payload: await fetchJson(demoEndpoint)};
  }

  let lastError = null;
  for (const endpoint of liveEndpoints) {
    try {
      return {endpoint, payload: await fetchJson(endpoint)};
    } catch (error) {
      lastError = error;
    }
  }
  throw lastError ?? new Error("Unable to fetch Spring Prism metrics");
}

function updateUrlState() {
  const nextUrl = new URL(window.location.href);
  if (demoMode) {
    nextUrl.searchParams.set("demo", "1");
  } else {
    nextUrl.searchParams.delete("demo");
  }

  if (pollingSeconds > 0 && pollingSeconds !== fallbackPollingSeconds) {
    nextUrl.searchParams.set("poll", `${pollingSeconds}`);
  } else {
    nextUrl.searchParams.delete("poll");
  }
  window.history.replaceState({}, "", nextUrl);
}

function updateModeUi() {
  modePill.textContent = demoMode ? "Demo mode" : "Live mode";
  demoButton.textContent = demoMode ? "Return to Live Data" : "Open Demo Data";
}

function updatePollingUi() {
  const modeLabel = pollingSeconds > 0 ? `Every ${pollingSeconds}s` : "Paused";
  document.getElementById("polling-mode").textContent = modeLabel;
  pollingToggleButton.textContent = pollingSeconds > 0 ? "Pause Polling" : "Resume Polling";
  pollingStatus.textContent =
      pollingSeconds > 0
          ? `Auto-refresh is enabled every ${pollingSeconds} seconds.`
          : "Auto-refresh is paused. Use Refresh for a manual snapshot.";
}

function setPolling(seconds, persistUrl = true) {
  pollingSeconds = seconds;
  pollingIntervalSelect.value = `${seconds}`;
  if (pollingTimer) {
    window.clearInterval(pollingTimer);
    pollingTimer = null;
  }
  if (seconds > 0) {
    pollingTimer = window.setInterval(() => {
      refresh();
    }, seconds * 1000);
  }
  if (persistUrl) {
    updateUrlState();
  }
  updatePollingUi();
}

function initializePollingFromMetrics(metrics) {
  if (serverPollingInitialized) {
    return;
  }

  const configuredDefault =
      metrics.dashboardConfiguration?.defaultPollingSeconds ?? fallbackPollingSeconds;
  serverPollingInitialized = true;
  setPolling(configuredDefault, false);
}

function formatMilliseconds(durationMetric) {
  if (!durationMetric || !durationMetric.averageNanos) {
    return "0 ms";
  }
  return `${(durationMetric.averageNanos / 1_000_000).toFixed(2)} ms`;
}

function setScoreBar(element, score) {
  if (!element) {
    return;
  }
  element.style.width = `${Math.max(0, Math.min(100, score))}%`;
}

function averageMilliseconds(durationMetric) {
  return durationMetric?.averageNanos ? durationMetric.averageNanos / 1_000_000 : 0;
}

function alertThresholds(metrics) {
  return metrics.dashboardConfiguration?.alertThresholds ?? {
    scanLatencyWarnMs: 25,
    scanLatencyCriticalMs: 75,
    tokenBacklogWarn: 5,
    tokenBacklogCritical: 20,
    detectionErrorWarn: 1,
    detectionErrorCritical: 5
  };
}

function selectedIntegration() {
  return integrationFilter.value || "ALL";
}

function selectedRulePack() {
  return rulePackFilter.value || "ALL";
}

function selectedEntity() {
  return entityFilter.value || "ALL";
}

function selectedHistoryWindow() {
  return historyWindowSelect.value || "ALL";
}

function renderFilterOptions(select, values) {
  const currentValue = select.value;
  select.replaceChildren();

  const allOption = document.createElement("option");
  allOption.value = "ALL";
  allOption.textContent = "All";
  select.append(allOption);

  values.forEach(value => {
    const option = document.createElement("option");
    option.value = value;
    option.textContent = value;
    select.append(option);
  });

  if ([...select.options].some(option => option.value === currentValue)) {
    select.value = currentValue;
  }
}

function updateOperationalFilters(metrics) {
  renderFilterOptions(
      integrationFilter,
      (metrics.integrationMetrics ?? []).map(integration => integration.name));
  renderFilterOptions(
      rulePackFilter,
      (metrics.rulePackMetrics ?? []).map(rulePack => rulePack.name));
  renderFilterOptions(
      entityFilter,
      [...new Set((metrics.entityMetrics ?? []).map(entity => entity.entityType))].sort());

  filterStatus.textContent =
      `${selectedIntegration() === "ALL" ? "All integrations" : selectedIntegration()} · `
      + `${selectedRulePack() === "ALL" ? "all rule packs" : selectedRulePack()} · `
      + `${selectedEntity() === "ALL" ? "all entity types" : selectedEntity()} · `
      + `${selectedHistoryWindow() === "ALL" ? "full retained history" : selectedHistoryWindow()}`;
}

function filteredIntegrationMetrics(integrationMetrics) {
  return integrationMetrics.filter(
      integration => selectedIntegration() === "ALL" || integration.name === selectedIntegration());
}

function filteredRulePackMetrics(rulePackMetrics) {
  return rulePackMetrics.filter(
      metric => selectedRulePack() === "ALL" || metric.name === selectedRulePack());
}

function filteredEntityMetrics(entityMetrics) {
  return entityMetrics.filter(entity =>
    (selectedRulePack() === "ALL" || entity.rulePackName === selectedRulePack())
    && (selectedEntity() === "ALL" || entity.entityType === selectedEntity()));
}

function detectionEntries(metrics) {
  const filteredEntities = filteredEntityMetrics(metrics.entityMetrics ?? []);
  if (filteredEntities.length > 0) {
    return filteredEntities
        .map(entity => [`${entity.rulePackName}:${entity.entityType}`, entity.detections])
        .sort((left, right) => right[1] - left[1]);
  }

  return Object.entries(metrics.detectionCounts ?? {})
      .sort((left, right) => right[1] - left[1]);
}

function topDetections(metrics) {
  return detectionEntries(metrics).slice(0, 4);
}

function sumDetections(metrics) {
  return detectionEntries(metrics).reduce((sum, [, count]) => sum + count, 0);
}

function dominantTimer(durationMetrics) {
  return Object.entries(durationMetrics ?? {})
      .sort((left, right) => right[1].averageNanos - left[1].averageNanos)[0];
}

function renderTopDetections(metrics) {
  const detections = topDetections(metrics);
  const topList = document.getElementById("top-detections");
  const topEntity = document.getElementById("top-entity");
  topList.replaceChildren();

  if (detections.length === 0) {
    topEntity.textContent = "No detections yet";
    return;
  }

  topEntity.textContent = detections[0][0];
  detections.forEach(([name, count]) => {
    const item = document.createElement("li");
    const label = document.createElement("span");
    const value = document.createElement("strong");
    label.textContent = name;
    value.textContent = `${count}`;
    item.append(label, value);
    topList.append(item);
  });
}

function renderPrivacyScore(metrics) {
  const privacyScore = metrics.privacyScore;
  if (!privacyScore) {
    privacyScoreValue.textContent = "0";
    privacyScoreRing.style.setProperty("--score", "0");
    return;
  }

  privacyScoreValue.textContent = `${privacyScore.score ?? 0}`;
  privacyScoreRing.style.setProperty("--score", `${privacyScore.score ?? 0}`);
  privacyScoreWindow.textContent = privacyScore.windowLabel ?? "Last 60 minutes";
  privacyScoreExplanation.textContent = privacyScore.explanation ?? "";

  privacyCoverageScore.textContent = `${privacyScore.coverage?.score ?? 0}%`;
  setScoreBar(privacyCoverageBar, privacyScore.coverage?.score ?? 0);
  privacyCoverageDetail.textContent = privacyScore.coverage?.detail ?? "";

  privacyReliabilityScore.textContent = `${privacyScore.reliability?.score ?? 0}%`;
  setScoreBar(privacyReliabilityBar, privacyScore.reliability?.score ?? 0);
  privacyReliabilityDetail.textContent = privacyScore.reliability?.detail ?? "";

  privacyPostureScore.textContent = `${privacyScore.posture?.score ?? 0}%`;
  setScoreBar(privacyPostureBar, privacyScore.posture?.score ?? 0);
  privacyPostureDetail.textContent = privacyScore.posture?.detail ?? "";
}

function renderRulePackMetrics(rulePackMetrics) {
  const container = document.getElementById("rule-pack-metrics");
  container.replaceChildren();

  const visibleMetrics = filteredRulePackMetrics(rulePackMetrics);
  if (visibleMetrics.length === 0) {
    container.innerHTML = "<p class=\"empty-state\">No rule-pack activity matches the current filters.</p>";
    return;
  }

  const maxDetections = Math.max(1, ...visibleMetrics.map(metric => metric.totalDetections));
  visibleMetrics.forEach(metric => {
    const row = document.createElement("div");
    row.className = "bar-row";

    const label = document.createElement("div");
    label.className = "bar-label";
    label.innerHTML =
        `<strong>${metric.name}</strong><span>${metric.totalDetections} detections · ${metric.detectorCount} detectors</span>`;

    const track = document.createElement("div");
    track.className = "bar-track";
    const fill = document.createElement("div");
    fill.className = "bar-fill";
    fill.style.width = `${Math.max(10, (metric.totalDetections / maxDetections) * 100)}%`;
    track.append(fill);

    row.append(label, track);
    container.append(row);
  });
}

function historySamplesForWindow() {
  const historySamples = currentMetrics?.historySamples ?? [];
  const limit = Number(historyLimitSelect.value || "25");
  const windowKey = selectedHistoryWindow();
  let visible = historySamples;

  if (windowKey !== "ALL") {
    const now = Date.now();
    const durations = {
      "5m": 5 * 60 * 1000,
      "15m": 15 * 60 * 1000,
      "1h": 60 * 60 * 1000
    };
    visible = historySamples.filter(
        sample => now - new Date(sample.capturedAt).getTime() <= durations[windowKey]);
  }

  return visible.slice(-Math.min(limit, maxRenderableHistoryEntries));
}

function renderTrendCards(metrics) {
  const trendCards = document.getElementById("trend-cards");
  trendCards.replaceChildren();

  const leadingRulePack = filteredRulePackMetrics(metrics.rulePackMetrics ?? [])[0];
  const totalDetections = sumDetections(metrics);
  const slowestTimer = dominantTimer(metrics.durationMetrics);
  const selectedRollup =
      (metrics.historyRollups ?? []).find(rollup => rollup.key === selectedHistoryWindow().toLowerCase())
      ?? (metrics.historyRollups ?? [])[0];

  [
    {
      label: "Leading Pack",
      value: leadingRulePack?.name ?? "None",
      caption: leadingRulePack
          ? `${leadingRulePack.totalDetections} detections across ${leadingRulePack.detectorCount} detectors`
          : "Waiting for live detections"
    },
    {
      label: "Visible Detections",
      value: `${totalDetections}`,
      caption: "Combined detections across the current entity and rule-pack filter"
    },
    {
      label: "Slowest Timer",
      value: slowestTimer ? slowestTimer[0] : "No timers",
      caption: slowestTimer ? formatMilliseconds(slowestTimer[1]) : "No duration samples yet"
    },
    {
      label: "Window Rollup",
      value: selectedRollup?.label ?? "Recent",
      caption: selectedRollup
          ? `${selectedRollup.sampleCount} sample(s) · ${selectedRollup.averageScanMilliseconds.toFixed(2)} ms avg scan`
          : "No rollup data yet"
    }
  ].forEach(card => {
    const article = document.createElement("article");
    article.className = "trend-card";
    article.innerHTML = `
      <p>${card.label}</p>
      <strong>${card.value}</strong>
      <span>${card.caption}</span>
    `;
    trendCards.append(article);
  });
}

function renderRollups(metrics) {
  const container = document.getElementById("rollup-cards");
  container.replaceChildren();

  (metrics.historyRollups ?? []).forEach(rollup => {
    const card = document.createElement("article");
    card.className = "rollup-card";
    card.innerHTML = `
      <p>${rollup.label}</p>
      <strong>${rollup.latestDetections}</strong>
      <span>${rollup.sampleCount} sample(s) · ${rollup.averageScanMilliseconds.toFixed(2)} ms avg scan</span>
      <dl class="mini-metrics">
        <div><dt>Errors</dt><dd>${rollup.errorEvents}</dd></div>
        <div><dt>Peak backlog</dt><dd>${rollup.peakTokenBacklog}</dd></div>
      </dl>
    `;
    container.append(card);
  });
}

function renderIntegrationSummary(metrics) {
  const container = document.getElementById("integration-summary");
  container.replaceChildren();

  const integrations = filteredIntegrationMetrics(metrics.integrationMetrics ?? []);
  if (integrations.length === 0) {
    container.innerHTML = "<p class=\"empty-state\">No integration timing data matches the current filter.</p>";
    return;
  }

  integrations.forEach(integration => {
    const title = integrationLabel(integration.name);
    const scanSamples = integration.scan?.samples ?? 0;
    const card = document.createElement("article");
    card.className = "integration-card";
    card.innerHTML = `
      <p>${title}</p>
      <strong>${formatMilliseconds(integration.scan)}</strong>
      <span>Scan avg</span>
      <dl class="mini-metrics">
        <div><dt>Samples</dt><dd>${scanSamples}</dd></div>
        <div><dt>Tokenize</dt><dd>${formatMilliseconds(integration.tokenize)}</dd></div>
        <div><dt>Detokenize</dt><dd>${formatMilliseconds(integration.detokenize)}</dd></div>
      </dl>
    `;
    container.append(card);
  });
}

function alertLevel(label, state, description) {
  return {label, state, description};
}

function renderAlerts(metrics) {
  const container = document.getElementById("alert-cards");
  container.replaceChildren();

  const scanMetric = highestScanMetric(metrics.integrationMetrics ?? []);
  const scanMs = averageMilliseconds(scanMetric);
  const tokenGap = metrics.tokenBacklog ?? Math.max(0, (metrics.tokenizedCount ?? 0) - (metrics.detokenizedCount ?? 0));
  const errorCount = metrics.detectionErrorCount ?? 0;
  const isRedis = (metrics.vaultType ?? "").toLowerCase().includes("redis");
  const thresholds = alertThresholds(metrics);

  const scanState =
      scanMs >= thresholds.scanLatencyCriticalMs
          ? "critical"
          : scanMs >= thresholds.scanLatencyWarnMs ? "warn" : "healthy";
  const backlogState =
      tokenGap >= thresholds.tokenBacklogCritical
          ? "critical"
          : tokenGap >= thresholds.tokenBacklogWarn ? "warn" : "healthy";
  const errorState =
      errorCount >= thresholds.detectionErrorCritical
          ? "critical"
          : errorCount >= thresholds.detectionErrorWarn ? "warn" : "healthy";

  [
    alertLevel(
        "Detection errors",
        errorState,
        `${errorCount} detection error event(s). Warn at ${thresholds.detectionErrorWarn}, critical at ${thresholds.detectionErrorCritical}.`),
    alertLevel(
        "Scan latency",
        scanState,
        `${scanMs.toFixed(2)} ms average scan time. Warn at ${thresholds.scanLatencyWarnMs} ms, critical at ${thresholds.scanLatencyCriticalMs} ms.`),
    alertLevel(
        "Token backlog",
        backlogState,
        `${tokenGap} outstanding restore delta. Warn at ${thresholds.tokenBacklogWarn}, critical at ${thresholds.tokenBacklogCritical}.`),
    alertLevel(
        "Vault mode",
        isRedis ? "info" : "healthy",
        isRedis
            ? "Redis-backed vault active for shared restore paths."
            : "Local in-memory vault active for single-node operation.")
  ].forEach(alert => {
    const card = document.createElement("article");
    card.className = `alert-card alert-${alert.state}`;
    card.innerHTML = `
      <p>${alert.label}</p>
      <strong>${alert.state.toUpperCase()}</strong>
      <span>${alert.description}</span>
    `;
    container.append(card);
  });
}

function highestScanMetric(integrationMetrics) {
  return integrationMetrics
      .map(integration => integration.scan)
      .filter(metric => metric && (metric.samples ?? 0) > 0)
      .sort((left, right) => averageMilliseconds(right) - averageMilliseconds(left))[0];
}

function integrationLabel(name) {
  switch (name) {
    case "spring-ai":
      return "Spring AI";
    case "langchain4j":
      return "LangChain4j";
    case "mcp-stdio":
      return "MCP Stdio";
    case "mcp-streamable-http":
      return "MCP Streamable HTTP";
    default:
      return name;
  }
}

function renderEntityDrilldowns(metrics) {
  const container = document.getElementById("entity-drilldowns");
  container.replaceChildren();

  const entityMetrics = filteredEntityMetrics(metrics.entityMetrics ?? []);
  if (entityMetrics.length === 0) {
    const empty = document.createElement("article");
    empty.className = "entity-card";
    empty.innerHTML = `
      <p>No detector activity yet</p>
      <strong>Waiting for redactions</strong>
      <span>Entity drill-downs will appear once Prism sees live detections.</span>
    `;
    container.append(empty);
    return;
  }

  entityMetrics.slice(0, 8).forEach(metric => {
    const card = document.createElement("article");
    card.className = "entity-card";
    card.innerHTML = `
      <p>${metric.rulePackName}</p>
      <strong>${metric.entityType}</strong>
      <span>${metric.detections} detection(s)</span>
    `;
    container.append(card);
  });
}

function renderVaultInsights(metrics) {
  const container = document.getElementById("vault-insights");
  container.replaceChildren();

  const vaultType = metrics.vaultType || "Unknown";
  const configuredVaultMode = metrics.configuredVaultMode || "AUTO";
  const readinessStatus = metrics.vaultReadinessStatus || "UNKNOWN";
  const readinessDetails = metrics.vaultReadinessDetails || "Vault readiness details unavailable.";
  const isRedis = vaultType.toLowerCase().includes("redis");
  const sharedVaultReady = Boolean(metrics.sharedVaultReady);
  const items = [
    {
      label: "Configured mode",
      value: configuredVaultMode,
      note:
          configuredVaultMode === "REDIS"
              ? "Explicit shared-vault deployment"
              : configuredVaultMode === "IN_MEMORY"
                  ? "Explicit single-node deployment"
                  : "Auto-selects Redis when available"
    },
    {
      label: "Runtime vault",
      value: vaultType,
      note: isRedis ? "Distributed restore path" : "Local restore path"
    },
    {
      label: "Topology",
      value: metrics.distributedVault ? "Shared" : "Single node",
      note: metrics.distributedVault
          ? "Best fit for horizontally scaled restore flows"
          : "Optimized for local in-memory protection"
    },
    {
      label: "Shared vault ready",
      value: metrics.distributedVault ? (sharedVaultReady ? "Yes" : "No") : "N/A",
      note: metrics.distributedVault
          ? sharedVaultReady
              ? "Shared Redis plus custom app secret are in place"
              : "Shared vault is active but secret hygiene still needs attention"
          : "Only relevant for distributed restore paths"
    },
    {
      label: "Readiness",
      value: readinessStatus,
      note: readinessDetails
    },
    {
      label: "Retention",
      value: `${metrics.auditRetentionLimit ?? 0} audit / ${metrics.historyRetentionLimit ?? 0} history`,
      note: "Bounded server-side dashboard memory"
    },
    {
      label: "Token balance",
      value: `${metrics.tokenBacklog ?? 0}`,
      note: "Outstanding restore delta"
    }
  ];

  items.forEach(item => {
    const block = document.createElement("article");
    block.className = "vault-card";
    block.innerHTML = `
      <p>${item.label}</p>
      <strong>${item.value}</strong>
      <span>${item.note}</span>
    `;
    container.append(block);
  });
}

function renderDashboardConfiguration(metrics) {
  const container = document.getElementById("dashboard-config");
  container.replaceChildren();

  const config = metrics.dashboardConfiguration ?? {};
  const thresholds = config.alertThresholds ?? {};
  [
    {
      label: "Retention",
      value: `${config.auditRetentionLimit ?? 0} / ${config.historyRetentionLimit ?? 0}`,
      note: "Audit and history sample limits"
    },
    {
      label: "Default polling",
      value: `${config.defaultPollingSeconds ?? fallbackPollingSeconds}s`,
      note: "Starter-provided dashboard refresh cadence"
    },
    {
      label: "Scan thresholds",
      value: `${thresholds.scanLatencyWarnMs ?? 25} / ${thresholds.scanLatencyCriticalMs ?? 75} ms`,
      note: "Warn and critical scan latency"
    },
    {
      label: "Backlog thresholds",
      value: `${thresholds.tokenBacklogWarn ?? 5} / ${thresholds.tokenBacklogCritical ?? 20}`,
      note: "Warn and critical token backlog"
    }
  ].forEach(item => {
    const card = document.createElement("article");
    card.className = "config-card";
    card.innerHTML = `
      <p>${item.label}</p>
      <strong>${item.value}</strong>
      <span>${item.note}</span>
    `;
    container.append(card);
  });
}

function updateAuditFilters(auditEvents, retentionLimit) {
  const actions = [...new Set((auditEvents ?? []).map(event => event.action.toUpperCase()))].sort();
  const sources = [...new Set((auditEvents ?? []).map(event => event.source))].sort();

  renderFilterOptions(auditActionFilter, actions);
  renderFilterOptions(auditSourceFilter, sources);

  const limit = retentionLimit ?? Math.max((auditEvents ?? []).length, 1);
  const desiredLimit = Math.min(Number(auditLimitFilter.value || "5"), limit);
  auditLimitFilter.value = `${desiredLimit}`;
  [...auditLimitFilter.options].forEach(option => {
    option.disabled = Number(option.value) > limit;
  });

  auditRetentionNote.textContent =
      `Showing masked history only. Prism retains the most recent ${limit} in-memory event(s).`;
}

function filteredAuditEvents(auditEvents, retentionLimit) {
  const limit = Math.min(Number(auditLimitFilter.value || "5"), retentionLimit ?? auditEvents.length);
  return (auditEvents ?? [])
      .filter(event =>
        auditActionFilter.value === "ALL"
            || event.action.toUpperCase() === auditActionFilter.value)
      .filter(event => auditSourceFilter.value === "ALL" || event.source === auditSourceFilter.value)
      .slice(0, limit);
}

function renderAuditLog(auditEvents, retentionLimit) {
  const auditLog = document.getElementById("audit-log");
  auditLog.replaceChildren();

  const visibleEvents = filteredAuditEvents(auditEvents, retentionLimit);
  if (visibleEvents.length === 0) {
    const empty = document.createElement("li");
    empty.className = "audit-empty";
    empty.textContent = "No masked Prism activity matches the current audit filters.";
    auditLog.append(empty);
    return;
  }

  visibleEvents.forEach(event => {
    const item = document.createElement("li");
    item.className = "audit-item";
    item.innerHTML = `
      <div>
        <strong>${event.action.toUpperCase()} ${event.subject}</strong>
        <p>${event.count} event(s) from ${event.source}</p>
      </div>
      <time>${new Date(event.timestamp).toLocaleTimeString()}</time>
    `;
    auditLog.append(item);
  });
}

function healthSignal(metrics) {
  if ((metrics.distributedVault ?? false) && !(metrics.sharedVaultReady ?? false)) {
    return "Attention";
  }
  if ((metrics.vaultReadinessStatus ?? "") === "LOCAL_ONLY_ATTENTION") {
    return "Attention";
  }
  const thresholds = alertThresholds(metrics);
  if ((metrics.detectionErrorCount ?? 0) >= thresholds.detectionErrorCritical) {
    return "Critical";
  }
  if ((metrics.detectionErrorCount ?? 0) > 0) {
    return "Attention";
  }
  const scanMetric = highestScanMetric(metrics.integrationMetrics ?? []);
  if (averageMilliseconds(scanMetric) >= thresholds.scanLatencyCriticalMs) {
    return "Critical";
  }
  if (averageMilliseconds(scanMetric) >= thresholds.scanLatencyWarnMs) {
    return "Warm";
  }
  return "Healthy";
}

function renderSparkline(svgId, values, stroke) {
  const svg = document.getElementById(svgId);
  svg.replaceChildren();

  if (!values.length) {
    return;
  }

  const width = 320;
  const height = 120;
  const padding = 10;
  const maxValue = Math.max(1, ...values);

  const points = values.map((value, index) => {
    const x = padding + ((width - padding * 2) * index) / Math.max(1, values.length - 1);
    const y = height - padding - ((height - padding * 2) * value) / maxValue;
    return `${x},${y}`;
  }).join(" ");

  const polyline = document.createElementNS("http://www.w3.org/2000/svg", "polyline");
  polyline.setAttribute("points", points);
  polyline.setAttribute("fill", "none");
  polyline.setAttribute("stroke", stroke);
  polyline.setAttribute("stroke-width", "4");
  polyline.setAttribute("stroke-linecap", "round");
  polyline.setAttribute("stroke-linejoin", "round");
  svg.append(polyline);
}

function renderHistory() {
  const points = historySamplesForWindow();
  if (!points.length) {
    historyPanel.classList.add("hidden");
    return;
  }

  historyRetentionNote.textContent =
      `Server-side history retains the latest ${currentMetrics?.historyRetentionLimit ?? points.length} sample(s); showing ${points.length} in the selected window.`;
  renderSparkline("history-detections", points.map(point => point.totalDetections), "#0f766e");
  renderSparkline("history-errors", points.map(point => point.detectionErrors), "#b42318");
  renderSparkline("history-scan", points.map(point => point.scanMilliseconds), "#1d4ed8");
  renderSparkline("history-backlog", points.map(point => point.tokenBacklog ?? 0), "#7c3aed");
  historyPanel.classList.remove("hidden");
}

function triggerDownload(name, type, content) {
  const blob = new Blob([content], {type});
  const link = document.createElement("a");
  link.href = URL.createObjectURL(blob);
  link.download = name;
  link.click();
  URL.revokeObjectURL(link.href);
}

function exportSnapshot() {
  if (!currentMetrics) {
    return;
  }

  triggerDownload(
      `spring-prism-dashboard-${Date.now()}.json`,
      "application/json",
      JSON.stringify(
          {
            endpoint: currentEndpoint,
            exportedAt: new Date().toISOString(),
            metrics: currentMetrics,
            history: historySamplesForWindow()
          },
          null,
          2));
}

function exportHistoryCsv() {
  if (!currentMetrics) {
    return;
  }

  const rows = ["captured_at,total_detections,detection_errors,scan_milliseconds,token_backlog,vault_type"];
  historySamplesForWindow().forEach(sample => {
    rows.push(
        [
          sample.capturedAt,
          sample.totalDetections,
          sample.detectionErrors,
          sample.scanMilliseconds,
          sample.tokenBacklog,
          sample.vaultType
        ].join(","));
  });
  triggerDownload(`spring-prism-history-${Date.now()}.csv`, "text/csv", rows.join("\n"));
}

function exportIncidentSummary() {
  if (!currentMetrics) {
    return;
  }

  const topEntities = topDetections(currentMetrics)
      .map(([name, count]) => `- ${name}: ${count}`)
      .join("\n") || "- none";
  const rollups = (currentMetrics.historyRollups ?? [])
      .map(rollup =>
        `- ${rollup.label}: ${rollup.sampleCount} samples, ${rollup.latestDetections} detections, ${rollup.averageScanMilliseconds.toFixed(2)} ms avg scan`)
      .join("\n");
  const summary = [
    "Spring Prism Incident Summary",
    `Generated: ${new Date().toISOString()}`,
    `Endpoint: ${currentEndpoint}`,
    `Configured vault mode: ${currentMetrics.configuredVaultMode ?? "AUTO"}`,
    `Vault: ${currentMetrics.vaultType}`,
    `Shared vault ready: ${currentMetrics.sharedVaultReady ? "yes" : "no"}`,
    `Vault readiness: ${currentMetrics.vaultReadinessStatus ?? "UNKNOWN"}`,
    `Health: ${healthSignal(currentMetrics)}`,
    `Token backlog: ${currentMetrics.tokenBacklog ?? 0}`,
    "",
    "Top entities",
    topEntities,
    "",
    "Rollups",
    rollups
  ].join("\n");

  triggerDownload(`spring-prism-incident-${Date.now()}.txt`, "text/plain", summary);
}

function renderMetrics(endpoint, metrics) {
  currentMetrics = metrics;
  currentEndpoint = endpoint;
  initializePollingFromMetrics(metrics);

  const scanMetric = highestScanMetric(metrics.integrationMetrics ?? []);

  document.getElementById("vault-type").textContent = metrics.vaultType || "-";
  document.getElementById("tokenized-count").textContent = `${metrics.tokenizedCount ?? 0}`;
  document.getElementById("detokenized-count").textContent = `${metrics.detokenizedCount ?? 0}`;
  document.getElementById("error-count").textContent = `${metrics.detectionErrorCount ?? 0}`;
  document.getElementById("scan-latency").textContent = formatMilliseconds(scanMetric);
  document.getElementById("health-signal").textContent = healthSignal(metrics);
  document.getElementById("rule-packs").textContent =
      (metrics.activeRulePacks ?? []).join(", ") || "-";
  document.getElementById("timer-count").textContent =
      `${Object.keys(metrics.durationMetrics ?? {}).length}`;
  document.getElementById("endpoint-used").textContent = endpoint;

  renderPrivacyScore(metrics);
  updateOperationalFilters(metrics);
  renderTopDetections(metrics);
  renderRulePackMetrics(metrics.rulePackMetrics ?? []);
  renderTrendCards(metrics);
  renderRollups(metrics);
  renderAlerts(metrics);
  renderIntegrationSummary(metrics);
  renderEntityDrilldowns(metrics);
  renderVaultInsights(metrics);
  renderDashboardConfiguration(metrics);
  updateAuditFilters(metrics.auditEvents ?? [], metrics.auditRetentionLimit ?? 0);
  renderAuditLog(metrics.auditEvents ?? [], metrics.auditRetentionLimit ?? 0);
  renderHistory();

  statusPanel.innerHTML =
      `<strong>Connected.</strong> Reading Prism metrics from <code>${endpoint}</code>.`;
  statusPanel.classList.remove("error-panel");
  cardsGrid.classList.remove("hidden");
  analyticsGrid.classList.remove("hidden");
  updateModeUi();
  updatePollingUi();
}

function renderError(error) {
  currentMetrics = null;
  currentEndpoint = null;
  statusPanel.innerHTML = `
    <strong>Metrics unavailable.</strong>
    <p>${error.message}</p>
    <p>Expose either the Actuator Prism endpoint, the fallback Prism metrics route, or use demo mode.</p>
  `;
  statusPanel.classList.add("error-panel");
  cardsGrid.classList.add("hidden");
  analyticsGrid.classList.add("hidden");
  historyPanel.classList.add("hidden");
  updateModeUi();
  updatePollingUi();
}

function rerenderCurrentMetrics() {
  if (!currentMetrics) {
    return;
  }
  renderMetrics(currentEndpoint, currentMetrics);
}

async function refresh() {
  statusPanel.innerHTML = "<strong>Refreshing dashboard metrics...</strong>";
  try {
    const {endpoint, payload} = await fetchMetrics();
    renderMetrics(endpoint, payload);
  } catch (error) {
    renderError(error);
  }
}

refreshButton.addEventListener("click", refresh);
demoButton.addEventListener("click", async () => {
  demoMode = !demoMode;
  updateUrlState();
  await refresh();
});
exportButton.addEventListener("click", exportSnapshot);
exportCsvButton.addEventListener("click", exportHistoryCsv);
exportSummaryButton.addEventListener("click", exportIncidentSummary);
pollingToggleButton.addEventListener("click", () => {
  if (pollingSeconds > 0) {
    setPolling(0);
    return;
  }
  const nextSeconds =
      Number(pollingIntervalSelect.value || `${fallbackPollingSeconds}`) || fallbackPollingSeconds;
  setPolling(nextSeconds);
});
pollingIntervalSelect.addEventListener("change", () => {
  setPolling(Number(pollingIntervalSelect.value || "0"));
});
[
  historyLimitSelect,
  historyWindowSelect,
  integrationFilter,
  rulePackFilter,
  entityFilter,
  auditActionFilter,
  auditSourceFilter,
  auditLimitFilter
].forEach(element => {
  element.addEventListener("change", rerenderCurrentMetrics);
});

updateModeUi();
setPolling(serverPollingInitialized ? pollingSeconds : fallbackPollingSeconds, false);
refresh();
