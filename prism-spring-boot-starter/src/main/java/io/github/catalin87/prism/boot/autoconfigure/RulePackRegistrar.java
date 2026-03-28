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

import io.github.catalin87.prism.core.PiiDetector;
import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.ruleset.EuropeRulePack;
import io.github.catalin87.prism.core.ruleset.UniversalRulePack;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;

/** Resolves active rule packs from starter properties. */
final class RulePackRegistrar {

  private static final Set<String> EUROPE_LOCALES =
      Set.of(
          "EU",
          "EUROPE",
          "DE",
          "DEU",
          "GERMANY",
          "PL",
          "POL",
          "POLAND",
          "RO",
          "ROU",
          "ROMANIA",
          "UK",
          "GB",
          "GBR",
          "UNITED_KINGDOM",
          "FR",
          "FRA",
          "FRANCE",
          "NL",
          "NLD",
          "NETHERLANDS");
  private static final Set<String> UNIVERSAL_LOCALES = Set.of("UNIVERSAL", "GLOBAL", "EN", "US");
  private static final Set<String> BASELINE_PACK_NAMES = Set.of("EUROPE", "UNIVERSAL");

  List<PrismRulePack> resolve(
      SpringPrismProperties properties, List<PrismRulePack> additionalRulePacks) {
    Set<String> locales =
        properties.getLocales().stream()
            .map(locale -> locale == null ? "" : locale.trim().toUpperCase(Locale.ROOT))
            .filter(locale -> !locale.isEmpty())
            .collect(Collectors.toCollection(LinkedHashSet::new));

    if (locales.isEmpty()) {
      locales.add("UNIVERSAL");
    }

    List<PrismRulePack> autoDiscoverableRulePacks =
        additionalRulePacks == null
            ? List.of()
            : additionalRulePacks.stream().filter(PrismRulePack::isAutoDiscoverable).toList();

    List<PrismRulePack> packs = new java.util.ArrayList<>();
    java.util.Set<String> disabledRules = properties.getDisabledRules();

    // Resolve primary packs for each requested baseline family
    if (locales.stream().anyMatch(EUROPE_LOCALES::contains)) {
      java.util.Optional<PrismRulePack> matching =
          findMatchingPack(autoDiscoverableRulePacks, EUROPE_LOCALES);
      packs.add(filtered(matching.orElseGet(() -> new EuropeRulePack()), disabledRules));
    }
    if (locales.stream().anyMatch(UNIVERSAL_LOCALES::contains)) {
      java.util.Optional<PrismRulePack> matching =
          findMatchingPack(autoDiscoverableRulePacks, UNIVERSAL_LOCALES);
      // Only add Universal fallback if no other primary pack (e.g. Europe) was picked that already
      // covers the baseline
      // or if an exact match for UNIVERSAL was found in the application context.
      if (matching.isPresent() || packs.isEmpty()) {
        packs.add(filtered(matching.orElseGet(() -> new UniversalRulePack()), disabledRules));
      }
    }

    CustomPropertyRulePack customPropertyRulePack = customPack(properties);
    if (!customPropertyRulePack.getDetectors().isEmpty()) {
      packs.add(filtered(customPropertyRulePack, disabledRules));
    }

    // Append any other auto-discoverable packs that match requested locales but are not baseline
    // replacements
    autoDiscoverableRulePacks.stream()
        .filter(rulePack -> !isBaselineFamilyPack(rulePack))
        .filter(rulePack -> matchesAnyAlias(rulePack, locales))
        .map(rulePack -> filtered(rulePack, disabledRules))
        .forEach(packs::add);

    Map<String, PrismRulePack> deduplicated = new LinkedHashMap<>();
    for (PrismRulePack pack : packs) {
      deduplicated.putIfAbsent(normalizedName(pack), pack);
    }
    return List.copyOf(deduplicated.values());
  }

  private boolean anyPackMatches(List<PrismRulePack> packs, Set<String> aliases) {
    return packs.stream().anyMatch(p -> matchesAnyAlias(p, aliases));
  }

  private boolean isBaselineFamilyPack(PrismRulePack rulePack) {
    String name = normalizedName(rulePack);
    return BASELINE_PACK_NAMES.contains(name)
        || name.contains("EUROPE")
        || name.contains("UNIVERSAL");
  }

  private java.util.Optional<PrismRulePack> findMatchingPack(
      List<PrismRulePack> candidates, Set<String> requestedAliases) {
    return candidates.stream()
        .filter(rulePack -> matchesAnyAlias(rulePack, requestedAliases))
        .sorted(java.util.Comparator.comparing(this::isBaselineFamilyPack))
        .findFirst();
  }

  private boolean matchesAnyAlias(PrismRulePack rulePack, Set<String> requestedAliases) {
    Set<String> normalizedAliases =
        rulePack.getActivationAliases().stream()
            .map(alias -> alias == null ? "" : alias.trim().toUpperCase(Locale.ROOT))
            .filter(alias -> !alias.isEmpty())
            .collect(Collectors.toSet());
    return requestedAliases.stream().anyMatch(normalizedAliases::contains);
  }

  private String normalizedName(PrismRulePack rulePack) {
    return rulePack.getName().trim().toUpperCase(Locale.ROOT);
  }

  private CustomPropertyRulePack customPack(SpringPrismProperties properties) {
    List<PiiDetector> detectors =
        properties.getCustomRules().stream()
            .filter(rule -> rule.getName() != null && !rule.getName().isBlank())
            .filter(rule -> rule.getPattern() != null && !rule.getPattern().isBlank())
            .<PiiDetector>map(rule -> new CustomRegexDetector(rule.getName(), rule.getPattern()))
            .toList();
    return new CustomPropertyRulePack(detectors);
  }

  private PrismRulePack filtered(PrismRulePack delegate, Set<String> disabledRules) {
    if (disabledRules == null || disabledRules.isEmpty()) {
      return delegate;
    }

    Set<String> normalizedDisabled =
        disabledRules.stream()
            .map(rule -> rule == null ? "" : rule.trim().toUpperCase(Locale.ROOT))
            .filter(rule -> !rule.isEmpty())
            .collect(Collectors.toUnmodifiableSet());

    List<PiiDetector> detectors =
        delegate.getDetectors().stream()
            .filter(
                detector ->
                    !normalizedDisabled.contains(detector.getEntityType().toUpperCase(Locale.ROOT)))
            .toList();

    return new FilteredRulePack(
        delegate.getName(),
        detectors,
        Set.copyOf(delegate.getActivationAliases()),
        delegate.isAutoDiscoverable());
  }

  private record FilteredRulePack(
      @NonNull String name,
      @NonNull List<PiiDetector> detectors,
      @NonNull Set<String> activationAliases,
      boolean autoDiscoverable)
      implements PrismRulePack {

    @Override
    public @NonNull String getName() {
      return name;
    }

    @Override
    public @NonNull List<PiiDetector> getDetectors() {
      return detectors;
    }

    @Override
    public @NonNull Set<String> getActivationAliases() {
      return activationAliases;
    }

    @Override
    public boolean isAutoDiscoverable() {
      return autoDiscoverable;
    }
  }
}
