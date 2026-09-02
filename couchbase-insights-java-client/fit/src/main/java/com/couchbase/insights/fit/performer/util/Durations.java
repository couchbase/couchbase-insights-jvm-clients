/*
 * Copyright (c) 2026 Couchbase, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.couchbase.insights.fit.performer.util;

public class Durations {
  private Durations() {
  }

  public static com.google.protobuf.Duration toFit(java.time.Duration d) {
    return com.google.protobuf.Duration.newBuilder()
      .setSeconds(d.getSeconds())
      .setNanos(d.getNano())
      .build();
  }

  public static java.time.Duration toJava(com.google.protobuf.Duration d) {
    return java.time.Duration.ofSeconds(d.getSeconds(), d.getNanos());
  }
}
