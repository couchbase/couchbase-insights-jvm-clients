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

import com.couchbase.insights.client.java.internal.utils.json.Mapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
class StatusResponse {

  @JsonIgnoreProperties(ignoreUnknown = true)
  static class Partition {
    public String handle = "";
    public int resultCount;

    @Override public String toString() {
      return "Partition{" +
        "handle='" + handle + '\'' +
        ", resultCount=" + resultCount +
        '}';
    }
  }

  public String status = "";
  public String handle = "";
  public ObjectNode metrics = Mapper.createObjectNode();
  public int resultCount;
  public List<Partition> partitions = Collections.emptyList();
  public boolean resultSetOrdered;
  public List<ErrorCodeAndMessage> errors = Collections.emptyList();
  public String createdAt = "";

  @Override public String toString() {
    return "StatusResponse{" +
      "status='" + status + '\'' +
      ", errors=" + errors +
      ", handle='" + handle + '\'' +
      ", resultCount=" + resultCount +
      ", partitions=" + partitions +
      ", resultSetOrdered=" + resultSetOrdered +
      ", createdAt=" + createdAt +
      ", metrics=" + metrics +
      '}';
  }
}
