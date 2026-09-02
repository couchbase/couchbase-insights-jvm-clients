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

import com.couchbase.insights.client.java.InsightsTimeoutException;
import com.couchbase.insights.client.java.Cluster;
import com.couchbase.insights.client.java.InvalidCredentialException;
import com.couchbase.insights.client.java.QueryException;
import com.couchbase.insights.client.java.QueryOptions;
import com.couchbase.insights.client.java.Queryable;
import com.couchbase.insights.client.java.Scope;
import com.couchbase.insights.client.java.internal.ThreadSafe;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * Wraps a {@link Cluster} or {@link Scope} for integration with Project Reactor.
 * <p>
 * Example usage:
 * <pre>
 * var reactor = ReactorQueryable.from(clusterOrScope);
 *
 * reactor.executeQuery("SELECT 'hello world' as greeting")
 *     .map(row -> row.as(ObjectNode.class).path("greeting").asText())
 *     .doOnNext(System.out::println)
 *     .blockLast();
 * </pre>
 * Instances are immutable and thread-safe. An instance is reusable
 * until the schedulers it uses are disposed.
 *
 * @see ReactorOptions
 */
@ThreadSafe
public interface ReactorQueryable {
  /**
   * Returns a new instance that wraps the given cluster or scope
   * and exposes a reactive query API, using default {@link ReactorOptions}.
   * <p>
   * If you plan to run more than a few queries concurrently,
   * use the overload that takes an options customizer and
   * set {@link ReactorOptions#queryScheduler(Scheduler)}
   * to a scheduler capable of executing many blocking tasks concurrently.
   * <p>
   * Alternatively, tune the capacity of Reactor's default bounded elastic scheduler
   * using the {@code reactor.schedulers.defaultBoundedElasticSize} system property.
   *
   * @see Schedulers#DEFAULT_BOUNDED_ELASTIC_SIZE
   * @see #from(Queryable, Consumer)
   */
  static ReactorQueryable from(Queryable clusterOrScope) {
    return from(clusterOrScope, options -> {
    });
  }

  /**
   * Returns a new instance that wraps the given cluster or scope
   * and exposes a reactive query API using the provided options.
   * <p>
   * If you plan to run more than a few queries concurrently,
   * set {@link ReactorOptions#queryScheduler(Scheduler)}
   * to a scheduler capable of executing many blocking tasks concurrently.
   * <p>
   * Alternatively, tune the capacity of Reactor's default bounded elastic scheduler
   * using the {@code reactor.schedulers.defaultBoundedElasticSize} system property.
   *
   * @see ReactorOptions
   * @see Schedulers#DEFAULT_BOUNDED_ELASTIC_SIZE
   */
  static ReactorQueryable from(
    Queryable clusterOrScope,
    Consumer<ReactorOptions> optionsCustomizer
  ) {
    ReactorOptions reactorOptions = new ReactorOptions();
    optionsCustomizer.accept(reactorOptions);
    return new ReactorQueryableImpl(clusterOrScope, reactorOptions.build());
  }

  /**
   * Returns a Mono that executes the given query statement and produces a result object
   * for accessing rows and metadata.
   * <p>
   * <b>Caution:</b> If the returned Mono produces a result, you SHOULD subscribe to
   * {@link ReactorQueryResult#rows()} to ensure resources are released promptly.
   * If you do not subscribe within the grace period, the {@code rows()} and
   * {@code metadata()} publishers are automatically canceled.
   *
   * @param statement The SQL++ statement to execute.
   * @return a cold publisher; each subscription executes a new query.
   * @throws QueryException (from the Mono) if the server response indicates an error occurred.
   * @throws InvalidCredentialException (from the Mono) if the server rejects the user credentials.
   * @throws InsightsTimeoutException (from the Mono) if the query does not complete before the timeout expires.
   * @throws RejectedExecutionException (from the Mono) if the query scheduler's task capacity is exceeded.
   * @see ReactorOptions#subscriptionGracePeriod(Duration)
   */
  default Mono<ReactorQueryResult> executeQuery(String statement) {
    return this.executeQuery(statement, (options) -> {
    });
  }

  /**
   * Returns a Mono that executes the given query statement using the specified options
   * (query parameters, etc.) and produces a result object for accessing rows and metadata.
   * <p>
   * <b>Caution:</b> If the returned Mono produces a result, you SHOULD subscribe to
   * {@link ReactorQueryResult#rows()} to ensure resources are released promptly.
   * If you do not subscribe within the grace period, the {@code rows()} and
   * {@code metadata()} publishers are automatically canceled.
   *
   * @param statement The SQL++ statement to execute.
   * @param options A callback for specifying custom query options.
   * @return a cold publisher; each subscription executes a new query.
   * @throws QueryException (from the Mono) if the server response indicates an error occurred.
   * @throws InvalidCredentialException (from the Mono) if the server rejects the user credentials.
   * @throws InsightsTimeoutException (from the Mono) if the query does not complete before the timeout expires.
   * @throws RejectedExecutionException (from the Mono) if the query scheduler's task capacity is exceeded.
   * @see ReactorOptions#subscriptionGracePeriod(Duration)
   */
  Mono<ReactorQueryResult> executeQuery(String statement, Consumer<QueryOptions> options);
}
