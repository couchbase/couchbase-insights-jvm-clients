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

import com.couchbase.insights.client.java.codec.Deserializer;
import com.couchbase.insights.client.java.internal.DefaultJacksonDeserializerHolder;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

import static com.couchbase.insights.client.java.internal.utils.lang.CbObjects.defaultIfNull;

/**
 * A mutable builder for configuring the cluster's behavior.
 *
 * @see Cluster#newInstance(String, Credential, Consumer)
 */
public final class ClusterOptions {
  @Nullable Deserializer deserializer;
  final SecurityOptions security = new SecurityOptions();
  final TimeoutOptions timeout = new TimeoutOptions();
  @Nullable Integer maxRetries;

  ClusterOptions() {
  }

  Unmodifiable build() {
    return new Unmodifiable(this);
  }

  /**
   * Sets the default deserializer for converting query result rows into Java objects.
   * <p>
   * If not specified, the SDK uses an instance of {@code JacksonDeserializer}
   * backed by a JsonMapper.
   * <p>
   * For complete control over the data conversion process,
   * provide your own custom {@link Deserializer} implementation.
   * <p>
   * Can be overridden on a per-query basis by calling
   * {@link QueryOptions#deserializer(Deserializer)}.
   */
  public ClusterOptions deserializer(@Nullable Deserializer deserializer) {
    this.deserializer = deserializer;
    return this;
  }

  /**
   * Default retry limit for failed retriable queries.
   * <p>
   * If not specified, defaults to {@value Unmodifiable#DEFAULT_MAX_RETRIES}.
   * <p>
   * Can be overridden on a per-query basis by calling
   * {@link QueryOptions#maxRetries(Integer)}.
   *
   * @throws IllegalArgumentException if the value is negative
   */
  public ClusterOptions maxRetries(@Nullable Integer maxRetries) {
    if (maxRetries != null && maxRetries < 0) {
      throw new IllegalArgumentException("maxRetries must be non-negative, but got: " + maxRetries);
    }
    this.maxRetries = maxRetries;
    return this;
  }

  public ClusterOptions security(Consumer<SecurityOptions> optionsCustomizer) {
    optionsCustomizer.accept(this.security);
    return this;
  }

  public ClusterOptions timeout(Consumer<TimeoutOptions> optionsCustomizer) {
    optionsCustomizer.accept(this.timeout);
    return this;
  }

  static class Unmodifiable {
    private static final int DEFAULT_MAX_RETRIES = 7;

    private final TimeoutOptions.Unmodifiable timeout;
    private final SecurityOptions.Unmodifiable security;
    private final Deserializer deserializer;
    private final int maxRetries;

    Unmodifiable(ClusterOptions builder) {
      this.timeout = builder.timeout.build();
      this.security = builder.security.build();
      this.maxRetries = defaultIfNull(builder.maxRetries, DEFAULT_MAX_RETRIES);

      this.deserializer = builder.deserializer != null
        ? builder.deserializer
        : DefaultJacksonDeserializerHolder.DESERIALIZER;
    }

    public TimeoutOptions.Unmodifiable timeout() {
      return timeout;
    }

    public SecurityOptions.Unmodifiable security() {
      return security;
    }

    public Deserializer deserializer() {
      return deserializer;
    }

    @Experimental
    public int maxRetries() {
      return maxRetries;
    }

    @Override
    public String toString() {
      return "ClusterOptions{" +
        "timeout=" + timeout +
        ", security=" + security +
        ", deserializer=" + deserializer +
        ", maxRetries=" + maxRetries +
        '}';
    }

  }
}
