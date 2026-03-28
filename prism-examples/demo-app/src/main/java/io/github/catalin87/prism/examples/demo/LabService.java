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
package io.github.catalin87.prism.examples.demo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.catalin87.prism.boot.autoconfigure.PrismMetricsSnapshot;
import io.github.catalin87.prism.boot.autoconfigure.PrismRuntimeMetrics;
import io.github.catalin87.prism.core.PrismFailureMode;
import io.github.catalin87.prism.core.PrismProtectionException;
import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.PrismVault;
import io.github.catalin87.prism.extensions.nlp.HeuristicPersonNameBackend;
import io.github.catalin87.prism.extensions.nlp.HybridPersonNameDetector;
import io.github.catalin87.prism.extensions.nlp.NlpExtensionRulePack;
import io.github.catalin87.prism.extensions.nlp.NlpPersonNameProperties;
import io.github.catalin87.prism.extensions.nlp.OpenNlpPersonNameBackend;
import io.github.catalin87.prism.extensions.nlp.PersonNameBackend;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/** Orchestrates the enterprise demo lab flows, node metadata, and cluster metrics. */
@Service
class LabService {

  private static final String MOCK_PREFIX = "Mock Prism Model :: ";
  private static final String MOCK_SUFFIX = " :: enterprise restore trace.";

  private final List<PrismRulePack> installedRulePacks;
  private final List<PrismRulePack> runtimeRulePacks;
  private final SimulationAwarePrismVault prismVault;
  private final PrismRuntimeMetrics prismRuntimeMetrics;
  private final LabRedactionEngine redactionEngine;
  private final LabSimulationState simulationState;
  private final LabProperties properties;
  private final ResourceLoader resourceLoader;
  private final ObjectMapper objectMapper;
  private final RestClient restClient;

  LabService(
      List<PrismRulePack> installedRulePacks,
      @org.springframework.beans.factory.annotation.Qualifier("springPrismRulePacks")
          List<PrismRulePack> runtimeRulePacks,
      @Qualifier("prismVault") PrismVault prismVault,
      PrismRuntimeMetrics prismRuntimeMetrics,
      LabRedactionEngine redactionEngine,
      LabSimulationState simulationState,
      LabProperties properties,
      ResourceLoader resourceLoader,
      ObjectMapper objectMapper) {
    this.installedRulePacks = List.copyOf(installedRulePacks);
    this.runtimeRulePacks = List.copyOf(runtimeRulePacks);
    this.prismVault = new SimulationAwarePrismVault(prismVault, simulationState);
    this.prismRuntimeMetrics = prismRuntimeMetrics;
    this.redactionEngine = redactionEngine;
    this.simulationState = simulationState;
    this.properties = properties;
    this.resourceLoader = resourceLoader;
    this.objectMapper = objectMapper;
    this.restClient = RestClient.builder().build();
  }

  LabBootstrapResponse bootstrap() {
    List<LabNodeStatus> nodes = nodeStatuses();
    return new LabBootstrapResponse(
        "Spring Prism Enterprise Lab",
        properties.getNodeId(),
        List.of("spring-ai", "langchain4j", "mcp"),
        availableRulePackOptions(),
        defaultRulePackIds(),
        List.of(PrismFailureMode.FAIL_SAFE.name(), PrismFailureMode.FAIL_CLOSED.name()),
        PrismFailureMode.FAIL_SAFE.name(),
        availableNlpModes(),
        availableNlpModes().contains(LabNlpMode.HYBRID.name())
            ? LabNlpMode.HYBRID.name()
            : LabNlpMode.HEURISTIC.name(),
        List.of(
            LabRouteMode.AUTO.name(), LabRouteMode.LOCAL.name(), LabRouteMode.CROSS_NODE.name()),
        LabRouteMode.AUTO.name(),
        promptPresets(),
        properties.getPublicBaseUrl() + "/prism/index.html",
        properties.getPublicBaseUrl() + "/actuator/prism",
        properties.getGrafanaUrl(),
        nodes);
  }

