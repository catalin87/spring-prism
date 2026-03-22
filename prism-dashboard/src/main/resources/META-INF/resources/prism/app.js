const liveEndpoints = ["/actuator/prism", "/prism/metrics"];
const demoEndpoint = "./demo-metrics.json";
const searchParams = new URLSearchParams(window.location.search);

const statusPanel = document.getElementById("status-panel");
const cardsGrid = document.getElementById("cards-grid");
const analyticsGrid = document.getElementById("analytics-grid");
const refreshButton = document.getElementById("refresh-button");
const demoButton = document.getElementById("demo-button");
const modePill = document.getElementById("mode-pill");
const auditActionFilter = document.getElementById("audit-action-filter");
const auditSourceFilter = document.getElementById("audit-source-filter");
const auditLimitFilter = document.getElementById("audit-limit-filter");
const auditRetentionNote = document.getElementById("audit-retention-note");

let currentMetrics = null;
let currentEndpoint = null;
let demoMode = searchParams.get("demo") === "1";

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

function updateModeUi() {
  modePill.textContent = demoMode ? "Demo mode" : "Live mode";
  demoButton.textContent = demoMode ? "Return to Live Data" : "Open Demo Data";
}

function formatMilliseconds(durationMetric) {
  if (!durationMetric || !durationMetric.averageNanos) {
    return "0 ms";
  }
  return `${(durationMetric.averageNanos / 1_000_000).toFixed(2)} ms`;
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

  const maxDetections = Math.max(1, ...rulePackMetrics.map(metric => metric.totalDetections));
  rulePackMetrics.forEach(metric => {
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
  document.getElementById("rule-packs").textContent =
      (metrics.activeRulePacks ?? []).join(", ") || "-";
  document.getElementById("timer-count").textContent =
      `${Object.keys(metrics.durationMetrics ?? {}).length}`;
  document.getElementById("endpoint-used").textContent = endpoint;

  renderTopDetections(topDetections(metrics.detectionCounts));
  renderRulePackMetrics(metrics.rulePackMetrics ?? []);
  renderTrendCards(metrics);
  updateAuditFilters(metrics.auditEvents ?? [], metrics.auditRetentionLimit ?? 0);
  renderAuditLog(metrics.auditEvents ?? [], metrics.auditRetentionLimit ?? 0);

  statusPanel.innerHTML =
      `<strong>Connected.</strong> Reading Prism metrics from <code>${endpoint}</code>.`;
  statusPanel.classList.remove("error-panel");
  cardsGrid.classList.remove("hidden");
  analyticsGrid.classList.remove("hidden");
  updateModeUi();
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
  updateModeUi();
}

function rerenderAuditFromCurrentState() {
  if (!currentMetrics) {
    return;
  }
  renderAuditLog(currentMetrics.auditEvents ?? [], currentMetrics.auditRetentionLimit ?? 0);
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
  const nextUrl = new URL(window.location.href);
  if (demoMode) {
    nextUrl.searchParams.set("demo", "1");
  } else {
    nextUrl.searchParams.delete("demo");
  }
  window.history.replaceState({}, "", nextUrl);
  await refresh();
});
[auditActionFilter, auditSourceFilter, auditLimitFilter].forEach(element => {
  element.addEventListener("change", rerenderAuditFromCurrentState);
});

updateModeUi();
refresh();
