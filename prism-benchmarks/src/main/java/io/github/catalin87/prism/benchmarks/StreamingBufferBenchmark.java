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

import io.github.catalin87.prism.core.vault.StreamingBuffer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

/** Benchmarks fragmented Prism token buffering during streaming response restoration. */
@State(Scope.Thread)
public class StreamingBufferBenchmark {

  private final String[] chunks = {"message ", "<PRISM_EM", "AIL_ABC123", "> done"};

  /** Measures fragmented token buffering for a representative streaming response sequence. */
  @Benchmark
  public String processFragmentedToken() {
    StreamingBuffer buffer = new StreamingBuffer();
    StringBuilder output = new StringBuilder();
    for (String chunk : chunks) {
      output.append(buffer.processChunk(chunk));
    }
    output.append(buffer.flush());
    return output.toString();
  }
}
