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

import com.couchbase.insights.client.java.internal.ThreadSafe;

import java.util.List;

import static com.couchbase.insights.client.java.internal.utils.lang.CbCollections.listCopyOf;
import static java.util.Objects.requireNonNull;

@ThreadSafe(caveat = "Unless you modify the byte array returned by Row.bytes()")
public final class QueryResult {
  private final List<Row> rows;
  private final QueryMetadata metadata;

  QueryResult(
    List<Row> rows,
    QueryMetadata metadata
  ) {
    this.rows = listCopyOf(rows);
    this.metadata = requireNonNull(metadata);
  }

  public List<Row> rows() {
    return rows;
  }

  public QueryMetadata metadata() {
    return metadata;
  }

  @Override
  public String toString() {
    return "QueryResult{" +
      "rowCount=" + rows.size() + // Just the count, otherwise the string could be HUGE!
      ", metadata=" + metadata +
      '}';
  }
}
