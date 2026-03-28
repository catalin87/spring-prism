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

import io.github.catalin87.prism.core.PrismProtectionException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Public API for the enterprise demo lab frontend. */
@RestController
@RequestMapping("/lab/api")
class LabController {

  private final LabService labService;

  LabController(LabService labService) {
    this.labService = labService;
  }

  @GetMapping("/bootstrap")
  LabBootstrapResponse bootstrap() {
    return labService.bootstrap();
  }

  @PostMapping("/run")
  LabRunResponse run(@RequestBody LabRunRequest request) {
    return labService.run(request);
  }

  @GetMapping("/metrics")
  LabMetricsResponse metrics() {
    return labService.metrics();
  }

  @GetMapping("/nodes")
  List<LabNodeStatus> nodes() {
    return labService.nodeStatuses();
  }

  @GetMapping("/internal/node-status")
  LabNodeStatus internalNodeStatus() {
    return labService.localNodeStatus();
  }

  @PostMapping("/simulators/redis-outage")
  ResponseEntity<Map<String, Object>> simulateRedisOutage() {
    labService.simulateRedisOutage(true);
    return ResponseEntity.accepted().body(Map.of("active", true));
  }

  @PostMapping("/simulators/redis-recover")
  ResponseEntity<Map<String, Object>> recoverRedis() {
    labService.simulateRedisOutage(false);
    return ResponseEntity.accepted().body(Map.of("active", false));
  }

  @PostMapping("/internal/restore")
  LabRestoreResponse restore(@RequestBody LabRestoreRequest request) {
    return labService.restoreInternal(request);
  }

  @PostMapping("/internal/simulator/redis-outage")
  ResponseEntity<Map<String, Object>> internalOutage(@RequestBody Map<String, Object> request) {
    Object active = request.get("active");
    labService.applyInternalOutage(Boolean.TRUE.equals(active));
    return ResponseEntity.accepted().body(Map.of("active", Boolean.TRUE.equals(active)));
  }

  @ExceptionHandler({ResponseStatusException.class, PrismProtectionException.class})
  ResponseEntity<Map<String, Object>> handle(RuntimeException exception) {
    if (exception instanceof ResponseStatusException responseStatusException) {
      return ResponseEntity.status(responseStatusException.getStatusCode())
          .body(
              Map.of(
                  "message",
                  Objects.requireNonNullElse(
                      responseStatusException.getReason(), "Enterprise lab failed")));
    }
    PrismProtectionException protectionException = (PrismProtectionException) exception;
    return ResponseEntity.internalServerError()
        .body(
            Map.of(
                "message", protectionException.getMessage(),
                "phase", protectionException.phase().name(),
                "reason", protectionException.reason().name()));
  }
}
