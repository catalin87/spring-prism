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

  private boolean securityStrictMode;
  private Duration ttl = Duration.ofMinutes(30);
  private String appSecret = DEFAULT_SECRET;
  private List<String> locales = new ArrayList<>(List.of("UNIVERSAL"));
  private List<CustomRule> customRules = new ArrayList<>();
  private Set<String> disabledRules = new LinkedHashSet<>();

  public boolean isSecurityStrictMode() {
    return securityStrictMode;
  }

  public void setSecurityStrictMode(boolean securityStrictMode) {
    this.securityStrictMode = securityStrictMode;
  }

  public Duration getTtl() {
    return ttl;
  }

  public void setTtl(Duration ttl) {
    this.ttl = ttl;
  }

  public String getAppSecret() {
    return appSecret;
  }

  public void setAppSecret(String appSecret) {
    this.appSecret = appSecret;
  }

  public List<String> getLocales() {
    return locales;
  }

  public void setLocales(List<String> locales) {
    this.locales = locales;
  }

  public List<CustomRule> getCustomRules() {
    return customRules;
  }

  public void setCustomRules(List<CustomRule> customRules) {
    this.customRules = customRules;
  }

  public Set<String> getDisabledRules() {
    return disabledRules;
  }

  public void setDisabledRules(Set<String> disabledRules) {
    this.disabledRules = disabledRules;
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
