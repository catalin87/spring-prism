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
package io.github.catalin87.prism.extensions.nlp;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Externalized settings for the optional NLP person-name extension. */
@ConfigurationProperties(prefix = "spring.prism.extensions.nlp")
public class NlpPersonNameProperties {

  private boolean enabled;
  private Backend backend = Backend.HYBRID;
  private String modelResource = "";
  private int confidenceThreshold = 4;
  private int maxTokens = 3;
  private boolean allowSingleTokenWithTitle = true;
  private List<String> positiveContextTerms = new ArrayList<>();
  private List<String> blockedPhrases = new ArrayList<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Backend getBackend() {
    return backend;
  }

  public void setBackend(Backend backend) {
    this.backend = backend == null ? Backend.HYBRID : backend;
  }

  public String getModelResource() {
    return modelResource;
  }

  public void setModelResource(String modelResource) {
    this.modelResource = modelResource == null ? "" : modelResource.trim();
  }

  public int getConfidenceThreshold() {
    return confidenceThreshold;
  }

  public void setConfidenceThreshold(int confidenceThreshold) {
    this.confidenceThreshold = Math.max(1, confidenceThreshold);
  }

  public int getMaxTokens() {
    return maxTokens;
  }

  public void setMaxTokens(int maxTokens) {
    this.maxTokens = maxTokens <= 0 ? 3 : maxTokens;
  }

  public boolean isAllowSingleTokenWithTitle() {
    return allowSingleTokenWithTitle;
  }

  public void setAllowSingleTokenWithTitle(boolean allowSingleTokenWithTitle) {
    this.allowSingleTokenWithTitle = allowSingleTokenWithTitle;
  }

  public List<String> getPositiveContextTerms() {
    return positiveContextTerms;
  }

  public void setPositiveContextTerms(List<String> positiveContextTerms) {
    this.positiveContextTerms =
        positiveContextTerms == null ? new ArrayList<>() : new ArrayList<>(positiveContextTerms);
  }

  public List<String> getBlockedPhrases() {
    return blockedPhrases;
  }

  public void setBlockedPhrases(List<String> blockedPhrases) {
    this.blockedPhrases =
        blockedPhrases == null ? new ArrayList<>() : new ArrayList<>(blockedPhrases);
  }

  public boolean requiresModelResource() {
    return backend == Backend.OPENNLP || backend == Backend.HYBRID;
  }

  /** Supported optional NLP backends. */
  public enum Backend {
    HEURISTIC,
    OPENNLP,
    HYBRID
  }
}
