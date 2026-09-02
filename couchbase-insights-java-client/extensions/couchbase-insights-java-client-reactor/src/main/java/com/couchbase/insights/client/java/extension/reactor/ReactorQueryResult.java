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
import com.couchbase.insights.client.java.Row;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

public interface ReactorQueryResult {
  /**
   * Produces the result rows. May not have more than one subscriber.
   * <p>
   * If the subscription grace period elapses before a subscriber arrives,
   * this Flux is automatically canceled.
   * <p>
   * When this Flux is canceled, the {@link #metadata()} Mono gets canceled too.
   * This can happen if a downstream operator like {@code take(1)} completes
   * before all rows are received from the server.
   *
   * @throws java.util.concurrent.CancellationException (from the Flux) if the query is cancelled.
   * @see ReactorOptions#subscriptionGracePeriod(Duration)
   */
  Flux<Row> rows();

  /**
   * Produces the query metadata.
   * <p>
   * <b>Caution:</b> This Mono does not complete until all result rows
   * have been received from the server. You SHOULD NOT block on the metadata Mono
   * unless you've already subscribed to the {@link #rows} Flux.
   * Otherwise, backpressure from unconsumed rows can halt the flow of data
   * from the server, preventing the metadata Mono from completing.
   *
   * @throws java.util.concurrent.CancellationException (from the Mono) if the query is cancelled.
   * Be aware that if an operator downstream of the {@code rows()} Flux completes without consuming all rows,
   * the row Flux is implicitly canceled, which causes the metadata Mono to be canceled too.
   */
  Mono<QueryMetadata> metadata();
}
