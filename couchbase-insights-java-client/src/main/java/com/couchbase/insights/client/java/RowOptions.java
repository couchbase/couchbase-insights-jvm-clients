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

import com.couchbase.insights.client.java.codec.Deserializer;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.function.Consumer;

import static com.couchbase.insights.client.java.internal.utils.lang.CbObjects.defaultIfNull;

/**
 * Optional parameters for methods that fetch rows from a {@link QueryResultHandle}.
 *
 * @see RowSource#streamRows(Consumer, Consumer)
 * @see RowSource#bufferRows(Consumer)
 */
public class RowOptions {
  private @Nullable Duration timeout;
  private @Nullable Deserializer deserializer;
  private @Nullable Integer maxRetries;

  RowOptions() {
  }

  /**
   * If not specified, defaults to {@link TimeoutOptions#queryTimeout(Duration)}.
   */
  public RowOptions timeout(@Nullable Duration timeout) {
    this.timeout = timeout;
    return this;
  }

  /**
   * Sets the deserializer used by {@link Row#as} to convert query result rows into Java objects.
   * <p>
   * If not specified, defaults to the cluster's default deserializer.
   *
   * @see ClusterOptions#deserializer(Deserializer)
   */
  public RowOptions deserializer(@Nullable Deserializer deserializer) {
    this.deserializer = deserializer;
    return this;
  }

  /**
   * Limits the number of times a failed retriable request is retried.
   * <p>
   * If not specified, defaults to the cluster's default max retries.
   *
   * @see ClusterOptions#maxRetries(Integer)
   */
  @Experimental
  public RowOptions maxRetries(@Nullable Integer maxRetries) {
    this.maxRetries = maxRetries;
    return this;
  }

  Unmodifiable build(ClusterOptions.Unmodifiable defaults) {
    return new Unmodifiable(this, defaults);
  }

  static class Unmodifiable {
    private final Duration timeout;
    private final Deserializer deserializer;
    private final Integer maxRetries;

    Unmodifiable(RowOptions builder, ClusterOptions.Unmodifiable defaults) {
      this.timeout = defaultIfNull(builder.timeout, defaults.timeout().queryTimeout());
      this.deserializer = defaultIfNull(builder.deserializer, defaults.deserializer());
      this.maxRetries = defaultIfNull(builder.maxRetries, defaults.maxRetries());
    }

    public Duration timeout() {
      return timeout;
    }

    public Deserializer deserializer() {
      return deserializer;
    }

    public Integer maxRetries() {
      return maxRetries;
    }
  }
}
