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
package io.github.catalin87.prism.mcp;

import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.PrismVault;
import io.micrometer.observation.ObservationRegistry;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

/** Builder for Prism-protected MCP clients. */
public final class PrismMcpClientBuilder {

  private List<PrismRulePack> rulePacks = List.of();
  private PrismVault vault;
  private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;
  private PrismMcpMetricsSink metricsSink = PrismMcpMetricsSink.NOOP;
  private boolean strictMode;
  private PrismMcpTransport transport;
  private String baseUrl;
  private String command;
  private List<String> args = new ArrayList<>();
  private Map<String, String> env = new LinkedHashMap<>();
  private Path workingDirectory;

  private PrismMcpClientBuilder() {}

  public static @NonNull PrismMcpClientBuilder builder() {
    return new PrismMcpClientBuilder();
  }

  public @NonNull PrismMcpClientBuilder withRulePacks(@NonNull List<PrismRulePack> rulePacks) {
    this.rulePacks = List.copyOf(rulePacks);
    return this;
  }

  public @NonNull PrismMcpClientBuilder withVault(@NonNull PrismVault vault) {
    this.vault = Objects.requireNonNull(vault, "vault");
    return this;
  }

  public @NonNull PrismMcpClientBuilder withObservationRegistry(
      @NonNull ObservationRegistry observationRegistry) {
    this.observationRegistry = Objects.requireNonNull(observationRegistry, "observationRegistry");
    return this;
  }

  public @NonNull PrismMcpClientBuilder withMetricsSink(@NonNull PrismMcpMetricsSink metricsSink) {
    this.metricsSink = Objects.requireNonNull(metricsSink, "metricsSink");
    return this;
  }

  public @NonNull PrismMcpClientBuilder withStrictMode(boolean strictMode) {
    this.strictMode = strictMode;
    return this;
  }

  public @NonNull PrismMcpClientBuilder withTransport(@NonNull PrismMcpTransport transport) {
    this.transport = Objects.requireNonNull(transport, "transport");
    return this;
  }

  public @NonNull PrismMcpClientBuilder withBaseUrl(@NonNull String baseUrl) {
    this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
    return this;
  }

  public @NonNull PrismMcpClientBuilder withCommand(@NonNull String command) {
    this.command = Objects.requireNonNull(command, "command");
    return this;
  }

  public @NonNull PrismMcpClientBuilder withArgs(@NonNull List<String> args) {
    this.args = new ArrayList<>(args);
    return this;
  }

  public @NonNull PrismMcpClientBuilder withEnv(@NonNull Map<String, String> env) {
    this.env = new LinkedHashMap<>(env);
    return this;
  }

  public @NonNull PrismMcpClientBuilder withWorkingDirectory(@NonNull Path workingDirectory) {
    this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory");
    return this;
  }

  /** Builds the configured Prism MCP client transport wrapper. */
  public @NonNull PrismMcpClient build() {
    if (vault == null) {
      throw new IllegalStateException("Prism MCP client requires a PrismVault");
    }
    if (transport == null) {
      throw new IllegalStateException("Prism MCP transport must be configured");
    }
    return switch (transport) {
      case STDIO ->
          new PrismStdioMcpClient(
              required(command, "command"),
              args,
              env,
              workingDirectory,
              rulePacks,
              vault,
              observationRegistry,
              metricsSink,
              strictMode);
      case STREAMABLE_HTTP ->
          new PrismHttpMcpClient(
              required(baseUrl, "baseUrl"),
              rulePacks,
              vault,
              observationRegistry,
              metricsSink,
              strictMode);
    };
  }

  private static String required(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Prism MCP " + name + " must be configured");
    }
    return value;
  }
}