  LabRunResponse run(LabRunRequest request) {
    String integration = normalized(request.integration(), "spring-ai");
    String message = request.message() == null ? "" : request.message().trim();
    if (message.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message must not be blank");
    }

    PrismFailureMode failureMode = parseFailureMode(request.failureMode());
    LabNlpMode nlpMode = parseNlpMode(request.nlpMode());
    LabRouteMode routeMode = parseRouteMode(request.routeMode());
    List<PrismRulePack> selectedRulePacks = selectedRulePacks(request.rulePacks(), nlpMode);

    LabRedactionEngine.TokenizeResult tokenizeResult =
        redactionEngine.tokenize(
            message,
            selectedRulePacks,
            prismVault,
            failureMode,
            integration,
            properties.getNodeId());
    String sanitizedOutbound = outboundPayload(integration, tokenizeResult.sanitizedText());
    String mockModelResponse = mockModelResponse(integration, tokenizeResult.sanitizedText());
    List<LabTraceEvent> traceEvents = new ArrayList<>(tokenizeResult.traceEvents());
    traceEvents.add(
        new LabTraceEvent(
            Instant.now().toString(),
            properties.getNodeId(),
            "dispatch",
            "info",
            "Forwarded masked payload",
            "The masked payload left the trusted application boundary as " + integration + "."));

    try {
      LabRestoreResponse restoreResponse =
          shouldUsePeerRestore(routeMode)
              ? restoreOnPeer(mockModelResponse, failureMode, integration)
              : restoreLocally(mockModelResponse, failureMode, integration);
      traceEvents.add(
          new LabTraceEvent(
              Instant.now().toString(),
              restoreResponse.nodeId(),
              "restore-route",
              "success",
              "Restore completed",
              "The response was restored on " + restoreResponse.nodeId() + "."));
      return new LabRunResponse(
          integration,
          message,
          selectedRulePacks.stream().map(PrismRulePack::getName).distinct().toList(),
          failureMode.name(),
          nlpMode.name(),
          routeMode.name(),
          properties.getNodeId(),
          restoreResponse.nodeId(),
          sanitizedOutbound,
          mockModelResponse,
          restoreResponse.restoredText(),
          false,
          "",
          "",
          "",
          traceEvents,
          properties.getPublicBaseUrl() + "/prism/index.html",
          properties.getPublicBaseUrl() + "/actuator/prism",
          properties.getGrafanaUrl());
    } catch (PrismProtectionException exception) {
      traceEvents.add(
          new LabTraceEvent(
              Instant.now().toString(),
              properties.getNodeId(),
              exception.phase().name().toLowerCase(Locale.ROOT),
              "blocked",
              "Request blocked",
              exception.reason().name()));
      return new LabRunResponse(
          integration,
          message,
          selectedRulePacks.stream().map(PrismRulePack::getName).distinct().toList(),
          failureMode.name(),
          nlpMode.name(),
          routeMode.name(),
          properties.getNodeId(),
          shouldUsePeerRestore(routeMode) ? peerNodeId() : properties.getNodeId(),
          sanitizedOutbound,
          mockModelResponse,
          "",
          true,
          exception.phase().name(),
          exception.reason().name(),
          exception.getMessage(),
          traceEvents,
          properties.getPublicBaseUrl() + "/prism/index.html",
          properties.getPublicBaseUrl() + "/actuator/prism",
          properties.getGrafanaUrl());
    }
  }

  LabMetricsResponse metrics() {
    List<LabNodeStatus> nodes = nodeStatuses();
    return new LabMetricsResponse(
        nodes.stream().mapToLong(LabNodeStatus::tokenizedCount).sum(),
        nodes.stream().mapToLong(LabNodeStatus::blockedRequestCount).sum(),
        nodes.stream().mapToLong(LabNodeStatus::blockedResponseCount).sum(),
        nodes.stream().mapToInt(LabNodeStatus::totalActiveRules).sum(),
        nodes.stream().allMatch(LabNodeStatus::sharedVaultReady),
        nodes.stream()
            .flatMap(node -> node.activeRulePacks().stream())
            .distinct()
            .sorted()
            .toList(),
        nodes);
  }

