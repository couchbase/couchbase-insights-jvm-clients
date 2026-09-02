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

package com.couchbase.insights.client.java;

import com.couchbase.insights.client.java.codec.Deserializer;
import com.couchbase.insights.client.java.internal.InternalJacksonSerDes;
import com.couchbase.insights.client.java.internal.RawQueryMetadata;
import com.couchbase.insights.client.java.internal.utils.json.Mapper;
import com.couchbase.insights.client.java.internal.utils.lang.HeadInterceptInputStream;
import com.couchbase.insights.client.java.internal.utils.time.Deadline;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.couchbase.insights.client.java.internal.utils.lang.CbStrings.isNullOrEmpty;
import static com.couchbase.insights.client.java.internal.utils.lang.CbStrings.removeStart;
import static com.couchbase.insights.client.java.internal.utils.lang.CbThrowables.hasCause;
import static com.couchbase.insights.client.java.internal.utils.time.GolangDuration.encodeDurationToMs;
import static java.util.Collections.emptyMap;
import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

class QueryExecutor {
  private static final Logger log = LoggerFactory.getLogger(QueryExecutor.class);

  enum Mode {
    SYNC, // executeQuery
    ASYNC, // startQuery
  }

  private static final BackoffCalculator backoff = new BackoffCalculator(
    Duration.ofMillis(100),
    Duration.ofMinutes(1)
  );

  private final String userAgent;
  private final AnalyticsOkHttpClient httpClient;
  final HttpUrl baseUrl;
  final HttpUrl apiV1RequestUrl;
  private final ClusterOptions.Unmodifiable clusterOptions;
  private final boolean maybeCouchbaseInternalNonProd;

  public QueryExecutor(
    AnalyticsOkHttpClient httpClient,
    HttpUrl baseUrl,
    ClusterOptions.Unmodifiable clusterOptions,
    String userAgent
  ) {
    this.httpClient = requireNonNull(httpClient);
    this.baseUrl = requireNonNull(baseUrl);
    this.apiV1RequestUrl = baseUrl.newBuilder()
      .addPathSegment("api")
      .addPathSegment("v1")
      .addPathSegment("request")
      .build();

    this.clusterOptions = requireNonNull(clusterOptions);

    this.maybeCouchbaseInternalNonProd = baseUrl.host().endsWith(".nonprod-project-avengers.com");
    this.userAgent = requireNonNull(userAgent);
  }

  public QueryResult executeQuery(
    @Nullable QueryContext queryContext,
    String statement,
    Consumer<QueryOptions> options
  ) {
    QueryOptions.Unmodifiable opts = QueryOptions.configure(clusterOptions, options);

    List<Row> rows = new ArrayList<>();
    RawQueryMetadata raw = executeStreamingQueryWithRetry(
      queryContext,
      Mode.SYNC,
      statement,
      rows::add,
      opts,
      opts.deserializer()
    );
    return new QueryResult(rows, new QueryMetadata(raw));
  }

  private static final Consumer<Row> expectNoRows = row -> {
    throw new InsightsException("Did not expect to receive a row when starting async query");
  };

  ClusterOptions.Unmodifiable clusterOptions() {
    return clusterOptions;
  }

  Request.Builder requestForPath(String encodedPath) {
    encodedPath = removeStart(encodedPath, "/"); // prevent empty path segment at start
    HttpUrl url = baseUrl.newBuilder()
      .addEncodedPathSegments(encodedPath)
      .build();

    return new Request.Builder()
      .url(url);
  }

  public QueryHandle startQuery(
    Supplier<QueryExecutor> executorSupplier,
    @Nullable QueryContext queryContext,
    String statement,
    Consumer<StartQueryOptions> options
  ) {
    StartQueryOptions.Unmodifiable opts = StartQueryOptions.configure(clusterOptions, options);

    RawQueryMetadata raw = executeStreamingQueryWithRetry(
      queryContext,
      Mode.ASYNC,
      statement,
      expectNoRows,
      opts,
      InternalJacksonSerDes.INSTANCE // dummy
    );
    if (raw.handle == null) {
      throw new InsightsException("Server response was missing 'handle' field for query handle.");
    }
    if (raw.requestId == null) {
      throw new InsightsException("Server response was missing 'requestID' field for query handle.");
    }
    return new QueryHandle(executorSupplier, raw.requestId, raw.handle);
  }

