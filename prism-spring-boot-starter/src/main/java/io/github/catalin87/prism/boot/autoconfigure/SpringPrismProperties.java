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

  private boolean enabled = true;
  private boolean securityStrictMode;
  private Duration ttl = DEFAULT_TTL;
  private String appSecret = DEFAULT_SECRET;
  private List<String> locales = new ArrayList<>(DEFAULT_LOCALES);
  private List<CustomRule> customRules = new ArrayList<>();
  private Set<String> disabledRules = new LinkedHashSet<>();

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
