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
package io.github.catalin87.prism.benchmarks;

import io.github.catalin87.prism.core.PiiCandidate;
import io.github.catalin87.prism.extensions.nlp.HeuristicPersonNameBackend;
import io.github.catalin87.prism.extensions.nlp.HybridPersonNameDetector;
import io.github.catalin87.prism.extensions.nlp.NlpPersonNameProperties;
import io.github.catalin87.prism.extensions.nlp.OpenNlpPersonNameBackend;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/** Benchmarks NLP startup and warm-scan paths for heuristic, OpenNLP, and hybrid detection. */
@State(Scope.Benchmark)
public class NlpBenchmark {

  private static final String MODEL_PROPERTY = "prism.bench.nlpModel";
  private static final String DEFAULT_MODEL_PATH = "prism-benchmarks/models/en-ner-person.bin";

  private HeuristicPersonNameBackend heuristicBackend;
  private OpenNlpPersonNameBackend openNlpBackend;
  private HybridPersonNameDetector hybridDetector;
  private Path modelPath;
  private String text;

  /** Preloads the heuristic backend and, when available, the OpenNLP model for warm benchmarks. */
  @Setup
  public void setUp() {
    heuristicBackend = new HeuristicPersonNameBackend();
    text =
        "Customer John Doe escalated the incident to Jane Smith while Dr. Emily Carter reviewed "
            + "the support case for Spring Prism Enterprise Lab.";
    modelPath = resolveModelPath();
    if (Files.isRegularFile(modelPath)) {
      openNlpBackend = loadOpenNlpBackend();
      NlpPersonNameProperties properties = new NlpPersonNameProperties();
      properties.setBackend(NlpPersonNameProperties.Backend.HYBRID);
      hybridDetector =
          new HybridPersonNameDetector(List.of(heuristicBackend, openNlpBackend), properties);
    }
  }

  /** Measures warm repeated heuristic-only person-name detection. */
  @Benchmark
  public int heuristicDetectWarm() {
    return heuristicBackend.detect(text).size();
  }

  /** Measures warm repeated OpenNLP-only person-name detection with a preloaded model. */
  @Benchmark
  public int openNlpDetectWarm() {
    return requireOpenNlpBackend().detect(text).size();
  }

  /** Measures warm repeated hybrid person-name detection. */
  @Benchmark
  public int hybridDetectWarm() {
    return requireHybridDetector().detect(text).size();
  }

  /** Measures the cold startup path for loading the OpenNLP model and creating the backend. */
  @Benchmark
  public OpenNlpPersonNameBackend loadOpenNlpModelCold() {
    return loadOpenNlpBackend();
  }

  /** Measures hybrid detector output size on a representative enterprise text sample. */
  @Benchmark
  public int hybridDetectCandidates() {
    List<PiiCandidate> candidates = requireHybridDetector().detect(text);
    return candidates.size();
  }

  private OpenNlpPersonNameBackend requireOpenNlpBackend() {
    if (openNlpBackend == null) {
      throw new IllegalStateException(
          "OpenNLP benchmark requires -D" + MODEL_PROPERTY + "=" + modelPath.toAbsolutePath());
    }
    return openNlpBackend;
  }

  private HybridPersonNameDetector requireHybridDetector() {
    if (hybridDetector == null) {
      throw new IllegalStateException(
          "Hybrid benchmark requires -D" + MODEL_PROPERTY + "=" + modelPath.toAbsolutePath());
    }
    return hybridDetector;
  }

  private OpenNlpPersonNameBackend loadOpenNlpBackend() {
    if (!Files.isRegularFile(modelPath)) {
      throw new IllegalStateException(
          "OpenNLP benchmark model not found at "
              + modelPath.toAbsolutePath()
              + ". Run scripts/download-nlp-model.sh or scripts/download-nlp-model.cmd first.");
    }
    try (InputStream inputStream = Files.newInputStream(modelPath)) {
      return new OpenNlpPersonNameBackend(new NameFinderME(new TokenNameFinderModel(inputStream)));
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to load OpenNLP benchmark model from " + modelPath.toAbsolutePath(), exception);
    }
  }

  private static Path resolveModelPath() {
    String configured = System.getProperty(MODEL_PROPERTY, DEFAULT_MODEL_PATH);
    return Path.of(configured);
  }
}
