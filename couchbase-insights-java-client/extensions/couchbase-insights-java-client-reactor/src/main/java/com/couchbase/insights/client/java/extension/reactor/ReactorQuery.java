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

import com.couchbase.insights.client.java.QueryMetadata;
import com.couchbase.insights.client.java.QueryOptions;
import com.couchbase.insights.client.java.Queryable;
import com.couchbase.insights.client.java.Row;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;
import static reactor.core.publisher.Sinks.EmitResult.FAIL_CANCELLED;
import static reactor.core.publisher.Sinks.EmitResult.FAIL_TERMINATED;
import static reactor.core.publisher.Sinks.EmitResult.OK;

class ReactorQuery {
  private static final Logger log = LoggerFactory.getLogger(ReactorQuery.class);

  private final AtomicBoolean alreadyExecuted = new AtomicBoolean(false);

  private final Queryable clusterOrScope;
  private final String statement;
  private final Consumer<QueryOptions> options;

  private final Scheduler queryScheduler;
  private final Scheduler auxiliaryScheduler;
  private final Duration subscriptionGracePeriod;

  private final DisposableHolder queryExecutionSubscription = new DisposableHolder();
  private final DisposableHolder rowSubscriptionTimeoutEnforcer = new DisposableHolder();

  private final AtomicReference<@Nullable String> cancellationReason = new AtomicReference<>();

  // Can use `unsafe` because only the query thread publishes to the sinks.
  private final Sinks.One<ReactorQueryResult> resultSink = Sinks.unsafe().one();
  private final Sinks.One<QueryMetadata> metadataSink = Sinks.unsafe().one();
  private final Sinks.Many<Row> rowSink = Sinks.unsafe().many().unicast()
    .onBackpressureBuffer(new BlockingBuffer<>(64));

  private final ReactorQueryResult queryResult = new ReactorQueryResult() {
    @Override
    public Flux<Row> rows() {
      return rowSink.asFlux()
        // Propagate cancellation to the query execution task in case it's blocked
        // offering a row to the overflow buffer and needs to be interrupted.
        .doOnCancel(() -> cancelQueryExecution("the row subscription was canceled"))
        .doOnSubscribe(it -> rowSubscriptionTimeoutEnforcer.dispose()); // defuse the timeout
    }

    @Override
    public Mono<QueryMetadata> metadata() {
      return metadataSink.asMono();
    }
  };

  ReactorQuery(
    Queryable clusterOrScope,
    String statement,
    Consumer<QueryOptions> options,
    ReactorOptions.Unmodifiable reactorOptions
  ) {
    this.clusterOrScope = requireNonNull(clusterOrScope);
    this.statement = requireNonNull(statement);
    this.options = requireNonNull(options);
    this.queryScheduler = reactorOptions.queryScheduler();
    this.auxiliaryScheduler = reactorOptions.auxiliaryScheduler();
    this.subscriptionGracePeriod = reactorOptions.subscriptionGracePeriod();
  }

  private void cancelQueryExecution(String reason) {
    log.trace("Cancelling query execution; reason = {}", reason);
    cancellationReason.compareAndSet(null, reason);
    queryExecutionSubscription.dispose();
  }

  private void requireBlockableThread() {
    if (Schedulers.isInNonBlockingThread()) {
      throw new IllegalStateException(
        "Query could not be executed because Schedulers.isInNonBlockingThread() says the configured 'queryScheduler'" +
          " (" + queryScheduler + ") does not run tasks in a blockable context." +
          " Worker thread = " + Thread.currentThread()
      );
    }
  }

