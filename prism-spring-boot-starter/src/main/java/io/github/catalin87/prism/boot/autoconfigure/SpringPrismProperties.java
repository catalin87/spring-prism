/*
 * Copyright (c) 2026 Catalin Dordea and Spring Prism Contributors
 *
 * Licensed under the EUPL, Version 1.2 or later (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */
package io.github.catalin87.prism.boot.autoconfigure;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Externalized configuration for the Spring Prism starter. */
@ConfigurationProperties(prefix = "spring.prism")
public class SpringPrismProperties {

  private static final String DEFAULT_SECRET = "spring-prism-change-me";
  private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);
  private static final List<String> DEFAULT_LOCALES = List.of("UNIVERSAL");
  private static final int DEFAULT_AUDIT_RETENTION = 12;
  private static final int DEFAULT_HISTORY_RETENTION = 120;
  private static final int DEFAULT_POLLING_SECONDS = 30;

  private boolean enabled = true;
  private boolean securityStrictMode;
  private Duration ttl = DEFAULT_TTL;
  private String appSecret = DEFAULT_SECRET;
  private List<String> locales = new ArrayList<>(DEFAULT_LOCALES);
  private List<CustomRule> customRules = new ArrayList<>();
  private Set<String> disabledRules = new LinkedHashSet<>();
  private Vault vault = new Vault();
  private Dashboard dashboard = new Dashboard();
  private Mcp mcp = new Mcp();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isSecurityStrictMode() {
    return securityStrictMode;
  }

  public void setSecurityStrictMode(boolean securityStrictMode) {
    this.securityStrictMode = securityStrictMode;
  }

  public Duration getTtl() {
    return ttl;
  }

  /** Sets the vault TTL, falling back to the starter default for null or non-positive values. */
  public void setTtl(Duration ttl) {
    if (ttl == null || ttl.isZero() || ttl.isNegative()) {
      this.ttl = DEFAULT_TTL;
      return;
    }
    this.ttl = ttl;
  }

  public String getAppSecret() {
    return appSecret;
  }

  /** Sets the HMAC application secret, falling back to the starter default when blank. */
  public void setAppSecret(String appSecret) {
    if (appSecret == null || appSecret.isBlank()) {
      this.appSecret = DEFAULT_SECRET;
      return;
    }
    this.appSecret = appSecret;
  }

  public List<String> getLocales() {
    return locales;
  }

  /** Sets the active locales, defaulting back to {@code UNIVERSAL} when the list is empty. */
  public void setLocales(List<String> locales) {
    if (locales == null || locales.isEmpty()) {
      this.locales = new ArrayList<>(DEFAULT_LOCALES);
      return;
    }
    this.locales = new ArrayList<>(locales);
  }

  public List<CustomRule> getCustomRules() {
    return customRules;
  }

  public void setCustomRules(List<CustomRule> customRules) {
    this.customRules = customRules == null ? new ArrayList<>() : new ArrayList<>(customRules);
  }

  public Set<String> getDisabledRules() {
    return disabledRules;
  }

  public void setDisabledRules(Set<String> disabledRules) {
    this.disabledRules =
        disabledRules == null ? new LinkedHashSet<>() : new LinkedHashSet<>(disabledRules);
  }

  public Vault getVault() {
    return vault;
  }

  public void setVault(Vault vault) {
    this.vault = vault == null ? new Vault() : vault;
  }

  public Dashboard getDashboard() {
    return dashboard;
  }

  public void setDashboard(Dashboard dashboard) {
    this.dashboard = dashboard == null ? new Dashboard() : dashboard;
  }

  public Mcp getMcp() {
    return mcp;
  }

  public void setMcp(Mcp mcp) {
    this.mcp = mcp == null ? new Mcp() : mcp;
  }

  /** Externalized vault selection settings. */
  public static class Vault {
    private VaultType type = VaultType.AUTO;

    public VaultType getType() {
      return type;
    }

    public void setType(VaultType type) {
      this.type = type == null ? VaultType.AUTO : type;
    }
  }

  /** Supported vault deployment strategies. */
  public enum VaultType {
    AUTO,
    IN_MEMORY,
    REDIS
  }

  /** Externalized dashboard-specific configuration. */
  public static class Dashboard {
    private int auditRetention = DEFAULT_AUDIT_RETENTION;
    private int historyRetention = DEFAULT_HISTORY_RETENTION;
    private int defaultPollingSeconds = DEFAULT_POLLING_SECONDS;
    private AlertThresholds alertThresholds = new AlertThresholds();

    public int getAuditRetention() {
      return auditRetention;
    }

    public void setAuditRetention(int auditRetention) {
      this.auditRetention = auditRetention <= 0 ? DEFAULT_AUDIT_RETENTION : auditRetention;
    }

    public int getHistoryRetention() {
      return historyRetention;
    }

    public void setHistoryRetention(int historyRetention) {
      this.historyRetention = historyRetention <= 0 ? DEFAULT_HISTORY_RETENTION : historyRetention;
    }

    public int getDefaultPollingSeconds() {
      return defaultPollingSeconds;
    }

    public void setDefaultPollingSeconds(int defaultPollingSeconds) {
      this.defaultPollingSeconds =
          defaultPollingSeconds < 0 ? DEFAULT_POLLING_SECONDS : defaultPollingSeconds;
    }

    public AlertThresholds getAlertThresholds() {
      return alertThresholds;
    }

    public void setAlertThresholds(AlertThresholds alertThresholds) {
      this.alertThresholds = alertThresholds == null ? new AlertThresholds() : alertThresholds;
    }
  }

  /** Externalized MCP client configuration. */
  public static class Mcp {
    private boolean enabled;
    private Boolean securityStrictMode;
    private String transport = "";
    private Http http = new Http();
    private Stdio stdio = new Stdio();

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public Boolean getSecurityStrictMode() {
      return securityStrictMode;
    }

    public void setSecurityStrictMode(Boolean securityStrictMode) {
      this.securityStrictMode = securityStrictMode;
    }

    public boolean resolveSecurityStrictMode(boolean fallback) {
      return securityStrictMode != null ? securityStrictMode.booleanValue() : fallback;
    }

    public String getTransport() {
      return transport;
    }

    public void setTransport(String transport) {
      this.transport = transport == null ? "" : transport.trim();
    }

    public Http getHttp() {
      return http;
    }

    public void setHttp(Http http) {
      this.http = http == null ? new Http() : http;
    }

    public Stdio getStdio() {
      return stdio;
    }

    public void setStdio(Stdio stdio) {
      this.stdio = stdio == null ? new Stdio() : stdio;
    }
  }

  /** Externalized HTTP transport settings for MCP. */
  public static class Http {
    private String baseUrl = "";

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
    }
  }

  /** Externalized stdio transport settings for MCP. */
  public static class Stdio {
    private String command = "";
    private List<String> args = new ArrayList<>();
    private String workingDirectory = "";
    private java.util.Map<String, String> env = new java.util.LinkedHashMap<>();

    public String getCommand() {
      return command;
    }

    public void setCommand(String command) {
      this.command = command == null ? "" : command.trim();
    }

    public List<String> getArgs() {
      return args;
    }

    public void setArgs(List<String> args) {
      this.args = args == null ? new ArrayList<>() : new ArrayList<>(args);
    }

    public String getWorkingDirectory() {
      return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
      this.workingDirectory = workingDirectory == null ? "" : workingDirectory.trim();
    }

    public java.util.Map<String, String> getEnv() {
      return env;
    }

    public void setEnv(java.util.Map<String, String> env) {
      this.env = env == null ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(env);
    }
  }

  /** Externalized dashboard alert thresholds. */
  public static class AlertThresholds {
    private double scanLatencyWarnMs = 25d;
    private double scanLatencyCriticalMs = 75d;
    private long tokenBacklogWarn = 5L;
    private long tokenBacklogCritical = 20L;
    private long detectionErrorWarn = 1L;
    private long detectionErrorCritical = 5L;

    public double getScanLatencyWarnMs() {
      return scanLatencyWarnMs;
    }

    public void setScanLatencyWarnMs(double scanLatencyWarnMs) {
      this.scanLatencyWarnMs = scanLatencyWarnMs <= 0 ? 25d : scanLatencyWarnMs;
    }

    public double getScanLatencyCriticalMs() {
      return scanLatencyCriticalMs;
    }

    public void setScanLatencyCriticalMs(double scanLatencyCriticalMs) {
      this.scanLatencyCriticalMs =
          scanLatencyCriticalMs <= 0 ? 75d : Math.max(scanLatencyCriticalMs, scanLatencyWarnMs);
    }

    public long getTokenBacklogWarn() {
      return tokenBacklogWarn;
    }

    public void setTokenBacklogWarn(long tokenBacklogWarn) {
      this.tokenBacklogWarn = tokenBacklogWarn < 0 ? 5L : tokenBacklogWarn;
    }

    public long getTokenBacklogCritical() {
      return tokenBacklogCritical;
    }

    public void setTokenBacklogCritical(long tokenBacklogCritical) {
      this.tokenBacklogCritical =
          tokenBacklogCritical < 0 ? 20L : Math.max(tokenBacklogCritical, tokenBacklogWarn);
    }

    public long getDetectionErrorWarn() {
      return detectionErrorWarn;
    }

    public void setDetectionErrorWarn(long detectionErrorWarn) {
      this.detectionErrorWarn = detectionErrorWarn < 0 ? 1L : detectionErrorWarn;
    }

    public long getDetectionErrorCritical() {
      return detectionErrorCritical;
    }

    public void setDetectionErrorCritical(long detectionErrorCritical) {
      this.detectionErrorCritical =
          detectionErrorCritical < 0 ? 5L : Math.max(detectionErrorCritical, detectionErrorWarn);
    }
  }

  /** Placeholder binding model for future property-defined detectors. */
  public static class CustomRule {
    private String name = "";
    private String pattern = "";

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getPattern() {
      return pattern;
    }

    public void setPattern(String pattern) {
      this.pattern = pattern;
    }
  }
}