  List<LabNodeStatus> nodeStatuses() {
    List<LabNodeStatus> nodes = new ArrayList<>();
    nodes.add(localNodeStatus());
    if (!properties.getPeerBaseUrl().isBlank()) {
      nodes.add(fetchNodeStatus(peerNodeId(), properties.getPeerBaseUrl(), false));
    }
    return nodes;
  }

  LabNodeStatus localNodeStatus() {
    return fetchNodeStatus(properties.getNodeId(), properties.getSelfBaseUrl(), true);
  }

  void simulateRedisOutage(boolean active) {
    if (active) {
      simulationState.enableRedisOutage();
    } else {
      simulationState.disableRedisOutage();
    }
    if (!properties.getPeerBaseUrl().isBlank()) {
      try {
        restClient
            .post()
            .uri(properties.getPeerBaseUrl() + "/lab/api/internal/simulator/redis-outage")
            .body(Map.of("active", active))
            .retrieve()
            .toBodilessEntity();
      } catch (RuntimeException ignored) {
        // Best-effort propagation so the demo can still run locally even if the peer is down.
      }
    }
  }

  void applyInternalOutage(boolean active) {
    if (active) {
      simulationState.enableRedisOutage();
    } else {
      simulationState.disableRedisOutage();
    }
  }

  LabRestoreResponse restoreInternal(LabRestoreRequest request) {
    return restoreLocally(
        request.rawResponse(),
        parseFailureMode(request.failureMode()),
        normalized(request.integration(), "spring-ai"));
  }

  private LabRestoreResponse restoreLocally(
      String rawResponse, PrismFailureMode failureMode, String integration) {
    LabRedactionEngine.RestoreResult restoreResult =
        redactionEngine.restore(
            rawResponse, prismVault, failureMode, integration, properties.getNodeId());
    return new LabRestoreResponse(
        properties.getNodeId(), restoreResult.restoredText(), restoreResult.restoredCount());
  }

  private LabRestoreResponse restoreOnPeer(
      String rawResponse, PrismFailureMode failureMode, String integration) {
    return restClient
        .post()
        .uri(properties.getPeerBaseUrl() + "/lab/api/internal/restore")
        .body(new LabRestoreRequest(rawResponse, failureMode.name(), integration))
        .retrieve()
        .body(LabRestoreResponse.class);
  }

  private boolean shouldUsePeerRestore(LabRouteMode routeMode) {
    return routeMode == LabRouteMode.CROSS_NODE && !properties.getPeerBaseUrl().isBlank();
  }

  private LabNodeStatus fetchNodeStatus(String nodeId, String baseUrl, boolean self) {
    try {
      PrismMetricsSnapshot snapshot =
          self
              ? restClient
                  .get()
                  .uri(baseUrl + "/actuator/prism")
                  .retrieve()
                  .body(PrismMetricsSnapshot.class)
              : null;
      if (self) {
        if (snapshot == null) {
          throw new IllegalStateException("Missing Prism snapshot");
        }
        return nodeStatusFromSnapshot(baseUrl, snapshot, simulationState.redisOutageActive());
      }
      LabNodeStatus remoteStatus =
          restClient
              .get()
              .uri(baseUrl + "/lab/api/internal/node-status")
              .retrieve()
              .body(LabNodeStatus.class);
      if (remoteStatus == null) {
        throw new IllegalStateException("Missing peer node status");
      }
      return remoteStatus;
    } catch (RuntimeException exception) {
      return new LabNodeStatus(
          nodeId,
          baseUrl,
          false,
          self && simulationState.redisOutageActive(),
          "",
          "",
          false,
          0,
          0,
          0,
          0,
          List.of(),
          exception.getMessage());
    }
  }

  private LabNodeStatus nodeStatusFromSnapshot(
      String baseUrl, PrismMetricsSnapshot snapshot, boolean redisOutageSimulated) {
    return new LabNodeStatus(
        properties.getNodeId(),
        baseUrl,
        true,
        redisOutageSimulated,
        snapshot.failureMode(),
        snapshot.vaultType(),
        snapshot.sharedVaultReady(),
        snapshot.tokenizedCount(),
        snapshot.blockedRequestCount(),
        snapshot.blockedResponseCount(),
        snapshot.totalActiveRules(),
        snapshot.activeRulePacks(),
        "");
  }

