/*
 * Copyright 2025 Couchbase, Inc.
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

import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

@ThreadSafe
public interface Queryable {

  /**
   * Starts executing a query statement using the specified options (query parameters, etc.),
   * and returns a "handle" for fetching the results later.
   * <p>
   * Use this execution mode to ensure resilience against network interruptions
   * during long-running queries.
   *
   * @param statement The Analytics SQL++ statement to execute.
   * @param options A callback for specifying custom query options.
   * @return A handle for fetching the result later.
   * @throws QueryException if the server response indicates an error occurred
   * @throws InvalidCredentialException if the server rejects the user credentials.
   * @throws InsightsTimeoutException if the server does not respond before the timeout expires.
   * @throws CancellationException if the calling thread is interrupted.
   */
  QueryHandle startQuery(
    String statement,
    Consumer<StartQueryOptions> options
  );

  /**
   * Starts executing a query statement using default options,
   * and returns a "handle" for fetching the results later.
   * <p>
   * Use this execution mode to ensure resilience against network interruptions
   * during long-running queries.
   *
   * @param statement The Analytics SQL++ statement to execute.
   * @return A handle for fetching the result later.
   * @throws QueryException if the server response indicates an error occurred
   * @throws InvalidCredentialException if the server rejects the user credentials.
   * @throws InsightsTimeoutException if the server does not respond before the timeout expires.
   * @throws CancellationException if the calling thread is interrupted.
   */
  default QueryHandle startQuery(String statement) {
    return startQuery(statement, options -> {
    });
  }

  /**
   * Executes a query statement using the specified options
   * (query parameters, etc.), and buffers all result rows in memory.
   * <p>
   * If the results are not known to fit in memory, consider using the
   * streaming version that takes a row action callback:
   * {@link #executeStreamingQuery(String, Consumer, Consumer)}.
   *
   * @param statement The Analytics SQL++ statement to execute.
   * @param options A callback for specifying custom query options.
   * @return A query result consisting of metadata and a list of rows.
   * @throws QueryException if the server response indicates an error occurred.
   * @throws InvalidCredentialException if the server rejects the user credentials.
   * @throws InsightsTimeoutException if the query does not complete before the timeout expires.
   * @throws CancellationException if the calling thread is interrupted.
   */
  QueryResult executeQuery(
    String statement,
    Consumer<QueryOptions> options
  );

  /**
   * Executes a query statement using default options
   * (no query parameters, etc.), and buffers all result rows in memory.
   * <p>
   * If the statement has parameters, use the overload that takes options:
   * {@link #executeQuery(String, Consumer)}.
   * <p>
   * If the results are not known to fit in memory, consider using the
   * streaming version that takes a row action callback:
   * {@link #executeStreamingQuery(String, Consumer)}.
   *
   * @param statement The Analytics SQL++ statement to execute.
   * @return A query result consisting of metadata and a list of rows.
   * @throws QueryException if the server response indicates an error occurred.
   * @throws InvalidCredentialException if the server rejects the user credentials.
   * @throws InsightsTimeoutException if the query does not complete before the timeout expires.
   * @throws CancellationException if the calling thread is interrupted.
   */
  default QueryResult executeQuery(String statement) {
    return executeQuery(
      statement,
      options -> {
      }
    );
  }

  /**
   * Executes a query statement using the specified options,
   * (query parameters, etc.), and passes result rows to the given
   * {@code rowAction} callback, one by one as they arrive from the server.
   * <p>
   * The callback action is guaranteed to execute in the same thread
   * (or virtual thread) that called this method. If the callback throws
   * an exception, the query is cancelled and the exception is re-thrown
   * by this method.
   *
   * @param statement The Analytics SQL++ statement to execute.
   * @param options A callback for specifying custom query options.
   * @return Query metadata.
   * @throws QueryException if the server response indicates an error occurred.
   * @throws InvalidCredentialException if the server rejects the user credentials.
   * @throws InsightsTimeoutException if the query does not complete before the timeout expires.
   * @throws CancellationException if the calling thread is interrupted.
   * @throws RuntimeException if the row action callback throws an exception.
   */
  QueryMetadata executeStreamingQuery(
    String statement,
    Consumer<Row> rowAction,
    Consumer<QueryOptions> options
  );

  /**
   * Executes a query statement and passes result rows to the given
   * {@code rowAction} callback, one by one as they arrive from the server.
   * <p>
   * The callback action is guaranteed to execute in the same thread
   * (or virtual thread) that called this method. If the callback throws
   * an exception, the query is cancelled and the exception is re-thrown
   * by this method.
   * <p>
   * If the statement has parameters, use the overload that takes options:
   * {@link #executeStreamingQuery(String, Consumer, Consumer)}.
   *
   * @param statement The Analytics SQL++ statement to execute.
   * @return Query metadata.
   * @throws QueryException if the server response indicates an error occurred.
   * @throws InvalidCredentialException if the server rejects the user credentials.
   * @throws InsightsTimeoutException if the query does not complete before the timeout expires.
   * @throws CancellationException if the calling thread is interrupted.
   * @throws RuntimeException if the row action callback throws an exception.
   */
  default QueryMetadata executeStreamingQuery(
    String statement,
    Consumer<Row> rowAction
  ) {
    return executeStreamingQuery(
      statement,
      rowAction,
      options -> {
      }
    );
  }
}