  Mono<ReactorQueryResult> execute() {
    if (alreadyExecuted.getAndSet(true)) {
      throw new IllegalStateException("This method may only be called once.");
    }

    Disposable querySubscription = Mono.fromRunnable(() -> {
        try {
          requireBlockableThread();

          MutableBoolean isFirstRow = new MutableBoolean(true);

          QueryMetadata metadata = clusterOrScope.executeStreamingQuery(
            statement,
            row -> {
              if (isFirstRow.getAndSet(false)) {
                requireOk(resultSink.tryEmitValue(queryResult), "query");
                scheduleRowSubscriptionTimeout();
              }
              requireOk(rowSink.tryEmitNext(row), "row");
            },
            options
          );

          if (isFirstRow.get()) {
            // Query returned no rows, but was successful.
            requireOk(resultSink.tryEmitValue(queryResult), "query");
          }

          requireOk(rowSink.tryEmitComplete(), "row");
          requireOk(metadataSink.tryEmitValue(metadata), "metadata");

          log.trace("Query execution successful.");

        } catch (Throwable t) {
          handleQueryExecutionFailure(t);
        }
      })
      .subscribeOn(queryScheduler) // We need to block, and need cancellation to interrupt the thread.
      .subscribe(
        ignore -> {
          // This Mono does not emit a value.
        },
        this::handleQueryExecutionFailure // Handles RejectedExecutionException from overloaded scheduler.
      );

    queryExecutionSubscription.set(querySubscription);

    return resultSink.asMono()
      .doOnCancel(() -> cancelQueryExecution("the query result subscription was canceled."));
  }

  private void scheduleRowSubscriptionTimeout() {
    // If there's already a row subscriber, skip timeout enforcement so there's no possibility
    // of a timeout being triggered due to a too-short grace period.
    rowSubscriptionTimeoutEnforcer.setIfNotAlreadyDisposed(() ->
      auxiliaryScheduler
        .schedule(
          () -> cancelQueryExecution("there was no row subscriber within the grace period of " + subscriptionGracePeriod + "."),
          subscriptionGracePeriod.toMillis(),
          TimeUnit.MILLISECONDS
        )
    );
  }

  private void handleQueryExecutionFailure(Throwable t) {
    if (log.isTraceEnabled()) {
      log.trace("Query execution failed or was cancelled before all rows were processed. Cancellation reason: {}", cancellationReason.get(), t);
    } else {
      log.debug("Query execution failed or was cancelled before all rows were processed. Cancellation reason: {}", cancellationReason.get());
    }

    if (t instanceof CancellationException) {
      String reason = cancellationReason.get();
      String message = "Query was canceled" + (reason == null ? "." : " because " + reason);
      t = newCancellationException(message, t);
    }

    emitErrorQuietly(resultSink, t, "result");
    emitErrorQuietly(metadataSink, t, "metadata");
    emitErrorQuietly(rowSink, t, "row");
  }

  private static CancellationException newCancellationException(String message, Throwable cause) {
    CancellationException e = new CancellationException(message);
    e.initCause(cause);
    return e;
  }

  private static void requireOk(Sinks.EmitResult r, String sinkDescription) {
    if (r != OK) {
      throw r == FAIL_CANCELLED
        ? new CancellationException("Could not emit value to " + sinkDescription + " sink because it was cancelled.")
        : new RuntimeException("Failed to publish to " + sinkDescription + " sink. EmitResult was: " + r);
    }
  }

  private static <T> void emitErrorQuietly(Sinks.One<T> sink, Throwable t, String sinkDescription) {
    checkEmitErrorQuietlyResult(sink.tryEmitError(t), t, sinkDescription);
  }

  private static <T> void emitErrorQuietly(Sinks.Many<T> sink, Throwable t, String sinkDescription) {
    checkEmitErrorQuietlyResult(sink.tryEmitError(t), t, sinkDescription);
  }

  private static void checkEmitErrorQuietlyResult(Sinks.EmitResult r, Throwable t, String name) {
    if (r != OK && r != FAIL_CANCELLED && r != FAIL_TERMINATED) {
      // This would be completely unexpected. All other results are unrelated
      // emitting errors, or not applicable to unsafe/unicast sinks.
      log.error("Unexpected result when emitting error to {} sink: {}", name, r, t);
    }
  }
}
