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
package io.github.catalin87.prism.core.detector.universal;

import io.github.catalin87.prism.core.PiiCandidate;
import io.github.catalin87.prism.core.PiiDetector;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;

/**
 * Detects common international and North American phone-number formats using a boundary-safe regex
 * followed by normalization checks to reduce false positives.
 */
public class PhoneNumberDetector implements PiiDetector {

  private static final Pattern PHONE_CANDIDATE_PATTERN =
      Pattern.compile("(?<!\\w)(?:\\+?(?:\\d|\\(\\d{2,4}\\))[\\d() .-]{8,}\\d)(?!\\w)");

  @Override
  public @NonNull String getEntityType() {
    return "PHONE_NUMBER";
  }

  @Override
  public @NonNull List<PiiCandidate> detect(@NonNull String input) {
    if (input.isEmpty()) {
      return List.of();
    }

    List<PiiCandidate> matches = new ArrayList<>();
    Matcher matcher = PHONE_CANDIDATE_PATTERN.matcher(input);
    while (matcher.find()) {
      String candidate = matcher.group();
      if (isValidPhoneCandidate(candidate)) {
        matches.add(new PiiCandidate(candidate, matcher.start(), matcher.end(), getEntityType()));
      }
    }
    return matches;
  }

  private static boolean isValidPhoneCandidate(String candidate) {
    String digits = candidate.replaceAll("\\D", "");
    if (digits.length() < 10 || digits.length() > 15) {
      return false;
    }

    if (!candidate.startsWith("+") && !containsFormatting(candidate) && digits.length() != 10) {
      return false;
    }

    if (looksLikeIpv4Address(candidate)) {
      return false;
    }

    return !allDigitsIdentical(digits);
  }

  private static boolean containsFormatting(String candidate) {
    return candidate.indexOf(' ') >= 0
        || candidate.indexOf('-') >= 0
        || candidate.indexOf('(') >= 0
        || candidate.indexOf(')') >= 0
        || candidate.indexOf('.') >= 0;
  }

  private static boolean looksLikeIpv4Address(String candidate) {
    String[] parts = candidate.split("\\.");
    if (parts.length != 4) {
      return false;
    }
    for (String part : parts) {
      if (!part.matches("\\d{1,3}")) {
        return false;
      }
    }
    return true;
  }

  private static boolean allDigitsIdentical(String digits) {
    char first = digits.charAt(0);
    for (int i = 1; i < digits.length(); i++) {
      if (digits.charAt(i) != first) {
        return false;
      }
    }
    return true;
  }
}