  public void discard(String path) {
    Request.Builder requestBuilder = requestForPath(path)
      .delete();

    try {
      executeStreamingQueryWithRetry(
        requestBuilder,
        rowOptionsForSimpleHandleRequest(),
        expectNoRows
      );
    } catch (QueryNotFoundException ignore) {
      log.debug("Discard failed for non-existent query {}", path);
    }
  }

  private RowOptions.Unmodifiable rowOptionsForSimpleHandleRequest() {
    RowOptions options = new RowOptions()
      .timeout(clusterOptions.timeout().handleRequestTimeout());
    return options.build(clusterOptions);
  }

  public void cancel(String requestId) {
    HttpUrl url = baseUrl.newBuilder()
      .addPathSegment("api")
      .addPathSegment("v1")
      .addPathSegment("active_requests")
      .build();

    RequestBody body = new FormBody.Builder()
      .add("request_id", requestId)
      .build();

    Request.Builder requestBuilder = new Request.Builder()
      .url(url)
      .delete(body);

    try {
      executeStreamingQueryWithRetry(
        requestBuilder,
        rowOptionsForSimpleHandleRequest(),
        expectNoRows
      );
    } catch (QueryNotFoundException ignore) {
      log.debug("Cancel failed for non-existent query {}", requestId);
    }
  }

