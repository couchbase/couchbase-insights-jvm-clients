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

import org.jspecify.annotations.Nullable;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Optional;

/**
 * For configuring {@link ReactorQueryable}.
 * <p>
 * Example usage:
 * <pre>
 * var reactor = ReactorQueryable.from(
 *     clusterOrScope,
 *     options -> options
 *         .queryScheduler(myCustomQueryScheduler())
 *         .auxiliaryScheduler(myCustomAuxScheduler())
 *         .subscriptionGracePeriod(Duration.ofSeconds(20))
 * );
 * </pre>
 */
public final class ReactorOptions {
  ReactorOptions() {
  }

  private @Nullable Scheduler queryScheduler;
  private @Nullable Scheduler auxiliaryScheduler;
  private @Nullable Duration subscriptionGracePeriod;

  // so we can reference it using a Javadoc {@value} tag
  private static final int DEFAULT_SUBSCRIPTION_GRACE_PERIOD_SECONDS = 30;

  Unmodifiable build() {
    return new Unmodifiable(this);
  }

  /**
   * If there is no subscriber for {@link ReactorQueryResult#rows()} within this duration,
   * the {@code row()} and {@code metadata()} publishers are automatically canceled
   * to prevent resource leaks.
   * <p>
   * The timer starts when the Mono returned by {@link ReactorQueryable#executeQuery} completes.
   * <p>
   * Defaults to {@value DEFAULT_SUBSCRIPTION_GRACE_PERIOD_SECONDS} seconds.
   *
   * @param subscriptionGracePeriod the grace period to use, or null for the default.
   * @throws IllegalArgumentException if duration is non-positive.
   */
  public ReactorOptions subscriptionGracePeriod(@Nullable Duration subscriptionGracePeriod) {
    this.subscriptionGracePeriod = requireNullOrPositive(subscriptionGracePeriod, "subscriptionGracePeriod");
    return this;
  }

  /**
   * The scheduler to use for executing queries. It must be friendly to blocking,
   * and must respond to task cancellation by interrupting the worker thread.
   * Its capacity should be sized to accommodate the expected number of concurrent
   * long-running queries.
   * <p>
   * Compatible schedulers include variants of bounded elastic, and schedulers created from executors.
   * <p>
   * Defaults to {@link Schedulers#boundedElastic()}, whose default capacity is
   * {@link Schedulers#DEFAULT_BOUNDED_ELASTIC_SIZE}.
   *
   * @param queryScheduler the scheduler to use, or null for the default.
   * @throws IllegalArgumentException if scheduler is not suitable.
   * Note that some suitability checks may be deferred until a query is executed.
   */
  public ReactorOptions queryScheduler(@Nullable Scheduler queryScheduler) {
    this.queryScheduler = requireNullOrNotImmediate(queryScheduler, "queryScheduler");
    return this;
  }

  /**
   * The scheduler to use for quick non-blocking housekeeping tasks,
   * like enforcing the subscription grace period.
   * <p>
   * Defaults to {@link Schedulers#parallel()}.
   *
   * @param auxiliaryScheduler the scheduler to use, or null for the default.
   * @throws IllegalArgumentException if scheduler is not suitable.
   */
  public ReactorOptions auxiliaryScheduler(@Nullable Scheduler auxiliaryScheduler) {
    this.auxiliaryScheduler = requireNullOrNotImmediate(auxiliaryScheduler, "auxiliaryScheduler");
    return this;
  }

  static class Unmodifiable {
    private final Scheduler queryScheduler;
    private final Scheduler auxiliaryScheduler;
    private final Duration subscriptionGracePeriod;

    private Unmodifiable(ReactorOptions builder) {
      this.queryScheduler = Optional.ofNullable(builder.queryScheduler).orElseGet(Schedulers::boundedElastic);
      this.auxiliaryScheduler = Optional.ofNullable(builder.auxiliaryScheduler).orElseGet(Schedulers::parallel);
      this.subscriptionGracePeriod = Optional.ofNullable(builder.subscriptionGracePeriod).orElse(Duration.ofSeconds(DEFAULT_SUBSCRIPTION_GRACE_PERIOD_SECONDS));
    }

    public Duration subscriptionGracePeriod() {
      return subscriptionGracePeriod;
    }

    public Scheduler queryScheduler() {
      return queryScheduler;
    }

    public Scheduler auxiliaryScheduler() {
      return auxiliaryScheduler;
    }

    @Override
    public String toString() {
      return "ReactorOptions{" +
        "queryScheduler=" + queryScheduler +
        ", auxiliaryScheduler=" + auxiliaryScheduler +
        ", subscriptionGracePeriod=" + subscriptionGracePeriod +
        '}';
    }
  }

  private static @Nullable Scheduler requireNullOrNotImmediate(@Nullable Scheduler scheduler, String name) {
    if (scheduler == Schedulers.immediate()) {
      throw new IllegalArgumentException(name + " must not be Schedulers.immediate().");
    }
    return scheduler;
  }

  private static @Nullable Duration requireNullOrPositive(@Nullable Duration d, String name) {
    if (d != null && (d.isNegative() || d.isZero())) {
      throw new IllegalArgumentException(name + " must be positive, but got: " + d);
    }
    return d;
  }
}
