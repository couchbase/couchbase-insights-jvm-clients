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
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

import static com.couchbase.insights.client.java.internal.utils.lang.CbObjects.defaultIfNull;

/**
 * Optional parameters common to methods that execute a query statement
 * and immediately process the result.
 *
 * @see Queryable#executeQuery(String, Consumer)
 * @see Queryable#executeStreamingQuery(String, Consumer)
 */
public class QueryOptions extends CommonQueryOptions<QueryOptions> {
  private @Nullable Deserializer deserializer;

  QueryOptions() {
  }

  /**
   * Sets the deserializer used by {@link Row#as} to convert query result rows into Java objects.
   * <p>
   * If not specified, defaults to the cluster's default deserializer.
   *
   * @see ClusterOptions#deserializer(Deserializer)
   */
  public QueryOptions deserializer(@Nullable Deserializer deserializer) {
    this.deserializer = deserializer;
    return this;
  }

  Unmodifiable build(ClusterOptions.Unmodifiable defaults) {
    return new Unmodifiable(this, defaults);
  }

  static class Unmodifiable extends CommonQueryOptions.Unmodifiable {
    private final Deserializer deserializer;

    Unmodifiable(
      QueryOptions builder,
      ClusterOptions.Unmodifiable defaults
    ) {
      super(builder, defaults);
      this.deserializer = defaultIfNull(builder.deserializer, defaults.deserializer());
    }

    @Override public String toString() {
      return "QueryOptions{" +
        "deserializer=" + deserializer +
        ", common=" + super.toString() +
        '}';
    }

    public Deserializer deserializer() {
      return deserializer;
    }
  }

  static QueryOptions.Unmodifiable configure(
    ClusterOptions.Unmodifiable defaults,
    Consumer<QueryOptions> configurator
  ) {
    QueryOptions builder = new QueryOptions();
    configurator.accept(builder);
    return builder.build(defaults);
  }
}
