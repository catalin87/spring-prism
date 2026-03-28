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
package io.github.catalin87.prism.extensions.nlp.autoconfigure;

import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.extensions.nlp.HeuristicPersonNameBackend;
import io.github.catalin87.prism.extensions.nlp.HybridPersonNameDetector;
import io.github.catalin87.prism.extensions.nlp.NlpExtensionRulePack;
import io.github.catalin87.prism.extensions.nlp.NlpPersonNameProperties;
import io.github.catalin87.prism.extensions.nlp.OpenNlpPersonNameBackend;
import io.github.catalin87.prism.extensions.nlp.PersonNameBackend;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/** Auto-configuration for the optional NLP extension rule pack. */
@AutoConfiguration
@ConditionalOnClass(NameFinderME.class)
@EnableConfigurationProperties(NlpPersonNameProperties.class)
@ConditionalOnProperty(
    prefix = "spring.prism.extensions.nlp",
    name = "enabled",
    havingValue = "true")
public class PrismNlpAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = "prismNlpRulePack")
  PrismRulePack prismNlpRulePack(
      List<PersonNameBackend> personNameBackends, NlpPersonNameProperties properties) {
    return new NlpExtensionRulePack(
        List.of(new HybridPersonNameDetector(personNameBackends, properties)));
  }

  @Bean
  @ConditionalOnMissingBean(name = "prismNlpPersonNameBackends")
  List<PersonNameBackend> prismNlpPersonNameBackends(
      NlpPersonNameProperties properties, ResourceLoader resourceLoader) {
    List<PersonNameBackend> backends = new ArrayList<>();
    if (properties.getBackend() == NlpPersonNameProperties.Backend.HEURISTIC
        || properties.getBackend() == NlpPersonNameProperties.Backend.HYBRID) {
      backends.add(new HeuristicPersonNameBackend());
    }
    if (properties.getBackend() == NlpPersonNameProperties.Backend.OPENNLP
        || properties.getBackend() == NlpPersonNameProperties.Backend.HYBRID) {
      backends.add(createOpenNlpBackend(properties, resourceLoader));
    }
    return List.copyOf(backends);
  }

  private static PersonNameBackend createOpenNlpBackend(
      NlpPersonNameProperties properties, ResourceLoader resourceLoader) {
    if (properties.getModelResource().isBlank()) {
      throw new IllegalStateException(
          "spring.prism.extensions.nlp.model-resource is required for backend "
              + properties.getBackend().name().toLowerCase());
    }
    Resource resource = resourceLoader.getResource(properties.getModelResource());
    if (!resource.exists()) {
      throw new IllegalStateException(
          "Configured NLP model resource does not exist: " + properties.getModelResource());
    }
    try (InputStream inputStream = resource.getInputStream()) {
      return new OpenNlpPersonNameBackend(new NameFinderME(new TokenNameFinderModel(inputStream)));
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to load OpenNLP person-name model from " + properties.getModelResource(),
          exception);
    }
  }
}
