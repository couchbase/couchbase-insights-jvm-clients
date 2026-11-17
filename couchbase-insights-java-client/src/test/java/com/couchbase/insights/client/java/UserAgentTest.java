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

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserAgentTest {
  private static final String SUCCESS_RESPONSE = "{\"requestID\":\"x\",\"results\":[],\"status\":\"success\"}";

  /**
   * The User-Agent header values received with each request.
   */
  private final BlockingQueue<List<String>> userAgents = new LinkedBlockingQueue<>();
  private HttpServer server;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/", exchange -> {
      List<String> values = exchange.getRequestHeaders().get("User-Agent");
      userAgents.add(values == null ? emptyList() : values);

      byte[] body = SUCCESS_RESPONSE.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(body);
      }
    });
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  private Cluster newCluster() {
    return Cluster.newInstance(
      "http://127.0.0.1:" + server.getAddress().getPort(),
      Credential.of("Administrator", "password")
    );
  }

  private String nextUserAgent() throws InterruptedException {
    List<String> result = userAgents.poll(10, TimeUnit.SECONDS);
    if (result == null) {
      throw new AssertionError("Server did not receive a request.");
    }
    assertEquals(1, result.size(), "Expected exactly one User-Agent header, but got: " + result);
    return result.get(0);
  }

  private String queryAndGetUserAgent(Cluster cluster) throws InterruptedException {
    cluster.executeQuery("SELECT 1");
    return nextUserAgent();
  }

  private String rawRequestAndGetUserAgent(Cluster cluster) throws InterruptedException {
    InternalUnsupportedHttpClient client = InternalUnsupportedHttpClient.from(cluster);
    try (InternalUnsupportedHttpClient.Response ignored = client.execute(
      req -> req.path("/foo"),
      Duration.ofSeconds(10)
    )) {
      return nextUserAgent();
    }
  }

  @Test
  void sdkOnlyByDefault() throws Exception {
    try (Cluster cluster = newCluster()) {
      String userAgent = queryAndGetUserAgent(cluster);
      assertTrue(userAgent.startsWith("java-insights/"), userAgent);
    }
  }

  @Test
  void callerProductComesFirst() throws Exception {
    try (Cluster cluster = newCluster()) {
      cluster.userAgent("tableau-taco", "3.0.0", "app=My Report (prod)");
      String userAgent = queryAndGetUserAgent(cluster);
      assertTrue(userAgent.startsWith("tableau-taco/3.0.0 (app=My Report \\(prod\\)) java-insights/"), userAgent);
    }
  }

  @Test
  void canChangeAfterCreation() throws Exception {
    try (Cluster cluster = newCluster()) {
      String sdkPart = queryAndGetUserAgent(cluster);

      cluster.userAgent("first", null);
      assertEquals("first " + sdkPart, queryAndGetUserAgent(cluster));

      cluster.userAgent("second", "2.0");
      assertEquals("second/2.0 " + sdkPart, queryAndGetUserAgent(cluster));

      // Survives a credential change, which replaces the query executor
      cluster.credential(Credential.of("Administrator", "newPassword"));
      assertEquals("second/2.0 " + sdkPart, queryAndGetUserAgent(cluster));

      cluster.clearUserAgent();
      assertEquals(sdkPart, queryAndGetUserAgent(cluster));
    }
  }

  @Test
  void rawRequestsIncludeUserAgent() throws Exception {
    try (Cluster cluster = newCluster()) {
      cluster.userAgent("my-driver", "1.0");
      String userAgent = rawRequestAndGetUserAgent(cluster);
      assertTrue(userAgent.startsWith("my-driver/1.0 java-insights/"), userAgent);
    }
  }

  @Test
  void rawRequestCanOverrideUserAgent() throws Exception {
    try (Cluster cluster = newCluster()) {
      InternalUnsupportedHttpClient client = InternalUnsupportedHttpClient.from(cluster);
      try (InternalUnsupportedHttpClient.Response ignored = client.execute(
        req -> req.path("/foo").header("User-Agent", "custom"),
        Duration.ofSeconds(10)
      )) {
        assertEquals("custom", nextUserAgent());
      }
    }
  }

  @Test
  void invalidCharactersAreReplaced() throws Exception {
    try (Cluster cluster = newCluster()) {
      cluster.userAgent("my driver", "1.0 beta", "café");
      String userAgent = queryAndGetUserAgent(cluster);
      assertTrue(userAgent.startsWith("my_driver/1.0_beta (caf?) java-insights/"), userAgent);
    }
  }

  @Test
  void rejectsEmptyProductName() {
    try (Cluster cluster = newCluster()) {
      assertThrows(IllegalArgumentException.class, () -> cluster.userAgent("", "1.0"));
    }
  }
}
