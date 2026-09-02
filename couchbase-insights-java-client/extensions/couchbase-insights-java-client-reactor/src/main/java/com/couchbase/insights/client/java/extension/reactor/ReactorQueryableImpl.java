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

package com.couchbase.insights.client.java.extension.reactor;

import com.couchbase.insights.client.java.QueryOptions;
import com.couchbase.insights.client.java.Queryable;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

class ReactorQueryableImpl implements ReactorQueryable {
  private final Queryable clusterOrScope;
  private final ReactorOptions.Unmodifiable reactorOptions;

  ReactorQueryableImpl(
    Queryable clusterOrScope,
    ReactorOptions.Unmodifiable reactorOptions
  ) {
    this.clusterOrScope = requireNonNull(clusterOrScope);
    this.reactorOptions = requireNonNull(reactorOptions);
  }


  @Override
  public Mono<ReactorQueryResult> executeQuery(
    String statement,
    Consumer<QueryOptions> options
  ) {
    // Defer so each subscription starts a new query.
    return Mono.defer(() -> new ReactorQuery(
        clusterOrScope,
        statement,
        options,
        reactorOptions
      ).execute()
    );
  }
}
