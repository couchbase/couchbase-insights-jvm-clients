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

import com.couchbase.insights.client.java.internal.RawQueryMetadata;
import com.couchbase.insights.client.java.internal.ThreadSafe;
import com.couchbase.insights.client.java.internal.utils.json.Mapper;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.couchbase.insights.client.java.internal.utils.lang.CbCollections.transform;
import static com.couchbase.insights.client.java.internal.utils.lang.CbObjects.defaultIfNull;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Holds associated metadata returned by the server.
 */
@ThreadSafe
public final class QueryMetadata {

  private final String requestId;
  private final byte @Nullable [] metrics;
  private final byte @Nullable [] warnings;

  QueryMetadata(RawQueryMetadata raw) {
    this.requestId = defaultIfNull(raw.requestId, "?");
    this.metrics = raw.metrics;
    this.warnings = raw.warnings;
  }

  /**
   * Get the request identifier of the query request
   *
   * @return request identifier
   */
  public String requestId() {
    return requestId;
  }

  /**
   * Get the associated metrics for the response.
   *
   * @return the metrics for the analytics response.
   */
  public QueryMetrics metrics() {
    return metrics == null
      ? new QueryMetrics("{}".getBytes(UTF_8)) // unexpected, but let's not explode.
      : new QueryMetrics(metrics);
  }

  /**
   * Returns warnings if present.
   *
   * @return warnings, if present.
   */
  public List<QueryWarning> warnings() {
    return warnings == null
      ? Collections.emptyList()
      : transform(ErrorCodeAndMessage.fromJsonArray(warnings), QueryWarning::new);
  }

  @Override
  public String toString() {
    return "QueryMetadata{" +
      "requestId='" + requestId + '\'' +
      ", metrics=" + (metrics == null ? "{}" : Mapper.readTree(metrics)) +
      ", warnings=" + (warnings == null ? "[]" : Mapper.readTree(warnings)) +
      '}';
  }
}
