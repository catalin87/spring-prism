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
package io.github.catalin87.prism.core.vault;

import java.util.Objects;
import org.jspecify.annotations.NonNull;

/**
 * A mathematical state buffer natively solving the generative LLM fragmentation problem globally.
 * External LLMs yield secure tokens randomly across chunks (e.g. Chunk 1: {@code <PRI}, Chunk 2:
 * {@code SM_EMAIL_}), which shatters Regex restoration logic bounds severely.
 *
 * <p>StreamingBuffer exclusively structures incomplete boundaries safely into a continuous matrix.
 */
public class StreamingBuffer {

  private final StringBuilder fragmentationBuffer = new StringBuilder();

  /**
   * Evaluates an incoming string chunk specifically from an LLM Server-Sent Event (SSE). Extracts
   * safely flushable text bounds while securely retaining actively dangling Token fragments.
   *
   * @param chunk The raw payload text natively output by the external generative provider context.
   * @return The securely unfragmented textual payload context sequence.
   */
  @NonNull
  public String processChunk(@NonNull String chunk) {
    if (chunk.isEmpty() && fragmentationBuffer.isEmpty()) {
      return "";
    }

    if (!chunk.isEmpty()) {
      fragmentationBuffer.append(chunk);
    }

    // The boundary metric for our cryptographic tokens is strictly defined utilizing '<' and '>'.
    int lastOpenBracket = lastIndexOf(fragmentationBuffer, '<');
    int lastCloseBracket = lastIndexOf(fragmentationBuffer, '>');

    // Standard topological line: No dangling incomplete bounds sequentially detected
    if (lastOpenBracket == -1 || (lastCloseBracket != -1 && lastCloseBracket > lastOpenBracket)) {
      String completeChunk = Objects.requireNonNull(fragmentationBuffer.toString());
      fragmentationBuffer.setLength(0);
      return completeChunk;
    }

    // Structural constraint: A `<` was isolated natively without a subsequent resolving `>`.
    // We logically mathematically split the output: safely flush everything before the `<`
    // instantly,
    // and securely lock the dangling sequence natively inside the internal buffer bounds.
    String safelyFlushable =
        Objects.requireNonNull(fragmentationBuffer.substring(0, lastOpenBracket));
    fragmentationBuffer.delete(0, lastOpenBracket);
    return safelyFlushable;
  }

  /**
   * Flush execution. Invoked natively upon standard termination of the generative LLM stream
   * payload boundary.
   *
   * @return Unbuffered terminal elements residing structurally sequence bounds.
   */
  @NonNull
  public String flush() {
    String remainder = Objects.requireNonNull(fragmentationBuffer.toString());
    fragmentationBuffer.setLength(0);
    return remainder;
  }

  private static int lastIndexOf(StringBuilder source, char needle) {
    for (int index = source.length() - 1; index >= 0; index--) {
      if (source.charAt(index) == needle) {
        return index;
      }
    }
    return -1;
  }
}
