/*
 * Copyright 2026 Couchbase, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.couchbase.insights.client.java;

import com.couchbase.insights.client.java.internal.ThreadSafe;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

@ThreadSafe
public interface RowSource {
  /**
   * Returns the number of result rows.
   */
  long rowCount();

  /**
   * Calls {@link #streamRows(Consumer, Consumer)} with default options.
   */
  default QueryMetadata streamRows(Consumer<Row> rowAction) {
    return streamRows(rowAction, options -> {
    });
  }

  /**
   * Fetches result rows and passes them to the given {@code rowAction} callback,
   * one by one as they arrive from the server.
   * <p>
   * The callback action is guaranteed to execute in the same thread
   * (or virtual thread) that called this method. If the callback throws
   * an exception, the exception is re-thrown by this method.
   * <p>
   * Prefer this over {@link #bufferRows()} when it's unknown whether the rows fit in memory.
   *
   * @param options A callback for specifying custom options.
   * @throws QueryException if the server response indicates an error occurred.
   * @throws InvalidCredentialException if the server rejects the user credentials.
   * @throws InsightsTimeoutException if the streaming does not complete before the timeout expires.
   * @throws CancellationException if the calling thread is interrupted.
   * @throws RuntimeException if the row action callback throws an exception.
   */
  QueryMetadata streamRows(
    Consumer<Row> rowAction,
    Consumer<RowOptions> options
  );

  /**
   * Calls {@link #bufferRows(Consumer)} with default options.
   */
  default QueryResult bufferRows() {
    return bufferRows(options -> {
    });
  }

  /**
   * Fetches result rows and buffers them in memory. Returns a {@link QueryResult} holding the rows and metadata.
   * <p>
   * If it's unknown whether the results rows fit in memory, use {@link #streamRows} instead.
   *
   * @param options A callback for specifying custom options.
   * @throws QueryException if the server response indicates an error occurred.
   * @throws InvalidCredentialException if the server rejects the user credentials.
   * @throws InsightsTimeoutException if the result rows cannot be fetched before the timeout expires.
   * @throws CancellationException if the calling thread is interrupted.
   * @throws RuntimeException if the row action callback throws an exception.
   */
  default QueryResult bufferRows(Consumer<RowOptions> options) {
    try {
      List<Row> rows = new ArrayList<>(Math.toIntExact(rowCount()));
      QueryMetadata md = streamRows(rows::add, options);
      return new QueryResult(rows, md);

    } catch (ArithmeticException e) {
      throw new InsightsException("Result has too many rows to buffer (" + rowCount() + "rows)");
    }
  }
}
