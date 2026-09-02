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

import com.couchbase.insights.client.java.internal.ThreadSafe;
import com.couchbase.insights.client.java.internal.utils.json.Mapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.couchbase.insights.client.java.internal.utils.lang.CbCollections.mapOf;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

@ThreadSafe
public class QueryHandle {
  final String requestId;
  final String handle;

  // Can't just hold a reference to a QueryExecutor, because the cluster's query executor
  // changes whenever the user sets a new Credential.
  private final Supplier<QueryExecutor> queryExecutorSupplier;

  QueryHandle(
    Supplier<QueryExecutor> queryExecutorSupplier,
    String requestId,
    String handle
  ) {
    this.queryExecutorSupplier = requireNonNull(queryExecutorSupplier);
    this.requestId = requireNonNull(requestId);
    this.handle = requireNonNull(handle);
  }

  private QueryExecutor queryExecutor() {
    return queryExecutorSupplier.get();
  }

  /**
   * Returns the query's current status. Call this method to access the query result.
   *
   * @throws QueryException if the server response indicates an error occurred during query execution.
   * @throws InsightsTimeoutException if the server does not respond before the timeout.
   * @throws InsightsException if anything else went wrong while executing this request.
   */
  public QueryStatus fetchStatus() {
    return fetchStatus(options -> {
    });
  }

  /**
   * Returns the query's current status. Call this method to access the query result.
   */
  public QueryStatus fetchStatus(Consumer<FetchStatusOptions> options) {
    QueryExecutor queryExecutor = queryExecutor();

    FetchStatusOptions fetchResultHandleOptions = new FetchStatusOptions();
    options.accept(fetchResultHandleOptions);
    FetchStatusOptions.Unmodifiable opts = fetchResultHandleOptions.build(queryExecutor.clusterOptions());

    StatusResponse r = queryExecutor.getStatus(this, opts.timeout());
    if (!r.errors.isEmpty()) throw QueryException.from(r.errors);

    QueryResultHandle resultHandle = r.handle.isEmpty()
      ? null
      : new QueryResultHandle(requestId, queryExecutorSupplier, r);

    return new QueryStatus(
      r.status,
      new QueryMetrics(r.metrics),
      parseInstantOrNull(r.createdAt),
      resultHandle
    );
  }

  private static @Nullable Instant parseInstantOrNull(String instant) {
    try {
      return Instant.parse(instant);
    } catch (RuntimeException e) {
      return null;
    }
  }

  public void cancel() {
    queryExecutor().cancel(requestId);
  }

  /**
   * Returns the serialized form of this query handle, suitable for persistence or transmission to another process.
   * <p>
   * <b>Note:</b> Since this is currently an experimental API, the serialized form is not guaranteed to be stable across library versions.
   *
   * @see #fromSerialized(Cluster, String)
   */
  @ApiStatus.Experimental
  public String toSerialized() {
    return Mapper.writeValueAsString(mapOf(
      "requestID", requestId,
      "handle", handle
    ));
  }

  /**
   * @see #toSerialized()
   */
  @ApiStatus.Experimental
  public static QueryHandle fromSerialized(
    Cluster cluster,
    String serializedForm
  ) {
    requireNonNull(cluster);

    JsonNode node = Mapper.readTree(serializedForm.getBytes(UTF_8));
    String requestId = node.path("requestID").textValue();
    String handle = node.path("handle").textValue();
    if (requestId == null || handle == null) {
      throw new IllegalArgumentException("Expected serialized query handle to have 'requestID' and 'handle' fields, but got: " + serializedForm);
    }
    return new QueryHandle(() -> cluster.queryExecutor, requestId, handle);
  }

  @Override
  public String toString() {
    return toSerialized();
  }
}
