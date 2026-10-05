/*
 * Copyright (c) 2026 Couchbase, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.couchbase.insights.fit.performer.query;

import com.couchbase.insights.client.java.QueryResult;
import com.couchbase.insights.client.java.Row;
import com.couchbase.insights.fit.performer.util.ResultUtil;
import fit.columnar.ContentAs;
import fit.columnar.EmptyResultOrFailureResponse;
import fit.columnar.QueryResultMetadataResponse;
import fit.columnar.QueryRowResponse;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;

import static com.couchbase.insights.fit.performer.query.PushBasedStreamer.toFit;

/**
 * Serves rows from a {@link QueryResult} that has already been fully buffered
 * (by {@code QueryResultHandle.bufferRows()} in the startQuery flow).
 * <p>
 * Rows are converted lazily, one per {@link #blockForRow} call, so each row can be
 * deserialized according to the {@code ContentAs} the driver sends with that row request.
 */
@NullMarked
public class QueryResultExistingBufferedStreamer implements ExecuteQueryStreamer {
  private final Logger logger;
  private final QueryResult result;
  private final String queryHandle;
  private final Iterator<Row> rows;

  public QueryResultExistingBufferedStreamer(QueryResult result, String queryHandle) {
    this.logger = LoggerFactory.getLogger("Query " + queryHandle);
    this.result = result;
    this.queryHandle = queryHandle;
    this.rows = result.rows().iterator();
  }

  @Override
  public EmptyResultOrFailureResponse blockForQueryResult() {
    // The QueryResult already exists; this was established by AsyncFetchResults.
    return ResultUtil.success(null);
  }

  @Override
  public synchronized QueryRowResponse blockForRow(@Nullable ContentAs contentAs) {
    if (!rows.hasNext()) {
      logger.info("Row iteration is complete");
      return QueryRowResponse.newBuilder()
        .setSuccess(QueryRowResponse.Result.newBuilder()
          .setEndOfStream(true))
        .build();
    }
    return QueryRowUtil.processRow(contentAs, rows.next()).row();
  }

  @Override
  public QueryResultMetadataResponse blockForMetadata() {
    return QueryResultMetadataResponse.newBuilder()
      .setSuccess(toFit(result.metadata()))
      .build();
  }

  @Override
  public void cancel() {
    // Nothing to cancel; all rows are already buffered in memory.
    logger.info("Cancel requested for already-buffered result; ignoring");
  }

  @Override
  public String queryHandle() {
    return queryHandle;
  }
}