  public StatusResponse getStatus(QueryHandle queryHandle, Duration requestTimeout) {
    Request.Builder requestBuilder = requestForPath(queryHandle.handle);

    try (Response response = executeRaw(
      requestBuilder.build(),
      requestTimeout
    )) {
      if (response.code() == 404) {
        throw QueryNotFoundException.forHandle(queryHandle);
      }

      try (ResponseBody responseBody = response.body()) {
        if (!response.isSuccessful()) {
          String body = responseBody == null ? "" : responseBody.string();
          if (body.isEmpty()) {
            body = "<empty>";
          }
          throw new InsightsException("Query handle poll failed. Server said: " + response.code() + " " + response.message() + " ; body=" + body);
        }

        if (isNull(responseBody)) {
          throw new InsightsException("Query status response body was null.");
        }

        String body = responseBody.string();
        return Mapper.readValue(body, StatusResponse.class);

      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    } catch (UncheckedIOException e) {
      throw new InsightsException("Failed to get query handle status for handle: " + queryHandle.toSerialized(), e);
    }
  }

  /**
   * This exception's cause is an exception throw by the user's row action callback,
   * which we want to rethrow verbatim.
   * <p>
   * Exists to differentiate a user exception from a JSON stream parser failure
   * which we might want to transform into some other exception.
   */
  private static final class RowActionException extends RuntimeException {
    public RowActionException(Throwable cause) {
      super(cause);
    }
  }

  <T> T doWithRetry(
    Duration timeout,
    int maxRetries,
    Function<Duration, T> action // first param is time remaining
  ) {
    Deadline retryDeadline = Deadline.of(timeout);
    QueryException prevException = null;
    try {
      for (int attempt = 0; attempt >= 0 && attempt <= maxRetries; attempt++) {
        try {
          return action.apply(timeout);

        } catch (QueryException e) {
          if (!e.errorCodeAndMessage.retry()) {
            throw e;
          }

          prevException = e;

          Duration backoffDelay = backoff.delayForAttempt(attempt);
          if (!retryDeadline.hasRemaining(backoffDelay)) {
            throw new InsightsTimeoutException(
              "Declaring timeout early because sleeping for backoff delay would exceed timeout deadline."
            );
          }

          log.debug("Query execution failed with error code {}; will retry in {}", e.errorCodeAndMessage, backoffDelay);
          sleep(backoffDelay);

          timeout = retryDeadline.remaining().orElseThrow(() ->
            new InsightsTimeoutException("Query execution did not complete within timeout")
          );
        }
      }

    } catch (RuntimeException e) {
      if (prevException != null) {
        e.addSuppressed(prevException);
      }
      throw e;
    }

    requireNonNull(prevException, "should not be here with null prevException");
    if (maxRetries > 0) {
      prevException.addSuppressed(newRuntimeExceptionWithoutStackTrace("Retries exhausted: " + maxRetries));
    }
    throw prevException;
  }

  public RawQueryMetadata executeStreamingQueryWithRetry(
    Request.Builder requestBuilder,
    RowOptions.Unmodifiable options,
    Consumer<Row> rowAction
  ) {
    return doWithRetry(
      options.timeout(),
      options.maxRetries(),
      timeRemaining -> executeStreamingQueryOnce(
        requestBuilder,
        timeRemaining,
        rowAction,
        options.deserializer()
      )
    );
  }

  public RawQueryMetadata executeStreamingQueryWithRetry(
    @Nullable QueryContext queryContext,
    Mode mode,
    String statement,
    Consumer<Row> rowAction,
    CommonQueryOptions.Unmodifiable opts,
    Deserializer deserializer
  ) {
    Duration queryTimeout = opts.timeout(); // server-side timeout

    // The call timeout is shorter in async mode (startQuery) because we don't need to wait for query execution to complete.
    Duration callTimeout = mode == Mode.ASYNC
      ? clusterOptions.timeout().handleRequestTimeout()
      : queryTimeout;

    return doWithRetry(
      callTimeout,
      opts.maxRetries(),
      timeRemaining -> executeStreamingQueryOnce(
        queryContext,
        mode,
        statement,
        rowAction,
        opts,
        deserializer,
        // For async queries, there is no relationship between the call timeout and the
        // server-side query timeout, so pass the full query timeout on every attempt.
        // For synchronous queries on the other hand, the retry backoff time eats into the total query time.
        mode == Mode.ASYNC ? queryTimeout : timeRemaining,
        timeRemaining
      )
    );
  }

  private static RuntimeException newRuntimeExceptionWithoutStackTrace(String message) {
    return new RuntimeException(message) {
      @Override
      public synchronized Throwable fillInStackTrace() {
        return this;
      }
    };
  }

  static void sleep(Duration d) {
    try {
      MILLISECONDS.sleep(d.toMillis());
    } catch (InterruptedException e) {
      throw propagateInterruption(e);
    }
  }

  RawQueryMetadata executeStreamingQueryOnce(
    @Nullable QueryContext queryContext,
    Mode mode,
    String statement,
    Consumer<Row> rowAction,
    CommonQueryOptions.Unmodifiable opts,
    Deserializer deserializer,
    Duration serverTimeout,
    Duration callTimeout
  ) {
    requireNonNull(statement, "statement cannot be null");

    // Tip the scales in favor of the user getting a client-enforced timeout
    // (surfaced as InsightsTimeoutException) instead of a server-enforced timeout
    // (surfaced as QueryTimeout).
    //
    // This was arguably a mistake; it might be better to tip the scales the other way
    // so the user is more likely to get an unambiguous signal when the query times out
    // on the server, as opposed to the current ambiguous `InsightsTimeoutException`
    // which could also indicate a network issue between the client and server.
    serverTimeout = serverTimeout.plus(Duration.ofSeconds(5));

    ObjectNode query = JsonNodeFactory.instance.objectNode()
      .put("statement", statement)
      .put("timeout", encodeDurationToMs(serverTimeout));

    if (mode == Mode.ASYNC) {
      query.put("mode", "async");
    }

    if (queryContext != null) {
      query.put("query_context", queryContext.format());
    }

    opts.injectParams(query);

    Request.Builder requestBuilder = new Request.Builder()
      .url(apiV1RequestUrl)
      .post(requestBody(query));

    return executeStreamingQueryOnce(
      requestBuilder,
      callTimeout,
      rowAction,
      deserializer
    );
  }

  Response executeRaw(
    Request request,
    Duration timeout
  ) {
    OkHttpClient client = httpClient.clientWithTimeout(timeout);
    Call call = client.newCall(request);

    try {
      return AnalyticsOkHttpClient.executeInterruptibly(call);

    } catch (SSLHandshakeException e) {
      throw new InsightsException(tlsHandshakeErrorMessage(e), e);

    } catch (IOException e) {
      if (Thread.currentThread().isInterrupted()) {
        throw propagateInterruption(e);
      }

      if (hasCause(e, SocketTimeoutException.class) || hasCause(e, InterruptedIOException.class)) {
        throw new InsightsTimeoutException(e);
      }

      throw new UncheckedIOException(e);
    }
  }

  RawQueryMetadata executeStreamingQueryOnce(
    Request.Builder requestBuilder,
    Duration callTimeout,
    Consumer<Row> rowAction,
    Deserializer deserializer
  ) {
    Request request = requestBuilder
      .header("User-Agent", userAgent)
      .build();

    OkHttpClient client = httpClient.clientWithTimeout(callTimeout);
    Call call = client.newCall(request);

    boolean allowConnectionReuse = false;

    try (
      AnalyticsResponseParser parser = new AnalyticsResponseParser(rowBytes -> {
        try {
          rowAction.accept(new Row(rowBytes, deserializer));
        } catch (RuntimeException e) {
          // Wrap to distinguish from other parse exceptions.
          throw new RowActionException(e);
        }
      });
      Response response = AnalyticsOkHttpClient.executeInterruptibly(call);
      ResponseBody body = response.body()
    ) {
      String httpStatusMessage = "HTTP response status: " + statusLine(response);

      if (response.code() == 401) {
        throw new InvalidCredentialException(httpStatusMessage);
      }

      if (response.code() == 404) {
        throw new QueryNotFoundException(
          "Server said 404 Not Found." +
            " This could mean the connection string does not point to a Couchbase Operational Insights cluster," +
            " or it could mean the query result you are referencing has expired or been discarded.");
      }

      if (body == null) {
        maybeThrowSyntheticServiceNotAvailable(response, null);
        throw new InsightsException("HTTP response had no body; this is unexpected! " + httpStatusMessage);
      }

      HeadInterceptInputStream bodyInterceptor = null; // cache the start of the response body so we can access it later
      try (HeadInterceptInputStream is = new HeadInterceptInputStream(body.byteStream(), 4 * 1024)) {
        bodyInterceptor = is;
        parser.feed(is);
        parser.endOfInput();
        allowConnectionReuse = true; // success!

      } catch (RowActionException e) {
        // Catch separately in case the user's row action threw UncheckedIOException!
        allowConnectionReuse = true; // the failure wasn't due to a degraded node or dead connection.
        throw (RuntimeException) e.getCause();

      } catch (UncheckedIOException e) {
        // Thread was interrupted, or got a non-blank non-JSON response.
        if (Thread.currentThread().isInterrupted()) {
          throw propagateInterruption(e);
        }
        String head = bodyInterceptor == null ? "<unknown>" : bodyInterceptor.getHeadAsString();
        throw new InsightsException("Query response parsing failed due to " + e + " ; " + httpStatusMessage + " ; response body = " + head, e);
      }

      if (request.method().equals("DELETE")) {
        if (!response.isSuccessful()) {
          throw new InsightsException("DELETE was not successful. " + httpStatusMessage);
        }

      } else if (parser.result.requestId == null && parser.result.createdAt == null) {
        String head = bodyInterceptor.getHeadAsString();
        maybeThrowSyntheticServiceNotAvailable(response, head);

        // Response wasn't a JSON Object with a "requestID" or "createdAt" field :-(
        throw new InsightsException("HTTP response body did not match expected query response format; did not have 'requestID' or 'createdAt' fields. Server said:" + httpStatusMessage + " ; response body = " + head);
      }

      return parser.result;

    } catch (SSLHandshakeException e) {
      throw new InsightsException(tlsHandshakeErrorMessage(e), e);

    } catch (IOException e) {
      if (Thread.currentThread().isInterrupted()) {
        throw propagateInterruption(e);
      }

      if (hasCause(e, SocketTimeoutException.class) || hasCause(e, InterruptedIOException.class)) {
        throw new InsightsTimeoutException(e);
      }

      throw new InsightsException(e);

    } finally {
      if (!allowConnectionReuse) {
        preventConnectionReuse();
      }
    }
  }

  /**
   * In some deployments, we might get HTTP status code 503 (Service Unavailable)
   * from an intermediary like a proxy or load balancer. In that case, throw a
   * {@link QueryException} with the same error code as if the response
   * had come from the Analytics cluster. This has two benefits:
   * <ul>
   * <li> It engages the SDK's automatic retry mechanism, since the synthetic
   * exception is marked as retriable.
   *
   * <li> It lets the user handle "service unavailable" the same way, regardless of
   * whether the error came from a load balancer or the analytics server.
   * </ul>
   */
  private static void maybeThrowSyntheticServiceNotAvailable(Response response, @Nullable String head) {
    if (response.code() == 503) {
      int analyticsServiceNotAvailable = 23000;
      String message = "Got HTTP status " + statusLine(response) + " -- but there was no analytics response body. This might indicate the HTTP response came from a proxy or load balancer.";
      message += " Response body = " + (isNullOrEmpty(head) ? "<empty>" : head);
      boolean retriable = true;
      throw new QueryException(new ErrorCodeAndMessage(analyticsServiceNotAvailable, message, retriable, emptyMap()));
    }
  }

  private void preventConnectionReuse() {
    // Can't mark just one connection as not reusable, so...
    log.debug("Clearing connection pool to avoid potentially reusing a connection to a degraded node.");
    httpClient.evictAll();
  }

  private String tlsHandshakeErrorMessage(Throwable tlsHandshakeError) {
    String message = "A TLS handshake problem prevented the client from connecting to the server." +
      " Potential causes include the server (or a proxy, or an on-path attacker)" +
      " presenting a certificate the client is not configured to trust." +
      " If connecting to a hosted service, make sure to use a relatively recent" +
      " SDK version that has up-to-date certificates." +
      " If connecting from inside a corporate network, make sure to configure the SDK" +
      " to trust the CA certificate of your proxy." +
      " Error message from the TLS engine: " + tlsHandshakeError;

    if (maybeCouchbaseInternalNonProd) {
      message = "It looks like you might be trying to connect to a Couchbase internal non-production hosted service." +
        " If this is true, please make sure you have configured the SDK to trust the non-prod certificate authority, like this:" +
        "\n\n" +
        "Cluster cluster = Cluster.newInstance(\n" +
        "  connectionString,\n" +
        "  Credential.of(username, password),\n" +
        "  clusterOptions -> clusterOptions\n" +
        "    .security(it -> it.trustOnlyCertificates(Certificates.getNonProdCertificates()))\n" +
        ");\n\n" +
        "We now return to your regularly scheduled exception message.\n\n"
        + message;
    }

    return message;
  }


  public static CancellationException propagateInterruption(Throwable cause) {
    Thread.currentThread().interrupt();
    CancellationException e = new CancellationException("Thread was interrupted.");
    e.initCause(cause);
    return e;
  }

  private static String statusLine(Response response) {
    return response.code() + " " + response.message();
  }

  private static RequestBody requestBody(JsonNode json) {
    return new JsonNodeRequestBody(json);
  }

  public void close() {
    httpClient.close();
  }

  static class JsonNodeRequestBody extends RequestBody {
    private static final MediaType APPLICATION_JSON = requireNonNull(MediaType.parse("application/json"));

    private final JsonNode json;

    public JsonNodeRequestBody(JsonNode json) {
      this.json = requireNonNull(json);
    }

    @Override
    public MediaType contentType() {
      return APPLICATION_JSON;
    }

    @Override
    public void writeTo(BufferedSink sink) {
      Mapper.writeValue(sink.outputStream(), json);
    }

    @Override
    public String toString() {
      return json.toString();
    }
  }
}
