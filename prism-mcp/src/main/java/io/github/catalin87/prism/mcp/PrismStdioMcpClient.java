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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** MCP client implementation for local subprocess servers over the standard stdio transport. */
public final class PrismStdioMcpClient extends AbstractPrismMcpClient {

  private final List<String> commandLine;
  private final Map<String, String> env;
  private final Path workingDirectory;

  /**
   * Creates a Prism-protected stdio client for local MCP subprocess servers.
   *
   * @param command subprocess executable or launcher
   * @param args subprocess arguments
   * @param env subprocess environment additions
   * @param workingDirectory subprocess working directory, or {@code null} for the current one
   * @param rulePacks active Prism rule packs
   * @param vault Prism vault used for tokenization and restoration
   * @param observationRegistry Micrometer registry reserved for future observations
   * @param metricsSink runtime metrics callback
   * @param strictMode whether detector failures should abort instead of failing open
   */
  public PrismStdioMcpClient(
      @NonNull String command,
      @NonNull List<String> args,
      @NonNull Map<String, String> env,
      Path workingDirectory,
      @NonNull List<PrismRulePack> rulePacks,
      @NonNull PrismVault vault,
      @NonNull ObservationRegistry observationRegistry,
      @NonNull PrismMcpMetricsSink metricsSink,
      boolean strictMode) {
    super(
        PrismMcpMetricsSink.MCP_STDIO_INTEGRATION,
        rulePacks,
        vault,
        observationRegistry,
        metricsSink,
        strictMode);
    this.commandLine = new ArrayList<>();
    this.commandLine.add(command);
    this.commandLine.addAll(args);
    this.env = Map.copyOf(env);
    this.workingDirectory = workingDirectory;
  }

  @Override
  protected @NonNull String execute(@NonNull String requestJson, @Nullable String requestId)
      throws IOException {
    ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
    processBuilder.redirectErrorStream(false);
    processBuilder.environment().putAll(env);
    if (workingDirectory != null) {
      processBuilder.directory(workingDirectory.toFile());
    }

    Process process = processBuilder.start();
    Thread.ofVirtual().start(() -> drainQuietly(process.getErrorStream()));

    try (Writer writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
        BufferedReader reader =
            new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      writer.write(requestJson);
      writer.write(System.lineSeparator());
      writer.flush();
      process.getOutputStream().close();

      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.isBlank()) {
          waitForQuietly(process);
          return line;
        }
      }
      waitForQuietly(process);
      throw new IllegalStateException("No MCP response received over stdio");
    }
  }

  private static void waitForQuietly(Process process) {
    try {
      process.waitFor();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted while waiting for MCP stdio response", exception);
    }
  }

  private static void drainQuietly(InputStream stream) {
    try (InputStream ignored = stream) {
      ignored.transferTo(OutputStream.nullOutputStream());
    } catch (IOException ignored) {
      // no-op
    }
  }
}
