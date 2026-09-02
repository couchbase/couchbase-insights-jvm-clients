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

/**
 * Like {@link java.util.concurrent.TimeoutException}, but unchecked.
 * <p>
 * Thrown if an interaction with the cluster does not complete before its timeout expires.
 */
public class InsightsTimeoutException extends InsightsException {

  public InsightsTimeoutException(String message) {
    super(message);
  }

  public InsightsTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }

  public InsightsTimeoutException(Throwable cause) {
    super(cause);
  }
}
