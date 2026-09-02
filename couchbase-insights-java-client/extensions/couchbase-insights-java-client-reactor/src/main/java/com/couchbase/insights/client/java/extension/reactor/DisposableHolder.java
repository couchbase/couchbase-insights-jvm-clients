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

package com.couchbase.insights.client.java.extension.reactor;

import org.jspecify.annotations.Nullable;
import reactor.core.Disposable;

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Holds a disposable. Defers disposal requests until a disposable is present.
 * <p>
 * In other words, it lets us pre-emptively cancel a task that might not exist yet.
 */
class DisposableHolder {
  private @Nullable Disposable held;
  private boolean disposalRequested;

  synchronized void set(Disposable d) {
    requireNonNull(d);
    if (held != null) throw new IllegalStateException("already set");

    this.held = d;
    if (disposalRequested) {
      held.dispose();
    }
  }

  /**
   * Avoid even scheduling a task if we know it would immediately be disposed.
   */
  synchronized void setIfNotAlreadyDisposed(Supplier<Disposable> d) {
    requireNonNull(d);
    if (held != null) throw new IllegalStateException("already set");

    if (!disposalRequested) {
      set(d.get());
    }
  }

  synchronized void dispose() {
    disposalRequested = true;
    if (held != null) {
      held.dispose();
    }
  }
}
