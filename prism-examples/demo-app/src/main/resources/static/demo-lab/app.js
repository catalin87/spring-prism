const { useEffect, useMemo, useState } = React;

function App() {
  const [options, setOptions] = useState({
    integrations: ["spring-ai", "langchain4j", "mcp"],
    rulePacks: [],
    dashboardUrl: "/prism/index.html",
    metricsUrl: "/actuator/prism",
  });
  const [integration, setIntegration] = useState("spring-ai");
  const [message, setMessage] = useState(
    "Please email john.doe@example.com and validate RO49AAAA1B31007593840000 before tomorrow."
  );
  const [rulePacks, setRulePacks] = useState([]);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    fetch("/demo-lab/api/options", { method: "POST" })
      .then((response) => response.json())
      .then((data) => {
        setOptions(data);
        setRulePacks(data.rulePacks || []);
      })
      .catch(() => setError("Unable to load demo options."));
  }, []);

  const activeLabel = useMemo(() => {
    if (integration === "spring-ai") return "Spring AI";
    if (integration === "langchain4j") return "LangChain4j";
    return "MCP";
  }, [integration]);

  async function runDemo() {
    setLoading(true);
    setError("");
    try {
      const response = await fetch("/demo-lab/api/run", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ integration, message, rulePacks }),
      });
      const data = await response.json();
      if (!response.ok) {
        throw new Error(data.message || "Demo request failed.");
      }
      setResult(data);
    } catch (exception) {
      setError(exception.message || "Demo request failed.");
    } finally {
      setLoading(false);
    }
  }

  function toggleRulePack(rulePack) {
    setRulePacks((current) =>
      current.includes(rulePack)
        ? current.filter((item) => item !== rulePack)
        : [...current, rulePack]
    );
  }

  return (
    <div className="lab-shell">
      <section className="hero">
        <div className="card hero-main">
          <div className="eyebrow">Spring Prism Demo Lab</div>
          <h1>See the exact payload that leaves your app and the response that comes back restored.</h1>
          <p>
            Run the same prompt through Spring AI, LangChain4j, or MCP with a mock model that echoes
            sanitized content. This makes the sanitize to model to restore path visible for demos and
            manual testing.
          </p>
          <div className="pill-row">
            <span className="pill">React frontend</span>
            <span className="pill">Mock AI responses</span>
            <span className="pill">Dashboard-linked</span>
            <span className="pill">Ruleset selectable</span>
          </div>
        </div>
        <aside className="card hero-side">
          <div className="mini-metric">
            <span className="eyebrow">Current Integration</span>
            <strong>{activeLabel}</strong>
            <span className="helper">Switch tabs below to compare the three supported integration paths.</span>
          </div>
          <div className="mini-metric">
            <span className="eyebrow">Live Companion</span>
            <strong>Dashboard Ready</strong>
            <span className="helper">Open the embedded dashboard in parallel to watch alerts, history, and Privacy Score update as you test prompts.</span>
          </div>
        </aside>
      </section>

      <section className="card workspace">
        <div className="tab-row">
          {options.integrations.map((item) => (
            <button
              key={item}
              type="button"
              className={`tab ${integration === item ? "active" : ""}`}
              onClick={() => setIntegration(item)}
            >
              {item === "spring-ai" ? "Spring AI" : item === "langchain4j" ? "LangChain4j" : "MCP"}
            </button>
          ))}
        </div>

        <p className="section-copy">
          Send text containing emails, phone numbers, IBANs, VAT IDs, or personal identifiers. The
          mock model returns the sanitized payload with a prefix and suffix so you can verify that
          restore happens only inside the trusted application boundary.
        </p>

        <div className="composer">
          <div>
            <div className="status-row">
              <span className="badge">Prompt composer</span>
              <span className="muted">Original message stays visible only inside the app.</span>
            </div>
            <textarea value={message} onChange={(event) => setMessage(event.target.value)} />
          </div>

          <div className="side-controls">
            <div>
              <div className="status-row">
                <span className="badge">Rule packs</span>
                <span className="muted">Toggle the exact scanners you want active for this run.</span>
              </div>
              <div className="rules-row" style={{ marginTop: "12px" }}>
                {options.rulePacks.map((rulePack) => (
                  <button
                    key={rulePack}
                    type="button"
                    className="rule-chip"
                    onClick={() => toggleRulePack(rulePack)}
                  >
                    <input
                      type="checkbox"
                      checked={rulePacks.includes(rulePack)}
                      readOnly
                      style={{ marginRight: "8px" }}
                    />
                    {rulePack}
                  </button>
                ))}
              </div>
            </div>

            <div className="action-row">
              <button type="button" className="action primary" onClick={runDemo} disabled={loading}>
                {loading ? "Running..." : "Run Demo Flow"}
              </button>
              <a className="action" href={options.dashboardUrl} target="_blank" rel="noreferrer">
                Open Dashboard
              </a>
              <a className="action" href={options.metricsUrl} target="_blank" rel="noreferrer">
                Open Metrics JSON
              </a>
            </div>

            {error ? <div className="result-value">{error}</div> : null}
          </div>
        </div>

        {result ? (
          <>
            <div className="results-grid">
              <article className="card result-card">
                <h3>Original prompt</h3>
                <div className="result-value">{result.originalPrompt}</div>
              </article>
              <article className="card result-card">
                <h3>Mock model raw response</h3>
                <div className="result-value">{result.mockModelResponse}</div>
              </article>
              <article className="card result-card">
                <h3>Restored response for the app</h3>
                <div className="result-value">{result.restoredResponse}</div>
              </article>
            </div>

            <div className="trace-grid">
              <article className="card trace-card">
                <h3>Sanitized outbound payload</h3>
                <pre>{result.sanitizedOutbound}</pre>
              </article>
              <article className="card trace-card">
                <h3>Run details</h3>
                <pre>
                  {JSON.stringify(
                    {
                      integration: result.integration,
                      activeRulePacks: result.activeRulePacks,
                      dashboardUrl: result.dashboardUrl,
                      metricsUrl: result.metricsUrl,
                    },
                    null,
                    2
                  )}
                </pre>
              </article>
            </div>
          </>
        ) : null}

        <div className="footer-links">
          <a href="/prism/index.html" target="_blank" rel="noreferrer" className="pill">Dashboard</a>
          <a href="/actuator/prism" target="_blank" rel="noreferrer" className="pill">Actuator Snapshot</a>
          <a href="/docs/mcp" target="_blank" rel="noreferrer" className="pill">MCP Docs</a>
        </div>
      </section>
    </div>
  );
}

ReactDOM.createRoot(document.getElementById("root")).render(<App />);
