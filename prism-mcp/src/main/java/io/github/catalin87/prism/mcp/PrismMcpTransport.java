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

import java.util.Locale;
import org.jspecify.annotations.NonNull;

/** Supported MCP client transports in the current Prism foundation milestone. */
public enum PrismMcpTransport {
  STDIO("stdio"),
  STREAMABLE_HTTP("streamable-http");

  private final String propertyValue;

  PrismMcpTransport(String propertyValue) {
    this.propertyValue = propertyValue;
  }

  /** Returns the property-friendly transport identifier. */
  public @NonNull String propertyValue() {
    return propertyValue;
  }

  /** Parses starter or builder property text into a supported transport. */
  public static @NonNull PrismMcpTransport fromProperty(@NonNull String rawValue) {
    String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "stdio" -> STDIO;
      case "streamable-http", "http", "streamable_http" -> STREAMABLE_HTTP;
      default -> throw new IllegalArgumentException("Unsupported MCP transport: " + rawValue);
    };
  }
}
