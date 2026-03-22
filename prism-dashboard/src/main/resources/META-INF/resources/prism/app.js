const endpoints = ["/actuator/prism", "/prism/metrics"];

const statusPanel = document.getElementById("status-panel");
const cardsGrid = document.getElementById("cards-grid");
const analyticsGrid = document.getElementById("analytics-grid");
const refreshButton = document.getElementById("refresh-button");

async function fetchMetrics() {
  let lastError = null;
  for (const endpoint of endpoints) {
    try {
      const response = await fetch(endpoint, {
        headers: {"Accept": "application/json"}
      });
      if (!response.ok) {
        lastError = new Error(`Request to ${endpoint} returned ${response.status}`);
        continue;
      }
      const payload = await response.json();
      return {endpoint, payload};
    } catch (error) {
      lastError = error;
    }
  }
  throw lastError ?? new Error("Unable to fetch Spring Prism metrics");
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

function renderAuditLog(auditEvents) {
  const auditLog = document.getElementById("audit-log");
  auditLog.replaceChildren();

  if (!auditEvents || auditEvents.length === 0) {
    const empty = document.createElement("li");
    empty.className = "audit-empty";
    empty.textContent = "No masked Prism activity has been recorded yet.";
    auditLog.append(empty);
    return;
  }

  auditEvents.forEach(event => {
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
  renderAuditLog(metrics.auditEvents ?? []);

  statusPanel.innerHTML =
      `<strong>Connected.</strong> Reading live Prism metrics from <code>${endpoint}</code>.`;
  statusPanel.classList.remove("error-panel");
  cardsGrid.classList.remove("hidden");
  analyticsGrid.classList.remove("hidden");
}

function renderError(error) {
  statusPanel.innerHTML = `
    <strong>Metrics unavailable.</strong>
    <p>${error.message}</p>
    <p>Expose either the Actuator Prism endpoint or the fallback Prism metrics route.</p>
  `;
  statusPanel.classList.add("error-panel");
  cardsGrid.classList.add("hidden");
  analyticsGrid.classList.add("hidden");
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
refresh();
