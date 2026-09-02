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
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

@ThreadSafe
public class QueryStatus {
  private final String code;
  private final QueryMetrics metrics;
  private final @Nullable Instant createdAt;
  private final @Nullable QueryResultHandle resultHandle;

  QueryStatus(
    String code,
    QueryMetrics metrics,
    @Nullable Instant createdAt,
    @Nullable QueryResultHandle resultHandle
  ) {
    this.code = requireNonNull(code);
    this.metrics = requireNonNull(metrics);
    this.createdAt = createdAt;
    this.resultHandle = resultHandle;
  }

  /**
   * Returns the value of the "status" field from the server response.
   * <p>
   * Typically one of:
   * <ul>
   *   <li>{@code "queued"}</li>
   *   <li>{@code "running"}</li>
   *   <li>{@code "success"}</li>
   * </ul>
   *
   * <b>Note:</b> This method is not expected to return a status code indicating failure,
   * because {@link QueryHandle#fetchStatus()} throws an exception if the query failed.
   */
  public String code() {
    return code;
  }

  @ApiStatus.Experimental
  public QueryMetrics metrics() {
    return metrics;
  }

  /**
   * Returns the query's creation time, or null if unknown.
   */
  @ApiStatus.Experimental
  public @Nullable Instant createdAt() {
    return createdAt;
  }

  /**
   * Returns true if query execution is complete, indicating it's safe to call {@link #resultHandle()}.
   */
  public boolean resultReady() {
    return resultHandle != null;
  }

  /**
   * Returns a handle to the query result.
   *
   * @throws IllegalStateException if the result is not ready; see {@link #resultReady()}.
   */
  public QueryResultHandle resultHandle() {
    if (resultHandle == null) throw new IllegalStateException("Result is not ready.");
    return resultHandle;
  }

  @Override public String toString() {
    return "QueryStatus{" +
      "code='" + code + '\'' +
      ", metrics=" + metrics +
      ", createdAt=" + createdAt +
      ", resultReady=" + resultReady() +
      '}';
  }
}
