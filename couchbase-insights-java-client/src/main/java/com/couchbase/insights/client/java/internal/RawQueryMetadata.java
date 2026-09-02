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

package com.couchbase.insights.client.java.internal;

import org.jspecify.annotations.Nullable;

import static java.nio.charset.StandardCharsets.UTF_8;

public class RawQueryMetadata {
  public @Nullable String requestId;
  public byte @Nullable [] signature;
  public byte @Nullable [] plans;
  public byte @Nullable [] metrics;
  public byte @Nullable [] errors;
  public byte @Nullable [] warnings;
  public @Nullable String clientContextId;
  public @Nullable String status;
  public @Nullable String handle;
  public @Nullable String createdAt;

  @Override public String toString() {
    return "RawQueryMetadata{" +
      "requestId='" + requestId + '\'' +
      ", signature=" + newString(signature) +
      ", plans=" + newString(plans) +
      ", metrics=" + newString(metrics) +
      ", errors=" + newString(errors) +
      ", warnings=" + newString(warnings) +
      ", clientContextId='" + clientContextId + '\'' +
      ", status='" + status + '\'' +
      ", handle='" + handle + '\'' +
      '}';
  }

  private static @Nullable String newString(byte @Nullable [] array) {
    return array == null ? null : new String(array, UTF_8);
  }
}
