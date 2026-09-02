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

import java.util.function.Consumer;

public class StartQueryOptions extends CommonQueryOptions<StartQueryOptions> {
  StartQueryOptions.Unmodifiable build(ClusterOptions.Unmodifiable defaults) {
    return new StartQueryOptions.Unmodifiable(this, defaults);
  }

  static class Unmodifiable extends CommonQueryOptions.Unmodifiable {
    Unmodifiable(StartQueryOptions builder, ClusterOptions.Unmodifiable defaults) {
      super(builder, defaults);
    }
  }

  static Unmodifiable configure(
    ClusterOptions.Unmodifiable defaults,
    Consumer<StartQueryOptions> configurator) {
    StartQueryOptions builder = new StartQueryOptions();
    configurator.accept(builder);
    return builder.build(defaults);
  }
}
