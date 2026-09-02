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

import com.couchbase.insights.client.java.internal.RawQueryMetadata;
import com.couchbase.insights.client.java.internal.ThreadSafe;
import okhttp3.Request;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

@ThreadSafe
abstract class AbstractRowSource implements RowSource {
  private final String requestId;
  private final long count;
  private final String path;
  private final Supplier<QueryExecutor> queryExecutorSupplier;

  AbstractRowSource(
    String requestId,
    long count,
    String path,
    Supplier<QueryExecutor> queryExecutorSupplier
  ) {
    this.requestId = requireNonNull(requestId);
    this.count = count;
    this.path = requireNonNull(path);
    this.queryExecutorSupplier = requireNonNull(queryExecutorSupplier);
  }

  @Override
  public long rowCount() {
    return count;
  }

  @Override
  public QueryMetadata streamRows(
    Consumer<Row> rowAction,
    Consumer<RowOptions> options
  ) {
    QueryExecutor executor = queryExecutor();

    RowOptions rowOptions = new RowOptions();
    options.accept(rowOptions);
    RowOptions.Unmodifiable opts = rowOptions.build(executor.clusterOptions());

    Request.Builder requestBuilder = executor.requestForPath(path);
    RawQueryMetadata rawQueryMetadata = executor.executeStreamingQueryWithRetry(
      requestBuilder,
      opts,
      rowAction
    );

    rawQueryMetadata.requestId = this.requestId;
    return new QueryMetadata(rawQueryMetadata);
  }

  QueryExecutor queryExecutor() {
    return queryExecutorSupplier.get();
  }
}
