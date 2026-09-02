/*
 * Copyright (c) 2024 Couchbase, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.couchbase.insights.fit.performer.util;

import com.couchbase.insights.client.java.InsightsException;
import com.couchbase.insights.client.java.UncheckedTimeoutException;
import com.couchbase.insights.client.java.InvalidCredentialException;
import com.couchbase.insights.client.java.QueryException;
import com.couchbase.insights.client.java.QueryNotFoundException;
import com.couchbase.insights.client.java.internal.utils.lang.CbThrowables;
import fit.columnar.PlatformErrorType;


public class ErrorUtil {
  private ErrorUtil() {
    throw new AssertionError("not instantiable");
  }

  private static fit.columnar.PlatformErrorType convertPlatformError(Throwable exception) {
    return (exception instanceof IllegalArgumentException)
      ? PlatformErrorType.PLATFORM_ERROR_INVALID_ARGUMENT
      : PlatformErrorType.PLATFORM_ERROR_OTHER;
  }

  public static fit.columnar.Error convertError(Throwable raw) {
    var ret = fit.columnar.Error.newBuilder();

    // FIT framework assumes clients surface all server-side timeouts as UncheckedTimeoutException,
    // but this is incompatible with the documented semantics of UncheckedTimeoutException, which is
    // "Thrown if an interaction with the Analytics cluster does not complete before its timeout expires."
    // In this case, the interaction completed, so UncheckedTimeoutException is not appropriate.
    // Convert it to a timeout exception just for the FIT driver.
    if (raw instanceof QueryException queryException && queryException.code() == 21002) {
      raw = new UncheckedTimeoutException(raw.getMessage());
    }

    if (raw instanceof InsightsException) {
      var out = fit.columnar.ColumnarError.newBuilder()
        .setAsString(CbThrowables.getStackTraceAsString(raw));

      if (raw instanceof QueryException queryException) {
        out.setSubException(fit.columnar.SubColumnarError.newBuilder().setQueryException(
          fit.columnar.QueryException.newBuilder()
            .setErrorCode(queryException.code())
            .setServerMessage(queryException.serverMessage())
            .build())
          .build());
      }
      if (raw instanceof InvalidCredentialException) {
        out.setSubException(fit.columnar.SubColumnarError.newBuilder().setInvalidCredentialException(
          fit.columnar.InvalidCredentialException.newBuilder().build())
          .build());
      }

      if (raw instanceof UncheckedTimeoutException) {
        out.setSubException(fit.columnar.SubColumnarError.newBuilder().setTimeoutException(fit.columnar.TimeoutException.newBuilder().build())
          .build());
      }

      if (raw instanceof QueryNotFoundException) {
        out.setSubException(fit.columnar.SubColumnarError.newBuilder().setQueryNotFoundException(fit.columnar.QueryNotFoundException.newBuilder().build())
          .build());
      }

      if (raw.getCause() != null) {
        out.setCause(convertError(raw.getCause()));
      }

      ret.setColumnar(out);
    } else {
      ret.setPlatform(fit.columnar.PlatformError.newBuilder()
        .setType(convertPlatformError(raw))
        .setAsString(raw.toString()));
    }

    return ret.build();
  }
}
