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

import static java.util.Objects.requireNonNull;

class QueryContext {
  private final String database;
  private final String scope;
  private final String formatted;

  QueryContext(String database, String scope) {
    this.database = requireNonNull(database);
    this.scope = requireNonNull(scope);

    if (database.contains("`")) {
      throw new IllegalArgumentException("Database name must not contain backtick (`), but got: " + database);
    }
    if (scope.contains("`")) {
      throw new IllegalArgumentException("Scope name must not contain backtick (`), but got: " + scope);
    }

    this.formatted = "default:`" + database + "`.`" + scope + "`";
  }

  public String database() {
    return database;
  }

  public String scope() {
    return scope;
  }

  public String format() {
    return formatted;
  }

  @Override
  public String toString() {
    return formatted;
  }
}
