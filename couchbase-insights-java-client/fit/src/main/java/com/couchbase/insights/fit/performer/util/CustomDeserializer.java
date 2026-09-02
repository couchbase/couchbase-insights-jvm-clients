/*
 * Copyright 2023 Couchbase, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.couchbase.insights.fit.performer.util;

import com.couchbase.insights.client.java.codec.Deserializer;
import com.couchbase.insights.client.java.codec.TypeRef;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * A custom deserializer for FIT that sneakily adds a field to the result
 * so the driver can verify the SDK honored the request to use a custom deserializer.
 */
@NullMarked
@SuppressWarnings("unchecked")
public class CustomDeserializer implements Deserializer {
  private static final JsonMapper mapper = JsonMapper.builder().build();

  @Override
  public <T> T deserialize(Class<T> target, byte[] input) {
    requireObjectNode(target);

    try {
      ObjectNode obj = (ObjectNode) mapper.readTree(input);
      obj.put("Serialized", false);
      return (T) obj;

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public <T> T deserialize(TypeRef<T> target, byte[] input) {
    requireObjectNode(target.type());
    return (T) deserialize(ObjectNode.class, input);
  }

  private static void requireObjectNode(Type type) {
    if (!type.equals(ObjectNode.class)) {
      throw new UnsupportedOperationException("Expected target class to be ObjectNode, but got: " + type);
    }
  }
}
