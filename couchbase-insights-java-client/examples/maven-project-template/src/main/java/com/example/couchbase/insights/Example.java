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

package com.example.couchbase.insights;

import com.couchbase.insights.client.java.Cluster;
import com.couchbase.insights.client.java.ClusterOptions;
import com.couchbase.insights.client.java.Credential;
import com.couchbase.insights.client.java.QueryHandle;
import com.couchbase.insights.client.java.QueryMetadata;
import com.couchbase.insights.client.java.QueryOptions;
import com.couchbase.insights.client.java.QueryResult;
import com.couchbase.insights.client.java.QueryResultHandle;
import com.couchbase.insights.client.java.QueryStatus;
import com.couchbase.insights.client.java.Queryable;
import com.couchbase.insights.client.java.Row;
import com.couchbase.insights.client.java.codec.Deserializer;
import com.couchbase.insights.client.java.extension.reactor.ReactorQueryResult;
import com.couchbase.insights.client.java.extension.reactor.ReactorQueryable;
import com.example.couchbase.insights.reactor.ReactorExample;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.SECONDS;

public class Example {
  public static void main(String[] args) throws Exception {
    var connectionString = "https://...";
    var username = "...";
    var password = "...";

    try (Cluster cluster = Cluster.newInstance(
      connectionString,
      Credential.of(username, password),
      // The third parameter is optional.
      // This example sets the default query timeout to 2 minutes.
      clusterOptions -> clusterOptions
        .timeout(it -> it.queryTimeout(Duration.ofMinutes(2)))
    )) {

      bufferedQueryExample(cluster);

      streamingQueryExample(cluster);

      dataBindingExample(cluster);

      nullRowExample(cluster);

      reactorQueryExample(cluster);

      queryHandleExample(cluster);
    }
  }

  /**
   * A "query handle" lets you execute a long-running query
   * in a way that is resilient against network interruptions.
   */
  static void queryHandleExample(Queryable clusterOrScope) throws InterruptedException, TimeoutException {
    String slowStatement = """
      SELECT COUNT (1) AS c
          FROM
          ARRAY_RANGE(0,10000) AS d1,
          ARRAY_RANGE(0,10000) AS d2
      """;

    Duration timeout = Duration.ofMinutes(15);

    QueryHandle queryHandle = clusterOrScope.startQuery(
      slowStatement,
      opt -> opt.timeout(timeout)
    );

    QueryResultHandle resultHandle = waitForResult(queryHandle, timeout);
    try {
      // Process rows one by one as they arrive from the server.
      QueryMetadata metadata = resultHandle.streamRows(row -> System.out.println("Got row: " + row));
      System.out.println("Got metadata: " + metadata);

      // Alternatively, if the result is known to fit in memory:
      QueryResult buffered = resultHandle.bufferRows();
      System.out.println("Got result: " + buffered);

    } finally {
      // Tell the server it can forget the result.
      resultHandle.discard();
    }
  }

  private static QueryResultHandle waitForResult(
    QueryHandle queryHandle,
    Duration timeout
  ) throws InterruptedException, TimeoutException {
    final long timeoutNanos = timeout.toNanos();
    final long startNanos = System.nanoTime();

    while (true) {
      QueryStatus status = queryHandle.fetchStatus();
      if (status.resultReady()) return status.resultHandle();

      System.out.println("Waiting for query to finish; current status: " + status);

      long elapsedNanos = System.nanoTime() - startNanos;
      if (elapsedNanos > timeoutNanos) {
        throw new TimeoutException("Query result not ready after " + timeout);
      }

      SECONDS.sleep(1); // or use exponential backoff
    }
  }

  /**
   * Executes a query, buffering all result rows in memory.
   */
  static void bufferedQueryExample(Queryable clusterOrScope) {
    QueryResult result = clusterOrScope.executeQuery(
      "select ?=1",
      options -> options
        .readOnly(true)
        .parameters(List.of(1))
    );
    result.rows().forEach(row -> System.out.println("Got row: " + row));
  }

  /**
   * Executes a query, processing rows one-by-one
   * as they arrive from server.
   */
  static void streamingQueryExample(Queryable clusterOrScope) {
    clusterOrScope.executeStreamingQuery(
      "select ?=1",
      row -> System.out.println("Got row: " + row),
      options -> options
        .readOnly(true)
        .parameters(List.of(1))
    );
  }

  /**
   * Converts a result row to a user-defined type using the default Jackson
   * {@link Deserializer}.
   *
   * @see ClusterOptions#deserializer(Deserializer)
   * @see QueryOptions#deserializer(Deserializer)
   */
  static void dataBindingExample(Queryable clusterOrScope) {
    record MyRowPojo(String greeting) {}

    QueryResult result = clusterOrScope.executeQuery(
      "SELECT 'hello world' AS greeting"
    );
    MyRowPojo resultRow = result.rows().getFirst().as(MyRowPojo.class);
    System.out.println(resultRow.greeting);
  }

  /**
   * Calls {@link Row#asNullable} because null is an expected result row value.
   */
  static void nullRowExample(Queryable clusterOrScope) {
    QueryResult result = clusterOrScope.executeQuery("SELECT RAW null");
    String nullableString = result.rows().getFirst().asNullable(String.class);
    System.out.println(nullableString);
  }

  /**
   * Executes a query using the optional Project Reactor extension library.
   * <p>
   * Requires adding {@code com.couchbase.client:couchbase-insights-java-client-reactor} as a dependency of your project.
   * <p>
   * See {@link ReactorExample} for more examples.
   */
  static void reactorQueryExample(Queryable clusterOrScope) {
    var reactor = ReactorQueryable.from(clusterOrScope);

    Flux<Row> resultRows = reactor.executeQuery("SELECT 1")
      .flatMapMany(ReactorQueryResult::rows);

    System.out.println(resultRows.blockLast());
  }
}
