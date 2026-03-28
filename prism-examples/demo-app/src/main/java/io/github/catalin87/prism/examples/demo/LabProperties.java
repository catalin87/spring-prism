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

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Externalized runtime settings for the enterprise demo lab sandbox. */
@ConfigurationProperties(prefix = "lab")
public class LabProperties {

  private String nodeId = "node-a";
  private String selfBaseUrl = "http://localhost:8080";
  private String peerBaseUrl = "http://localhost:8081";
  private String publicBaseUrl = "http://localhost:8080";
  private String grafanaUrl = "http://localhost:3000";
  private String nlpModelResource = "";

  public String getNodeId() {
    return nodeId;
  }

  public void setNodeId(String nodeId) {
    this.nodeId = nodeId == null || nodeId.isBlank() ? "node-a" : nodeId.trim();
  }

  public String getSelfBaseUrl() {
    return selfBaseUrl;
  }

  public void setSelfBaseUrl(String selfBaseUrl) {
    this.selfBaseUrl = selfBaseUrl == null || selfBaseUrl.isBlank() ? "" : selfBaseUrl.trim();
  }

  public String getPeerBaseUrl() {
    return peerBaseUrl;
  }

  public void setPeerBaseUrl(String peerBaseUrl) {
    this.peerBaseUrl = peerBaseUrl == null || peerBaseUrl.isBlank() ? "" : peerBaseUrl.trim();
  }

  public String getPublicBaseUrl() {
    return publicBaseUrl;
  }

  public void setPublicBaseUrl(String publicBaseUrl) {
    this.publicBaseUrl =
        publicBaseUrl == null || publicBaseUrl.isBlank() ? "" : publicBaseUrl.trim();
  }

  public String getGrafanaUrl() {
    return grafanaUrl;
  }

  public void setGrafanaUrl(String grafanaUrl) {
    this.grafanaUrl = grafanaUrl == null || grafanaUrl.isBlank() ? "" : grafanaUrl.trim();
  }

  public String getNlpModelResource() {
    return nlpModelResource;
  }

  public void setNlpModelResource(String nlpModelResource) {
    this.nlpModelResource =
        nlpModelResource == null || nlpModelResource.isBlank() ? "" : nlpModelResource.trim();
  }
}
