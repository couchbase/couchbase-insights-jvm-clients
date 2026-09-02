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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.couchbase.insights.fit.performer.query.PushBasedStreamer.toFit;

class ExistingBufferedStreamer extends Thread {
  private final Logger logger;
  private final BlockingQueue<QueryRowResponse> rows = new LinkedBlockingQueue<>(1);
  private final BlockingQueue<fit.columnar.EmptyResultOrFailureResponse> queryResult = new LinkedBlockingQueue<>(1);
  private final CompletableFuture<fit.columnar.QueryResultMetadataResponse> metadata = new CompletableFuture<>();
  private final AtomicBoolean rowsFinished = new AtomicBoolean(false);
  private final QueryResult result;
  private final String queryHandle;

  public ExistingBufferedStreamer(QueryResult result, String queryHandle) {
    this.logger = LoggerFactory.getLogger("Query " + queryHandle);
    this.result = result;
    this.queryHandle = queryHandle;
  }

  public fit.columnar.QueryResultMetadataResponse blockForMetadata() {
    try {
      return metadata.get(30, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      throw new RuntimeException(e.getCause());
    } catch (InterruptedException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  public void cancel() {
    logger.info("Cancelling query");
    interrupt();
  }

  @Override
  public void run() {
      logger.info("Starting buffered query from existing result on handle {}", queryHandle);

      try {
        // todo think about timings
        queryResult.put(ResultUtil.success(null));

        for (Row next : result.rows()) {
          // kludge: assume content as byte array
          var contentAs = ContentAs.newBuilder().setAsByteArray(true).build();
          var processed = QueryRowUtil.processRow(contentAs, next);
          // This will block until the driver pulls it
          rows.put(processed.row());
        }

        logger.info("Finished row iteration");
        rowsFinished.set(true);

        metadata.complete(fit.columnar.QueryResultMetadataResponse.newBuilder()
          .setSuccess(toFit(result.metadata()))
          .build());
      }
      catch (RuntimeException | InterruptedException err) {
        logger.warn("ExistingBufferedStreamer thread failed unexpectedly: " + err);
      }
  }

  public fit.columnar.QueryRowResponse blockForRow() {
    try {
      logger.info("Waiting for a row to be available");

      if (rowsFinished.get()) {
        if (!rows.isEmpty()) {
          return rows.take();
        }

        logger.info("Row iteration is complete");

        return fit.columnar.QueryRowResponse.newBuilder()
                .setSuccess(fit.columnar.QueryRowResponse.Result.newBuilder()
                        .setEndOfStream(true))
                .build();
      }

      return rows.take();
    } catch (InterruptedException e) {
      logger.info("Interrupted taking row!");
      throw new RuntimeException(e);
    }
  }

  public EmptyResultOrFailureResponse blockForQueryResult() {
    logger.info("Waiting for QueryResult available");

    try {
      return queryResult.take();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}

public class QueryResultExistingBufferedStreamer implements ExecuteQueryStreamer {
  private final String queryHandle;
  private final ExistingBufferedStreamer asyncExecuteQuery;

  public QueryResultExistingBufferedStreamer(QueryResult result, String queryHandle) {
    this.queryHandle = queryHandle;
    asyncExecuteQuery = new ExistingBufferedStreamer(result, queryHandle);
    asyncExecuteQuery.start();
  }

  @Override
  public EmptyResultOrFailureResponse blockForQueryResult() {
    return asyncExecuteQuery.blockForQueryResult();
  }

  public QueryRowResponse blockForRow() {
    return asyncExecuteQuery.blockForRow();
  }

  public QueryResultMetadataResponse blockForMetadata() {
    return asyncExecuteQuery.blockForMetadata();
  }

  public void cancel() {
    asyncExecuteQuery.cancel();
  }

  public String queryHandle() {
    return queryHandle;
  }
}
