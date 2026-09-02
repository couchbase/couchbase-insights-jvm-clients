/*
 * Copyright (c) 2024 Couchbase, Inc.
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

package com.couchbase.insights.fit.performer.rpc;

import com.couchbase.insights.client.java.QueryHandle;
import com.couchbase.insights.client.java.QueryResultHandle;
import com.couchbase.insights.client.java.QueryStatus;
import com.couchbase.insights.client.java.Queryable;
import com.couchbase.insights.fit.performer.modes.Mode;
import com.couchbase.insights.fit.performer.query.ExecuteQueryStreamer;
import com.couchbase.insights.fit.performer.query.QueryOptionsUtil;
import com.couchbase.insights.fit.performer.query.QueryResultBufferedStreamer;
import com.couchbase.insights.fit.performer.query.QueryResultExistingBufferedStreamer;
import com.couchbase.insights.fit.performer.query.QueryResultPushBasedStreamer;
import com.couchbase.insights.fit.performer.util.ErrorUtil;
import com.couchbase.insights.fit.performer.util.ResultUtil;
import fit.columnar.AsyncCancelHandleRequest;
import fit.columnar.AsyncDiscardResultsRequest;
import fit.columnar.AsyncFetchResultsRequest;
import fit.columnar.AsyncFetchStatusRequest;
import fit.columnar.AsyncFetchStatusResponse;
import fit.columnar.AsyncQueryStatusResultHandleRequest;
import fit.columnar.CloseAllQueryResultsRequest;
import fit.columnar.CloseQueryResultRequest;
import fit.columnar.ColumnarCrossServiceGrpc;
import fit.columnar.EmptyResultOrFailureResponse;
import fit.columnar.ExecuteQueryRequest;
import fit.columnar.ExecuteQueryResponse;
import fit.columnar.QueryCancelRequest;
import fit.columnar.QueryMetadataRequest;
import fit.columnar.QueryResultMetadataResponse;
import fit.columnar.QueryResultRequest;
import fit.columnar.QueryRowRequest;
import fit.columnar.QueryRowResponse;
import fit.columnar.StartQueryRequest;
import fit.columnar.StartQueryResponse;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.couchbase.insights.fit.performer.common.util.StartTimes.startTiming;

public class JavaAnalyticsCrossService extends ColumnarCrossServiceGrpc.ColumnarCrossServiceImplBase {
  private final static Logger LOGGER = LoggerFactory.getLogger(JavaAnalyticsCrossService.class);
  private final IntraServiceContext context;
  private final ConcurrentMap<String, ExecuteQueryStreamer> queryResultStreamers = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, QueryHandle> queryHandles = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, QueryStatus> queryStatuses = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, QueryResultHandle> queryResultHandles = new ConcurrentHashMap<>();

  public JavaAnalyticsCrossService(IntraServiceContext context) {
    this.context = context;
  }

  @Override
  public void executeQuery(ExecuteQueryRequest request, StreamObserver<ExecuteQueryResponse> responseObserver) {
    var startTime = startTiming();
    try {
      Queryable clusterOrScope;
      int modeIdx;

      if (request.hasClusterLevel()) {
        clusterOrScope = context.cluster(request.getClusterLevel()).cluster();
        modeIdx = request.getClusterLevel().getShared().getModeIndex();
      } else if (request.hasScopeLevel()) {
        clusterOrScope = context.scope(request.getScopeLevel());
        modeIdx = request.getScopeLevel().getShared().getModeIndex();
      } else {
        throw new UnsupportedOperationException();
      }

      ExecuteQueryStreamer streamer;
      if (modeIdx == Mode.PUSH_BASED_STREAMING.ordinal()) {
        streamer = new QueryResultPushBasedStreamer(clusterOrScope, request);
      } else {
        streamer = new QueryResultBufferedStreamer(clusterOrScope, request);
      }

      queryResultStreamers.put(streamer.queryHandle(), streamer);
      responseObserver.onNext(fit.columnar.ExecuteQueryResponse.newBuilder()
        .setMetadata(ResultUtil.responseMetadata(startTime))
        .setQueryHandle(streamer.queryHandle())
        .build());

    } catch (Throwable err) {
      LOGGER.warn("Failure during executeQuery: {}", err.toString());
    }
    responseObserver.onCompleted();
  }

  @Override
  public void startQuery(StartQueryRequest request, StreamObserver<StartQueryResponse> responseObserver) {
    try {
      Queryable clusterOrScope;

      if (request.hasClusterLevel()) {
        clusterOrScope = context.cluster(request.getClusterLevel()).cluster();
      } else if (request.hasScopeLevel()) {
        clusterOrScope = context.scope(request.getScopeLevel());
      } else {
        throw new UnsupportedOperationException();
      }

      var options = QueryOptionsUtil.convertStartQueryOptions(request);
      var handle = options != null
        ? clusterOrScope.startQuery(request.getStatement(), options)
        : clusterOrScope.startQuery(request.getStatement());

      var queryHandle = UUID.randomUUID().toString();
      queryHandles.put(queryHandle, handle);

      responseObserver.onNext(StartQueryResponse.newBuilder()
        .setQueryHandle(queryHandle)
        .build());

    } catch (Throwable err) {
      responseObserver.onNext(StartQueryResponse.newBuilder()
        .setFailure(ErrorUtil.convertError(err))
        .build());
    }
    responseObserver.onCompleted();
  }

  @Override
  public void asyncFetchStatus(AsyncFetchStatusRequest request, StreamObserver<AsyncFetchStatusResponse> responseObserver) {
    try {
      var handle = queryHandles.get(request.getQueryHandle());
      var status = handle.fetchStatus();
      queryStatuses.put(request.getQueryHandle(), status);

      responseObserver.onNext(AsyncFetchStatusResponse.newBuilder()
        .setQueryStatus(AsyncFetchStatusResponse.QueryStatusResult.newBuilder()
          .setResultsReady(status.resultReady())
          .setToString(status.toString())
          .build())
        .build());

    } catch (Throwable err) {
      responseObserver.onNext(AsyncFetchStatusResponse.newBuilder()
        .setFailure(ErrorUtil.convertError(err))
        .build());
    }
    responseObserver.onCompleted();
  }

  @Override
  public void asyncCancelHandle(AsyncCancelHandleRequest request, StreamObserver<EmptyResultOrFailureResponse> responseObserver) {
    var startTime = startTiming();
    try {
      var handle = queryHandles.get(request.getQueryHandle());
      handle.cancel();
      responseObserver.onNext(ResultUtil.success(startTime));
    } catch (Throwable err) {
      responseObserver.onNext(ResultUtil.failure(err, startTime));
    }
    responseObserver.onCompleted();
  }

  @Override
  public void asyncQueryStatusResultHandle(AsyncQueryStatusResultHandleRequest request, StreamObserver<EmptyResultOrFailureResponse> responseObserver) {
    var startTime = startTiming();
    try {
      var status = queryStatuses.get(request.getQueryHandle());
      var resultHandle = status.resultHandle();
      queryResultHandles.put(request.getQueryHandle(), resultHandle);
      responseObserver.onNext(ResultUtil.success(startTime));
    } catch (Throwable err) {
      responseObserver.onNext(ResultUtil.failure(err, startTime));
    }
    responseObserver.onCompleted();
  }

  @Override
  public void asyncFetchResults(AsyncFetchResultsRequest request, StreamObserver<EmptyResultOrFailureResponse> responseObserver) {
    var startTime = startTiming();
    try {
      var resultHandle = queryResultHandles.get(request.getQueryHandle());
      var options = QueryOptionsUtil.convertRowOptions(request);
      var queryResult = options != null ? resultHandle.bufferRows(options) : resultHandle.bufferRows();

      var streamer = new QueryResultExistingBufferedStreamer(queryResult, request.getQueryHandle());
      queryResultStreamers.put(request.getQueryHandle(), streamer);

      responseObserver.onNext(ResultUtil.success(startTime));
    } catch (Throwable err) {
      responseObserver.onNext(ResultUtil.failure(err, startTime));
    }
    responseObserver.onCompleted();
  }

  @Override
  public void asyncDiscardResults(AsyncDiscardResultsRequest request, StreamObserver<EmptyResultOrFailureResponse> responseObserver) {
    var startTime = startTiming();
    try {
      var resultHandle = queryResultHandles.get(request.getQueryHandle());
      resultHandle.discard();
      responseObserver.onNext(ResultUtil.success(startTime));
    } catch (Throwable err) {
      responseObserver.onNext(ResultUtil.failure(err, startTime));
    }
    responseObserver.onCompleted();
  }

  @Override
  public void queryResult(QueryResultRequest request, StreamObserver<EmptyResultOrFailureResponse> responseObserver) {
    var startTime = startTiming();
    try {
      var streamer = queryResultStreamers.get(request.getQueryHandle());
      responseObserver.onNext(streamer.blockForQueryResult());
    } catch (Throwable err) {
      responseObserver.onNext(ResultUtil.failure(err, startTime));
    }
    responseObserver.onCompleted();
  }

  @Override
  public void queryRow(QueryRowRequest request, StreamObserver<QueryRowResponse> responseObserver) {
    var startTime = startTiming();
    try {
      var streamer = queryResultStreamers.get(request.getQueryHandle());
      responseObserver.onNext(streamer.blockForRow());
    } catch (Throwable err) {
      responseObserver.onNext(fit.columnar.QueryRowResponse.newBuilder()
        .setMetadata(ResultUtil.responseMetadata(startTime))
        .setRowLevelFailure(ErrorUtil.convertError(err))
        .build());
    }
    responseObserver.onCompleted();
  }

  @Override
  public void queryCancel(QueryCancelRequest request, StreamObserver<EmptyResultOrFailureResponse> responseObserver) {
    var startTime = startTiming();
    try {
      var streamer = queryResultStreamers.get(request.getQueryHandle());
      streamer.cancel();
      responseObserver.onNext(ResultUtil.success(startTime));
    } catch (Throwable err) {
      responseObserver.onNext(ResultUtil.failure(err, startTime));
    }
    responseObserver.onCompleted();

  }

  @Override
  public void queryMetadata(QueryMetadataRequest request, StreamObserver<QueryResultMetadataResponse> responseObserver) {
    var startTime = startTiming();
    try {
      var streamer = queryResultStreamers.get(request.getQueryHandle());
      responseObserver.onNext(streamer.blockForMetadata());
    } catch (Throwable err) {
      responseObserver.onNext(fit.columnar.QueryResultMetadataResponse.newBuilder()
        .setMetadata(ResultUtil.responseMetadata(startTime))
        .setFailure(ErrorUtil.convertError(err))
        .build());
    }
    responseObserver.onCompleted();
  }

  @Override
  public void closeQueryResult(CloseQueryResultRequest request, StreamObserver<EmptyResultOrFailureResponse> responseObserver) {
    queryResultStreamers.remove(request.getQueryHandle());
    queryHandles.remove(request.getQueryHandle());
    queryStatuses.remove(request.getQueryHandle());
    queryResultHandles.remove(request.getQueryHandle());
    noop(responseObserver);
  }

  @Override
  public void closeAllQueryResults(CloseAllQueryResultsRequest request, StreamObserver<EmptyResultOrFailureResponse> responseObserver) {
    queryResultStreamers.clear();
    queryHandles.clear();
    queryStatuses.clear();
    queryResultHandles.clear();
    noop(responseObserver);
  }

  private static void noop(StreamObserver<EmptyResultOrFailureResponse> responseObserver) {
    responseObserver.onNext(ResultUtil.success(startTiming()));
    responseObserver.onCompleted();
  }
}
