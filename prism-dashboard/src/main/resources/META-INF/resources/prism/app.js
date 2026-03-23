const liveEndpoints = ["/actuator/prism", "/prism/metrics"];
const demoEndpoint = "./demo-metrics.json";
const searchParams = new URLSearchParams(window.location.search);
const defaultPollingSeconds = 30;
const maxHistoryEntries = 50;

const statusPanel = document.getElementById("status-panel");
const cardsGrid = document.getElementById("cards-grid");
const analyticsGrid = document.getElementById("analytics-grid");
const historyPanel = document.getElementById("history-panel");
const refreshButton = document.getElementById("refresh-button");
const demoButton = document.getElementById("demo-button");
const exportButton = document.getElementById("export-button");
const pollingToggleButton = document.getElementById("polling-toggle");
const pollingIntervalSelect = document.getElementById("polling-interval");
const historyLimitSelect = document.getElementById("history-limit");
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

let currentMetrics = null;
let currentEndpoint = null;
let demoMode = searchParams.get("demo") === "1";
let pollingSeconds = Number(searchParams.get("poll")) || defaultPollingSeconds;
let pollingTimer = null;

pollingIntervalSelect.value = `${pollingSeconds}`;

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

  if (pollingSeconds > 0 && pollingSeconds !== defaultPollingSeconds) {
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

function setPolling(seconds) {
  pollingSeconds = seconds;
  if (pollingTimer) {
    window.clearInterval(pollingTimer);
    pollingTimer = null;
  }
  if (seconds > 0) {
    pollingTimer = window.setInterval(() => {
      refresh();
    }, seconds * 1000);
  }
  updateUrlState();
  updatePollingUi();
}

function formatMilliseconds(durationMetric) {
  if (!durationMetric || !durationMetric.averageNanos) {
    return "0 ms";
  }
  return `${(durationMetric.averageNanos / 1_000_000).toFixed(2)} ms`;
}

function averageMilliseconds(durationMetric) {
  return durationMetric?.averageNanos ? durationMetric.averageNanos / 1_000_000 : 0;
}

function topDetections(detectionCounts) {
  return Object.entries(detectionCounts ?? {})
      .sort((left, right) => right[1] - left[1])
      .slice(0, 4);
}

function sumDetections(detectionCounts) {
  return Object.values(detectionCounts ?? {}).reduce((sum, count) => sum + count, 0);
}

function dominantTimer(durationMetrics) {
  return Object.entries(durationMetrics ?? {})
      .sort((left, right) => right[1].averageNanos - left[1].averageNanos)[0];
}

function renderTopDetections(detections) {
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

function renderRulePackMetrics(rulePackMetrics) {
  const container = document.getElementById("rule-pack-metrics");
  container.replaceChildren();

  const visibleMetrics = filteredRulePackMetrics(rulePackMetrics);
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

function renderTrendCards(metrics) {
  const trendCards = document.getElementById("trend-cards");
  trendCards.replaceChildren();

  const leadingRulePack = (metrics.rulePackMetrics ?? [])[0];
  const totalDetections = sumDetections(metrics.detectionCounts);
  const slowestTimer = dominantTimer(metrics.durationMetrics);
  const retentionLabel =
      `${Math.min((metrics.auditEvents ?? []).length, metrics.auditRetentionLimit ?? 0)} / ${metrics.auditRetentionLimit ?? 0}`;

  [
    {
      label: "Leading Pack",
      value: leadingRulePack?.name ?? "None",
      caption: leadingRulePack
          ? `${leadingRulePack.totalDetections} detections across ${leadingRulePack.detectorCount} detectors`
          : "Waiting for live detections"
    },
    {
      label: "Total Detections",
      value: `${totalDetections}`,
      caption: "Combined detections across all active rule packs"
    },
    {
      label: "Slowest Timer",
      value: slowestTimer ? slowestTimer[0] : "No timers",
      caption: slowestTimer ? formatMilliseconds(slowestTimer[1]) : "No duration samples yet"
    },
    {
      label: "Audit Retention",
      value: retentionLabel,
      caption: "Masked events currently kept in the in-memory dashboard history"
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

function renderIntegrationSummary(metrics) {
  const container = document.getElementById("integration-summary");
  container.replaceChildren();

  filteredIntegrationMetrics(metrics.integrationMetrics ?? []).forEach(integration => {
    const title = integration.name === "spring-ai" ? "Spring AI" : "LangChain4j";
    const card = document.createElement("article");
    card.className = "integration-card";
    card.innerHTML = `
      <p>${title}</p>
      <strong>${formatMilliseconds(integration.scan)}</strong>
      <span>Scan avg</span>
      <dl class="mini-metrics">
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

  const scanMetric = metrics.durationMetrics?.["spring-ai:scan"]
      ?? metrics.durationMetrics?.["langchain4j:scan"];
  const scanMs = averageMilliseconds(scanMetric);
  const tokenGap = Math.max(0, (metrics.tokenizedCount ?? 0) - (metrics.detokenizedCount ?? 0));
  const errorCount = metrics.detectionErrorCount ?? 0;
  const isRedis = (metrics.vaultType ?? "").toLowerCase().includes("redis");

  [
    errorCount > 0
        ? alertLevel("Detection errors", "warn", `${errorCount} detection error event(s) observed.`)
        : alertLevel("Detection errors", "healthy", "No detection errors recorded."),
    scanMs > 25
        ? alertLevel("Scan latency", "warn", `${scanMs.toFixed(2)} ms average scan time.`)
        : alertLevel("Scan latency", "healthy", `${scanMs.toFixed(2)} ms average scan time.`),
    tokenGap > 5
        ? alertLevel("Token backlog", "warn", `${tokenGap} more tokenizations than restorations.`)
        : alertLevel("Token backlog", "healthy", "Tokenize/detokenize activity is balanced."),
    isRedis
        ? alertLevel("Vault mode", "info", "Redis-backed vault active for shared restore paths.")
        : alertLevel("Vault mode", "info", "Local in-memory vault active for single-node operation.")
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

  entityMetrics.slice(0, 6).forEach(metric => {
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
  const isRedis = vaultType.toLowerCase().includes("redis");
  const items = [
    {
      label: "Mode",
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
      label: "Retention",
      value: `${metrics.auditRetentionLimit ?? 0} audit / ${metrics.historyRetentionLimit ?? 0} history`,
      note: "Bounded server-side dashboard memory"
    },
    {
      label: "Token balance",
      value: `${metrics.tokenBacklog ?? Math.max(0, (metrics.tokenizedCount ?? 0) - (metrics.detokenizedCount ?? 0))}`,
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

function selectedIntegration() {
  return integrationFilter.value || "ALL";
}

function selectedRulePack() {
  return rulePackFilter.value || "ALL";
}

function selectedEntity() {
  return entityFilter.value || "ALL";
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
      + `${selectedEntity() === "ALL" ? "all entity types" : selectedEntity()}`;
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
  if ((metrics.detectionErrorCount ?? 0) > 0) {
    return "Attention";
  }
  const scanMetric = metrics.durationMetrics?.["spring-ai:scan"]
      ?? metrics.durationMetrics?.["langchain4j:scan"];
  if (averageMilliseconds(scanMetric) > 25) {
    return "Warm";
  }
  return "Healthy";
}

function limitedHistory() {
  const limit = Number(historyLimitSelect.value || "25");
  const serverHistory = currentMetrics?.historySamples ?? [];
  return serverHistory.slice(-Math.min(limit, maxHistoryEntries));
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
    const x =
        padding
        + ((width - padding * 2) * index) / Math.max(1, values.length - 1);
    const y =
        height
        - padding
        - ((height - padding * 2) * value) / maxValue;
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
  const points = limitedHistory();
  if (!points.length) {
    historyPanel.classList.add("hidden");
    return;
  }

  historyRetentionNote.textContent =
      `Server-side history retains the latest ${currentMetrics?.historyRetentionLimit ?? points.length} sample(s).`;
  renderSparkline("history-detections", points.map(point => point.totalDetections), "#0f766e");
  renderSparkline("history-errors", points.map(point => point.detectionErrors), "#b42318");
  renderSparkline("history-scan", points.map(point => point.scanMilliseconds), "#1d4ed8");
  renderSparkline("history-backlog", points.map(point => point.tokenBacklog ?? 0), "#7c3aed");
  historyPanel.classList.remove("hidden");
}

function exportSnapshot() {
  if (!currentMetrics) {
    return;
  }

  const blob = new Blob(
      [
        JSON.stringify(
            {
              endpoint: currentEndpoint,
              exportedAt: new Date().toISOString(),
              metrics: currentMetrics,
              history: limitedHistory()
            },
            null,
            2)
      ],
      {type: "application/json"});
  const link = document.createElement("a");
  link.href = URL.createObjectURL(blob);
  link.download = `spring-prism-dashboard-${Date.now()}.json`;
  link.click();
  URL.revokeObjectURL(link.href);
}

function renderMetrics(endpoint, metrics) {
  currentMetrics = metrics;
  currentEndpoint = endpoint;

  const scanMetric = metrics.durationMetrics?.["spring-ai:scan"]
      ?? metrics.durationMetrics?.["langchain4j:scan"];

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

  updateOperationalFilters(metrics);
  renderTopDetections(topDetections(metrics.detectionCounts));
  renderRulePackMetrics(metrics.rulePackMetrics ?? []);
  renderTrendCards(metrics);
  renderAlerts(metrics);
  renderIntegrationSummary(metrics);
  renderEntityDrilldowns(metrics);
  renderVaultInsights(metrics);
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

function rerenderAuditFromCurrentState() {
  if (!currentMetrics) {
    return;
  }
  renderAuditLog(currentMetrics.auditEvents ?? [], currentMetrics.auditRetentionLimit ?? 0);
}

function rerenderHistoryFromCurrentState() {
  renderHistory();
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
pollingToggleButton.addEventListener("click", () => {
  if (pollingSeconds > 0) {
    pollingIntervalSelect.value = "0";
    setPolling(0);
    return;
  }
  const nextSeconds = Number(pollingIntervalSelect.value || defaultPollingSeconds) || defaultPollingSeconds;
  pollingIntervalSelect.value = `${nextSeconds}`;
  setPolling(nextSeconds);
});
pollingIntervalSelect.addEventListener("change", () => {
  setPolling(Number(pollingIntervalSelect.value || "0"));
});
historyLimitSelect.addEventListener("change", rerenderHistoryFromCurrentState);
integrationFilter.addEventListener("change", () => currentMetrics && renderMetrics(currentEndpoint, currentMetrics));
rulePackFilter.addEventListener("change", () => currentMetrics && renderMetrics(currentEndpoint, currentMetrics));
entityFilter.addEventListener("change", () => currentMetrics && renderMetrics(currentEndpoint, currentMetrics));
[auditActionFilter, auditSourceFilter, auditLimitFilter].forEach(element => {
  element.addEventListener("change", rerenderAuditFromCurrentState);
});

updateModeUi();
setPolling(pollingSeconds);
refresh();
