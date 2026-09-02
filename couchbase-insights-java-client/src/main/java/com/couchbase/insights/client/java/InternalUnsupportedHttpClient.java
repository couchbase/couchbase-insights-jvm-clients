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

import com.couchbase.insights.client.java.codec.Deserializer;
import com.couchbase.insights.client.java.internal.InternalJacksonSerDes;
import com.couchbase.insights.client.java.internal.RawQueryMetadata;
import com.couchbase.insights.client.java.internal.ThreadSafe;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

import static com.couchbase.insights.client.java.internal.utils.lang.CbStrings.removeStart;
import static java.util.Objects.requireNonNull;

/**
 * An HTTP client intended for use by other Couchbase libraries.
 * <p>
 * NOT PART OF THE PUBLIC API! This class may change without notice.
 */
@ThreadSafe
@ApiStatus.Internal
public class InternalUnsupportedHttpClient {
  private final Cluster cluster;
  private final HttpUrl baseUrl;

  public static InternalUnsupportedHttpClient from(Cluster cluster) {
    return new InternalUnsupportedHttpClient(
      cluster,
      cluster.queryExecutor.baseUrl
    );
  }

  private InternalUnsupportedHttpClient(
    Cluster cluster,
    HttpUrl baseUrl
  ) {
    this.cluster = requireNonNull(cluster);

    this.baseUrl = new HttpUrl.Builder()
      .scheme(baseUrl.scheme())
      .host(baseUrl.host())
      .port(baseUrl.port())
      .build();
  }

  /**
   * Executes an arbitrary HTTP request.
   *
   * @throws InsightsTimeoutException if request times out.
   * @throws CancellationException if thread is interrupted.
   * @throws InsightsException for all other IO errors.
   */
  public Response execute(
    Consumer<RequestBuilder> requestCustomizer,
    Duration timeout
  ) {
    RequestBuilder builder = new RequestBuilder(baseUrl.toString());
    requestCustomizer.accept(builder);

    return new Response(
      cluster.queryExecutor.executeRaw(
        builder.wrapped.build(),
        timeout
      )
    );
  }

  /**
   * Executes an HTTP request for an Analytics query.
   *
   * @throws QueryException if response has a non-empty "errors" field.
   * @throws InsightsTimeoutException if request times out.
   * @throws CancellationException if thread is interrupted.
   * @throws InsightsException for all other IO errors.
   */
  public RawQueryMetadata executeStreaming(
    Consumer<RequestBuilder> requestCustomizer,
    Duration timeout,
    Consumer<Row> rowAction,
    @Nullable Deserializer deserializer
  ) {
    RequestBuilder builder = new RequestBuilder(baseUrl.toString());
    requestCustomizer.accept(builder);

    return cluster.queryExecutor.executeStreamingQueryOnce(
      builder.wrapped,
      timeout,
      rowAction,
      deserializer == null ? InternalJacksonSerDes.INSTANCE : deserializer
    );
  }

  public static class Response implements Closeable {
    private final okhttp3.Response wrapped;

    Response(okhttp3.Response wrapped) {
      this.wrapped = wrapped;
    }

    @Override
    public void close() {
      wrapped.close();
    }

    public int httpStatusCode() {
      return wrapped.code();
    }

    /**
     * Returns an input stream over the bytes of the body,
     * or null if the response does not have a body.
     * <p>
     * Must not be called more than once.
     */
    public @Nullable InputStream bodyInputStream() {
      ResponseBody body = wrapped.body();
      return body == null ? null : body.byteStream();
    }

    /**
     * Returns the response body as a string,
     * or null if the response does not have a body.
     *
     * @throws UncheckedIOException if there was an error reading the response body.
     */
    public @Nullable String bodyAsString() {
      try {
        ResponseBody body = wrapped.body();
        return body == null ? null : body.string();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  public static class RequestBuilder {
    private final String baseUrl;

    RequestBuilder(String baseUrl) {
      this.baseUrl = baseUrl.endsWith("/")
        ? baseUrl
        : baseUrl + "/";
    }

    private final Request.Builder wrapped = new Request.Builder();

    /**
     * Sets the path component (and query string, if applicable).
     * <p>
     * Caller is responsible for ensuring the input is correctly URI-encoded.
     *
     * @param path pre-encoded path and query
     */
    public RequestBuilder path(String path) {
      wrapped.url(baseUrl + removeStart(path, "/"));
      return this;
    }

    public RequestBuilder header(String name, String value) {
      wrapped.header(name, value);
      return this;
    }

    private static final MediaType JSON = requireNonNull(MediaType.parse("application/json"));

    public RequestBuilder postJson(byte[] body) {
      wrapped.post(new RequestBody() {
        @Override
        public long contentLength() {
          return body.length;
        }

        @Override
        public MediaType contentType() {
          return JSON;
        }

        @Override
        public void writeTo(BufferedSink bufferedSink) throws IOException {
          bufferedSink.write(body);
        }
      });

      return this;
    }

    public RequestBuilder delete() {
      wrapped.delete();
      return this;
    }

    public RequestBuilder get() {
      wrapped.get();
      return this;
    }
  }
}