  private List<LabRulePackOption> availableRulePackOptions() {
    Map<String, PrismRulePack> packs = installedRulePackMap();
    return List.of(
            option("UNIVERSAL", "Universal", "🌐", "Baseline global detectors.", true, packs),
            option("RO", "Romania", "🇷🇴", "CNP, CIF, IBAN and universal detectors.", true, packs),
            option(
                "US",
                "United States",
                "🇺🇸",
                "SSN, EIN, ABA routing and universal detectors.",
                true,
                packs),
            option(
                "DE", "Germany", "🇩🇪", "Steuer-ID, IBAN and universal detectors.", true, packs),
            option(
                "GB", "United Kingdom", "🇬🇧", "NINO, NHS and universal detectors.", true, packs),
            option(
                "FR", "France", "🇫🇷", "NIR, SIREN, SIRET and universal detectors.", true, packs),
            option("NL", "Netherlands", "🇳🇱", "BSN, IBAN and universal detectors.", true, packs),
            option(
                "PL", "Poland", "🇵🇱", "PESEL, NIP, IBAN and universal detectors.", true, packs))
        .stream()
        .filter(option -> option != null)
        .sorted(Comparator.comparing(LabRulePackOption::id))
        .toList();
  }

  private LabRulePackOption option(
      String id,
      String label,
      String flag,
      String description,
      boolean defaultSelected,
      Map<String, PrismRulePack> packs) {
    return packs.containsKey(id)
        ? new LabRulePackOption(id, label, flag, description, defaultSelected)
        : null;
  }

  private List<String> defaultRulePackIds() {
    return availableRulePackOptions().stream()
        .filter(LabRulePackOption::defaultSelected)
        .map(LabRulePackOption::id)
        .toList();
  }

  private List<String> availableNlpModes() {
    List<String> modes = new ArrayList<>();
    modes.add(LabNlpMode.OFF.name());
    modes.add(LabNlpMode.HEURISTIC.name());
    if (hybridModelAvailable()) {
      modes.add(LabNlpMode.HYBRID.name());
    }
    return List.copyOf(modes);
  }

  private List<LabPromptPreset> promptPresets() {
    return List.of(
        new LabPromptPreset(
            "mixed",
            "Mixed Enterprise Payload",
            "Customer John Doe from Berlin can be reached at john.doe@example.com. "
                + "Validate DE75512108001245126199, FR1420041010050500013M02606, and test card "
                + "4012000033330026."),
        new LabPromptPreset(
            "ro",
            "Romania",
            "Validate customer 1751015412728 and RO49AAAA1B31007593840000 for support ticket 42."),
        new LabPromptPreset(
            "us",
            "United States",
            "Escalate payroll record 123-45-6789 and routing 021000021 for user "
                + "alice@example.com."),
        new LabPromptPreset(
            "nlplab",
            "NLP Person Names",
            "Customer Jane Smith spoke with account manager Robert Miles about a refund today."));
  }

  private List<PrismRulePack> selectedRulePacks(
      List<String> requestedRulePacks, LabNlpMode nlpMode) {
    Map<String, PrismRulePack> available = installedRulePackMap();
    LinkedHashSet<String> requested =
        requestedRulePacks == null || requestedRulePacks.isEmpty()
            ? new LinkedHashSet<>(defaultRulePackIds())
            : requestedRulePacks.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    List<PrismRulePack> selected = new ArrayList<>();
    for (String rulePackId : requested) {
      PrismRulePack rulePack = available.get(rulePackId);
      if (rulePack != null) {
        selected.add(rulePack);
      }
    }
    if (selected.isEmpty()) {
      selected.addAll(runtimeRulePacks);
    }
    if (nlpMode != LabNlpMode.OFF) {
      selected.add(createNlpRulePack(nlpMode));
    }
    return List.copyOf(selected);
  }

