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

package com.couchbase.insights.fit.performer.query;

import com.couchbase.insights.client.java.QueryOptions;
import com.couchbase.insights.client.java.RowOptions;
import com.couchbase.insights.client.java.ScanConsistency;
import com.couchbase.insights.client.java.StartQueryOptions;
import com.couchbase.insights.fit.performer.util.CustomDeserializer;
import com.couchbase.insights.fit.performer.util.Durations;
import com.couchbase.insights.fit.performer.util.grpc.ProtobufConversions;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

import static com.couchbase.insights.fit.performer.util.grpc.ProtobufConversions.protobufStructToMap;
import static java.time.Duration.ofSeconds;

public class QueryOptionsUtil {
  public static @Nullable Consumer<QueryOptions> convertQueryOptions(fit.columnar.ExecuteQueryRequest executeQueryRequest) {
    if (!executeQueryRequest.hasOptions()) {
      return null;
    }

    return options -> {
      var opts = executeQueryRequest.getOptions();
      if (opts.hasPriority() && opts.getPriority()) {
        throw new UnsupportedOperationException("Specifying high priority query is not supported.");
      }
      if (opts.hasParametersPositional()) {
        options.parameters(ProtobufConversions.protobufListValueToList(opts.getParametersPositional()));
      }
      if (opts.hasParametersNamed()) {
        options.parameters(protobufStructToMap(opts.getParametersNamed()));
      }
      if (opts.hasReadonly()) {
        options.readOnly(opts.getReadonly());
      }
      if (opts.hasScanConsistency()) {
        options.scanConsistency(switch (opts.getScanConsistency()) {
          case SCAN_CONSISTENCY_REQUEST_PLUS -> ScanConsistency.REQUEST_PLUS;
          case SCAN_CONSISTENCY_NOT_BOUNDED -> ScanConsistency.NOT_BOUNDED;
          case UNRECOGNIZED -> throw new IllegalArgumentException("Bad scan consistency");
        });
      }
      if (opts.hasRaw()) {
        options.raw(protobufStructToMap(opts.getRaw()));
      }
      if (opts.hasTimeout()) {
        options.timeout(Durations.toJava(opts.getTimeout()));
      }
      if (opts.hasDeserializer() && opts.getDeserializer().hasCustom()) {
        CustomDeserializer customDeserializer = new CustomDeserializer();
        options.deserializer(customDeserializer);
      }
      if (opts.hasMaxRetries()) {
        options.maxRetries(opts.getMaxRetries());
      }
    };
  }

  public static @Nullable Consumer<StartQueryOptions> convertStartQueryOptions(fit.columnar.StartQueryRequest startQueryRequest) {
    if (!startQueryRequest.hasOptions()) {
      return null;
    }

    return options -> {
      var opts = startQueryRequest.getOptions();
      if (opts.hasParametersPositional()) {
        options.parameters(ProtobufConversions.protobufListValueToList(opts.getParametersPositional()));
      }
      if (opts.hasParametersNamed()) {
        options.parameters(protobufStructToMap(opts.getParametersNamed()));
      }
      if (opts.hasReadonly()) {
        options.readOnly(opts.getReadonly());
      }
      if (opts.hasScanConsistency()) {
        options.scanConsistency(switch (opts.getScanConsistency()) {
          case SCAN_CONSISTENCY_REQUEST_PLUS -> ScanConsistency.REQUEST_PLUS;
          case SCAN_CONSISTENCY_NOT_BOUNDED -> ScanConsistency.NOT_BOUNDED;
          case UNRECOGNIZED -> throw new IllegalArgumentException("Bad scan consistency");
        });
      }
      if (opts.hasRaw()) {
        options.raw(protobufStructToMap(opts.getRaw()));
      }
      if (opts.hasTimeout()) {
        options.timeout(Durations.toJava(opts.getTimeout()));
      }
      if (opts.hasMaxRetries()) {
        options.maxRetries(opts.getMaxRetries());
      }
    };
  }

  public static @Nullable Consumer<RowOptions> convertRowOptions(fit.columnar.AsyncFetchResultsRequest asyncFetchResultsRequest) {
//    if (!asyncFetchResultsRequest.hasOptions()) {
//      return null;
//    }

    return options -> {
      options.timeout(ofSeconds(30));

      if (asyncFetchResultsRequest.hasOptions()) {
        var opts = asyncFetchResultsRequest.getOptions();
        if (opts.hasDeserializer() && opts.getDeserializer().hasCustom()) {
          CustomDeserializer customDeserializer = new CustomDeserializer();
          options.deserializer(customDeserializer);
        }
      }
    };
  }
}
