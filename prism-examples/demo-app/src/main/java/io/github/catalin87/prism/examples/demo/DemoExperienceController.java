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

import java.util.Map;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** API controller serving the unified demo frontend. */
@RestController
@RequestMapping("/demo-lab/api")
class DemoExperienceController {

  private final DemoExperienceService demoExperienceService;

  DemoExperienceController(DemoExperienceService demoExperienceService) {
    this.demoExperienceService = demoExperienceService;
  }

  @PostMapping("/options")
  DemoOptionsResponse options() {
    return demoExperienceService.options();
  }

  @PostMapping("/run")
  DemoRunResponse run(
      @RequestBody DemoRunRequest request, jakarta.servlet.http.HttpServletRequest servletRequest) {
    String baseUrl =
        servletRequest.getScheme()
            + "://"
            + servletRequest.getServerName()
            + ":"
            + servletRequest.getServerPort();
    return demoExperienceService.run(request, baseUrl);
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, Object>> handle(ResponseStatusException exception) {
    return ResponseEntity.status(exception.getStatusCode())
        .body(Map.of("message", Objects.requireNonNullElse(exception.getReason(), "Demo failed")));
  }
}
