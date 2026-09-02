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

package com.example.couchbase.insights.reactor;

import com.couchbase.insights.client.java.QueryMetadata;
import com.couchbase.insights.client.java.Row;
import com.couchbase.insights.client.java.extension.reactor.ReactorQueryResult;
import com.couchbase.insights.client.java.extension.reactor.ReactorQueryable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * Toy Data Access Object (DAO) used by {@link ReactorExample}.
 */
public class ExampleReactorDao {
  private static final Logger log = LoggerFactory.getLogger(ExampleReactorDao.class);

  private final ReactorQueryable reactor;

  public ExampleReactorDao(ReactorQueryable reactor) {
    this.reactor = requireNonNull(reactor);
  }

  /**
   * Asks the server what time it is.
   */
  public Mono<Instant> serverTime() {
    return reactor.executeQuery("SELECT RAW CLOCK_MILLIS()")
      .flatMap(result -> result
        .rows().single()
        .map(row -> row.as(Long.class))
        .map(Instant::ofEpochMilli)
      );
  }

  public Flux<Row> executeQueryAndLogMetadata(String statement) {
    return withMetadataCallbackAsSideEffect(
      reactor.executeQuery(statement),
      metadata -> log.info("Metadata for query [{}] = {}", statement, metadata)
    ).doOnCancel(() -> log.info("Metadata for query [{}] is not available because row Flux was canceled.", statement));
  }

  /**
   * Helper function that returns the given query's row Flux,
   * transformed so the given metadata callback is executed
   * as a side effect just before the returned Flux completes.
   * <p>
   * The callback is executed by the same scheduler that
   * publishes the rows.
   * <p>
   * If the callback throws an exception, the returned Flux
   * signals an error.
   * <p>
   * The callback is not executed if the Flux is cancelled,
   * for example by a downstream operator like {@code take(1)}
   * that does not consume all rows.
   *
   * @return a cold publisher; each subscription executes a new query.
   */
  public static Flux<Row> withMetadataCallbackAsSideEffect(
    Mono<ReactorQueryResult> query,
    Consumer<QueryMetadata> metadataCallback
  ) {
    return query.flatMapMany(result -> result
      .rows().concatWith(
        result.metadata()
          .doOnSuccess(metadataCallback)
          .then() // Don't emit anything.
          .cast(Row.class) // Empty Mono can be cast to any type.
      )
    );
  }
}
