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

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.function.Consumer;

import static com.couchbase.insights.client.java.internal.utils.lang.CbObjects.defaultIfNull;
import static com.couchbase.insights.client.java.internal.utils.time.GolangDuration.parseDuration;

/**
 * For specifying default timeouts in
 * {@link Cluster#newInstance(String, Credential, Consumer)}.
 */
public final class TimeoutOptions {
  TimeoutOptions() {
  }

  // Specify as Golang durations so it's easy to reference them in Javadoc using @value tags.
  private static final String DEFAULT_CONNECT_TIMEOUT = "10s";
  private static final String DEFAULT_QUERY_TIMEOUT = "10m";
  private static final String DEFAULT_HANDLE_REQUEST_TIMEOUT = "30s";

  private @Nullable Duration connectTimeout;
  private @Nullable Duration queryTimeout;
  private @Nullable Duration handleRequestTimeout;

  Unmodifiable build() {
    return new Unmodifiable(this);
  }

  static @Nullable Duration requireNullOrNonNegative(@Nullable Duration d, String name) {
    if (d != null && d.isNegative()) {
      throw new IllegalArgumentException(name + " must be non-negative, but got: " + d);
    }
    return d;
  }

  /**
   * Default time limit for long-running requests that might involve intensive
   * server-side processing like executing a query or fetching result rows.
   * <p>
   * If not specified, defaults to {@value #DEFAULT_QUERY_TIMEOUT}
   *
   * @see Queryable#executeQuery
   * @see Queryable#executeStreamingQuery
   * @see QueryResultHandle#bufferRows
   * @see QueryResultHandle#streamRows
   */
  public TimeoutOptions queryTimeout(@Nullable Duration queryTimeout) {
    this.queryTimeout = requireNullOrNonNegative(queryTimeout, "queryTimeout");
    return this;
  }

  /**
   * Default time limit for requests that return a {@link QueryHandle} or {@link QueryResultHandle}.
   * <p>
   * These request typically do not involve intensive server-side processing,
   * so this timeout should be set to a duration significantly shorter than {@link #queryTimeout}.
   * <p>
   * If not specified, defaults to {@value #DEFAULT_HANDLE_REQUEST_TIMEOUT}
   *
   * @see Queryable#startQuery
   * @see QueryHandle#fetchStatus
   */
  public TimeoutOptions handleRequestTimeout(@Nullable Duration handleRequestTimeout) {
    this.handleRequestTimeout = requireNullOrNonNegative(handleRequestTimeout, "requestTimeout");
    return this;
  }

  /**
   * Time limit for establishing a socket connection to the server.
   * <p>
   * If not specified, defaults to {@value #DEFAULT_CONNECT_TIMEOUT}
   */
  public TimeoutOptions connectTimeout(@Nullable Duration connectTimeout) {
    this.connectTimeout = requireNullOrNonNegative(connectTimeout, "connectTimeout");
    return this;
  }

  static class Unmodifiable {
    private final Duration queryTimeout;
    private final Duration connectTimeout;
    private final Duration handleRequestTimeout;

    private Unmodifiable(TimeoutOptions builder) {
      this.connectTimeout = defaultIfNull(builder.connectTimeout, parseDuration(DEFAULT_CONNECT_TIMEOUT));
      this.queryTimeout = defaultIfNull(builder.queryTimeout, parseDuration(DEFAULT_QUERY_TIMEOUT));
      this.handleRequestTimeout = defaultIfNull(builder.handleRequestTimeout, parseDuration(DEFAULT_HANDLE_REQUEST_TIMEOUT));
    }

    public Duration queryTimeout() {
      return queryTimeout;
    }

    public Duration connectTimeout() {
      return connectTimeout;
    }

    public Duration handleRequestTimeout() {
      return handleRequestTimeout;
    }

    @Override
    public String toString() {
      return "TimeoutOptions{" +
        "queryTimeout=" + queryTimeout +
        ", requestTimeout=" + handleRequestTimeout +
        ", connectTimeout=" + connectTimeout +
        ", handleRequestTimeout=" + handleRequestTimeout +
        '}';
    }
  }
}
