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
package io.github.catalin87.prism.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;

/**
 * Detects both IPv4 (dotted-decimal) and IPv6 (colon-hex) address sequences. IPv4 octet values are
 * range-validated (0-255) to eliminate false positives like version strings.
 */
public class IpAddressDetector implements PiiDetector {

  // IPv4: strict octet range 0-255
  private static final Pattern IPV4_PATTERN =
      Pattern.compile(
          "\\b(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)"
              + "\\.(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)"
              + "\\.(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)"
              + "\\.(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)\\b");

  // IPv6: full and compressed forms (does not validate compressed :: uniqueness — acceptable
  // tradeoff)
  private static final Pattern IPV6_PATTERN =
      Pattern.compile(
          "(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}"
              + "|(?:[0-9a-fA-F]{1,4}:){1,7}:"
              + "|(?:[0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}"
              + "|::(?:[0-9a-fA-F]{1,4}:){0,5}[0-9a-fA-F]{1,4}"
              + "|::1"
              + "|::",
          Pattern.CASE_INSENSITIVE);

  @Override
  public @NonNull String getEntityType() {
    return "IP_ADDRESS";
  }

  @Override
  public @NonNull List<PiiCandidate> detect(@NonNull String input) {
    if (input.isEmpty()) {
      return List.of();
    }

    List<PiiCandidate> matches = new ArrayList<>();
    collectMatches(IPV4_PATTERN.matcher(input), matches);
    collectMatches(IPV6_PATTERN.matcher(input), matches);

    return matches;
  }

  private void collectMatches(Matcher matcher, List<PiiCandidate> matches) {
    while (matcher.find()) {
      matches.add(
          new PiiCandidate(matcher.group(), matcher.start(), matcher.end(), getEntityType()));
    }
  }
}