  private PrismRulePack createNlpRulePack(LabNlpMode nlpMode) {
    NlpPersonNameProperties properties = new NlpPersonNameProperties();
    switch (nlpMode) {
      case HEURISTIC -> properties.setBackend(NlpPersonNameProperties.Backend.HEURISTIC);
      case HYBRID -> {
        properties.setBackend(NlpPersonNameProperties.Backend.HYBRID);
        properties.setModelResource(properties().getNlpModelResource());
        if (!hybridModelAvailable()) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST,
              "Hybrid NLP mode requires a configured OpenNLP person-name model "
                  + "in the enterprise lab.");
        }
      }
      case OFF -> throw new IllegalStateException("OFF mode must not create an NLP rule pack");
      default -> throw new IllegalStateException("Unsupported NLP mode: " + nlpMode);
    }
    List<PersonNameBackend> backends = new ArrayList<>();
    if (nlpMode == LabNlpMode.HEURISTIC || nlpMode == LabNlpMode.HYBRID) {
      backends.add(new HeuristicPersonNameBackend());
    }
    if (nlpMode == LabNlpMode.HYBRID) {
      backends.add(createOpenNlpBackend(properties));
    }
    return new NlpExtensionRulePack(List.of(new HybridPersonNameDetector(backends, properties)));
  }

  private PersonNameBackend createOpenNlpBackend(NlpPersonNameProperties properties) {
    Resource resource = resourceLoader.getResource(properties.getModelResource());
    try (InputStream inputStream = resource.getInputStream()) {
      return new OpenNlpPersonNameBackend(new NameFinderME(new TokenNameFinderModel(inputStream)));
    } catch (IOException exception) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Unable to load the OpenNLP model configured for the enterprise lab.",
          exception);
    }
  }

  private boolean hybridModelAvailable() {
    if (properties.getNlpModelResource().isBlank()) {
      return false;
    }
    return resourceLoader.getResource(properties.getNlpModelResource()).exists();
  }

  private Map<String, PrismRulePack> installedRulePackMap() {
    Map<String, PrismRulePack> packs = new LinkedHashMap<>();
    for (PrismRulePack rulePack : installedRulePacks) {
      String name = rulePack.getName().toUpperCase(Locale.ROOT);
      if (List.of("UNIVERSAL", "RO", "US", "PL", "NL", "GB", "FR", "DE").contains(name)) {
        packs.putIfAbsent(name, rulePack);
      }
    }
    return packs;
  }

  private String outboundPayload(String integration, String sanitizedText) {
    return switch (integration) {
      case "mcp" ->
          prettyJson(
              Map.of(
                  "jsonrpc",
                  "2.0",
                  "method",
                  "tools/call",
                  "params",
                  Map.of("prompt", sanitizedText)));
      case "langchain4j" ->
          prettyJson(Map.of("messages", List.of(Map.of("role", "user", "content", sanitizedText))));
      default -> sanitizedText;
    };
  }

  private String mockModelResponse(String integration, String sanitizedText) {
    return switch (integration) {
      case "mcp" ->
          prettyJson(
              Map.of(
                  "jsonrpc",
                  "2.0",
                  "result",
                  Map.of("message", MOCK_PREFIX + sanitizedText + MOCK_SUFFIX)));
      default -> MOCK_PREFIX + sanitizedText + MOCK_SUFFIX;
    };
  }

  private String prettyJson(Map<String, Object> payload) {
    try {
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize demo lab payload", exception);
    }
  }

  private PrismFailureMode parseFailureMode(String value) {
    try {
      return value == null || value.isBlank()
          ? PrismFailureMode.FAIL_SAFE
          : PrismFailureMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown failure mode");
    }
  }

  private LabNlpMode parseNlpMode(String value) {
    try {
      return value == null || value.isBlank()
          ? LabNlpMode.HEURISTIC
          : LabNlpMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown NLP mode");
    }
  }

  private LabRouteMode parseRouteMode(String value) {
    try {
      return value == null || value.isBlank()
          ? LabRouteMode.AUTO
          : LabRouteMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown route mode");
    }
  }

  private String normalized(String value, String defaultValue) {
    return value == null || value.isBlank() ? defaultValue : value.trim().toLowerCase(Locale.ROOT);
  }

  private String peerNodeId() {
    return "node-a".equalsIgnoreCase(properties.getNodeId()) ? "node-b" : "node-a";
  }

  private LabProperties properties() {
    return properties;
  }
}
