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

import com.couchbase.insights.client.java.Cluster;
import com.couchbase.insights.client.java.Credential;
import com.couchbase.insights.client.java.Row;
import com.couchbase.insights.client.java.extension.reactor.ReactorQueryResult;
import com.couchbase.insights.client.java.extension.reactor.ReactorQueryable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * The examples in this class require adding the optional
 * {@code couchbase-insights-java-client-reactor}
 * extension library to your project as a dependency.
 * <p>
 * You can see that library's Maven coordinates in
 * this example project's pom.xml.
 */
public class ReactorExample {

  public static void main(String[] args) throws InterruptedException {
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

      // Start by wrapping a normal Cluster or Scope instance.
      var reactor = ReactorQueryable.from(cluster);

      // Now you can do reactive queries!
      Flux<Row> resultRows = reactor.executeQuery("SELECT 1")
        .flatMapMany(ReactorQueryResult::rows);
      System.out.println(resultRows.blockLast());


      // A real app might group reactive methods in a DAO / Repository.
      var dao = new ExampleReactorDao(reactor);

      // Notice how each subscription triggers a new query execution.
      Mono<Instant> serverTime = dao.serverTime();
      System.out.println("The time is now " + serverTime.block());
      SECONDS.sleep(1);
      System.out.println("The time is now " + serverTime.block());


      // If you care about query metadata, see this method's
      // implementation for one way to process it.
      Flux<Long> numbers = dao.executeQueryAndLogMetadata(
          "SELECT RAW i FROM ARRAY_RANGE(0, 10) AS i"
        )
        .map(row -> row.as(Long.class));
      System.out.println(numbers.collectList().block());

      // There's a catch: metadata is only available
      // if downstream operators consume all rows.
      System.out.println(numbers.take(3).collectList().block());
    }
  }
}
