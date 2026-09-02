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

package com.couchbase.insights.client.java.internal.utils.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;

import static java.util.Objects.requireNonNull;

public class Mapper {
  private static final JsonMapper mapper = JsonMapper.builder().build();

  public static JsonNode readTree(byte[] json) {
    try {
      return mapper.readTree(json);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static <T> T convertValue(
    Object fromValue,
    TypeReference<T> toValueType
  ) {
    return mapper.convertValue(fromValue, toValueType);
  }

  public static ObjectNode createObjectNode() {
    return mapper.createObjectNode();
  }

  public static <T> T convertValue(
    Object fromValue,
    Class<T> toValueType
  ) {
    return mapper.convertValue(fromValue, toValueType);
  }

  public static <T> T readValue(String json, Class<T> valueType) {
    try {
      return requireNonNull(mapper.readValue(json, valueType));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static <T> T readValue(byte[] json, Class<T> valueType) {
    try {
      return requireNonNull(mapper.readValue(json, valueType));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static String writeValueAsString(@Nullable Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static byte[] writeValueAsBytes(@Nullable Object value) {
    try {
      return mapper.writeValueAsBytes(value);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static void writeValue(OutputStream outputStream, @Nullable Object value) {
    try {
      mapper.writeValue(outputStream, value);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

}
