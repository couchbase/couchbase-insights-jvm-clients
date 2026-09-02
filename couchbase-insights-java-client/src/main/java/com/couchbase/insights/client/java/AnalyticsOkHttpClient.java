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

import com.couchbase.insights.client.java.internal.TrustSource;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.CipherSuite;
import okhttp3.Connection;
import okhttp3.ConnectionPool;
import okhttp3.ConnectionSpec;
import okhttp3.Dns;
import okhttp3.EventListener;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.TlsVersion;
import okhttp3.tls.HandshakeCertificates;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.couchbase.insights.client.java.QueryExecutor.propagateInterruption;
import static com.couchbase.insights.client.java.internal.utils.lang.CbCollections.listOf;
import static com.couchbase.insights.client.java.internal.utils.lang.CbThrowables.propagate;
import static com.couchbase.insights.client.java.internal.utils.lang.CbThrowables.throwIfInstanceOf;

class AnalyticsOkHttpClient implements Closeable {

  final OkHttpClient client;
  private final Duration defaultQueryTimeout;

  private static final Dns shufflingDns = hostname -> {
    List<InetAddress> result = new ArrayList<>(Dns.SYSTEM.lookup(hostname));
    Collections.shuffle(result);
    return result;
  };

  private static HandshakeCertificates getHandshakeCertificates(Credential credential, TrustSource trustSource) {
    HandshakeCertificates.Builder builder = new HandshakeCertificates.Builder();
    credential.addHeldCertificate(builder);

    List<X509Certificate> certs = trustSource.certificates();
    if (certs != null) {
      certs.forEach(builder::addTrustedCertificate);
    }

    return builder.build();
  }

  private static final HostnameVerifier insecureHostnameVerifier = new HostnameVerifier() {
    @Override
    public boolean verify(String hostname, SSLSession session) {
      return true;
    }

    @Override
    public String toString() {
      return "InsecureHostnameVerifier";
    }
  };

  public AnalyticsOkHttpClient(ClusterOptions.Unmodifiable options, HttpUrl url, Credential credential) {
    this.defaultQueryTimeout = options.timeout().queryTimeout();
    Duration connectTimeout = options.timeout().connectTimeout();

    OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
      .connectTimeout(connectTimeout)
      .connectionSpecs(listOf(url.isHttps() ? secureConnectionSpec(options.security()) : ConnectionSpec.CLEARTEXT))

      // Analytics service doesn't start sending the response until it's done calculating it,
      // so set the read timeout to the same value as the call timeout.
      .readTimeout(defaultQueryTimeout)
      .callTimeout(defaultQueryTimeout)

      .connectionPool(new ConnectionPool(5, 1, TimeUnit.SECONDS))
      .dns(shufflingDns)
      //.eventListener(newEventListener())
      ;

    TrustSource trustSource = options.security().trustSource();
    TrustManagerFactory trustManagerFactory = trustSource.trustManagerFactory();

    HandshakeCertificates handshakeCertificates = getHandshakeCertificates(credential, options.security().trustSource());
    KeyManager[] keyManagers = new KeyManager[]{handshakeCertificates.keyManager()};

    if (trustSource.isInsecure()) {
      clientBuilder.hostnameVerifier(insecureHostnameVerifier);
      clientBuilder.sslSocketFactory(newInsecureSocketFactory(keyManagers), insecureTrustManager);

    } else if (trustManagerFactory != null) {
      clientBuilder.sslSocketFactory(
        newSocketFactory(
          keyManagers, // get client certs from usual place
          trustManagerFactory // let user override the "trust" side of things
        ),
        firstX509TrustManager(trustManagerFactory)
      );

    } else {
      clientBuilder.sslSocketFactory(handshakeCertificates.sslSocketFactory(), handshakeCertificates.trustManager());
    }

    clientBuilder.addInterceptor(chain -> {
      Request request = chain.request();

      // get the value every time in case the credential is dynamic (deprecated)
      String authorizationHeaderValue = credential.httpAuthorizationHeaderValue();
      if (authorizationHeaderValue != null) {
        request = request.newBuilder()
          .header("Authorization", authorizationHeaderValue)
          .build();
      }

      return chain.proceed(request);
    });

    this.client = clientBuilder.build();

    int maxInFlightRequests = 128;
    this.client.dispatcher().setMaxRequests(maxInFlightRequests);
    this.client.dispatcher().setMaxRequestsPerHost(maxInFlightRequests);
  }

  private static X509TrustManager firstX509TrustManager(TrustManagerFactory tmf) {
    for (TrustManager tm : tmf.getTrustManagers()) {
      if (tm instanceof X509TrustManager) {
        return (X509TrustManager) tm;
      }
    }
    throw new IllegalArgumentException("The configured TrustManagerFactory does not provide an X509TrustManager.");
  }

  private ConnectionSpec secureConnectionSpec(SecurityOptions.Unmodifiable security) {
    return new ConnectionSpec.Builder(true)
      .cipherSuites(
        security.cipherSuites().stream()
          .map(CipherSuite::forJavaName)
          .toArray(CipherSuite[]::new)
      )
      .tlsVersions(TlsVersion.TLS_1_3)
      .build();
  }

  private EventListener newEventListener() {
    return new EventListener() {
      @Override
      public void dnsStart(Call call, String domainName) {
        System.out.println("dnsStart: " + domainName);
      }

      @Override
      public void dnsEnd(Call call, String domainName, List<InetAddress> addresses) {
        System.out.println("dnsEnd: " + domainName + " -> " + addresses);
      }

      @Override
      public void connectStart(Call call, InetSocketAddress address, Proxy proxy) {
        System.out.println("connectStart: " + address);
//          call.request().tag(ConcurrentHashMap.class).put("remote", address.toString());
      }

      @Override
      public void connectFailed(Call call, InetSocketAddress inetSocketAddress, Proxy proxy, @Nullable Protocol protocol, IOException ioe) {
        System.out.println(call.request().url() + " failed: " + ioe);
      }

      @Override
      public void connectionAcquired(Call call, Connection connection) {
        System.out.println(call.request().url() + " acquired: " + connection);
      }

      @Override
      public void connectionReleased(Call call, Connection connection) {
        System.out.println(call.request().url() + " released: " + connection);
      }
    };
  }

  /**
   * Like {@link Call#execute()} but responsive to interruption
   * while waiting for the response body.
   */
  public static Response executeInterruptibly(Call call) throws IOException {
    try {
      // Future.get() throws InterruptedException when this thread is interrupted.
      // That's exactly the behavior we want, and the only reason we're wrapping the
      // response in a future instead of simply calling Call.execute().
      return executeFuture(call).get();

    } catch (InterruptedException e) {
      call.cancel();
      throw propagateInterruption(e);

    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      throwIfInstanceOf(cause, IOException.class);
      throw propagate(e);
    }
  }

  /**
   * Enqueues the given call and returns a future that completes
   * when the call completes.
   */
  public static Future<Response> executeFuture(Call call) {
    CompletableFuture<Response> future = new CompletableFuture<>();

    call.enqueue(new Callback() {
      @Override
      public void onResponse(Call call, Response response) {
        future.complete(response);
      }

      @Override
      public void onFailure(Call call, IOException e) {
        future.completeExceptionally(e);
      }
    });

    return future;
  }

  public OkHttpClient clientWithTimeout(Duration timeout) {
    if (timeout.equals(defaultQueryTimeout)) {
      return client;
    }

    return client.newBuilder()
      .readTimeout(timeout)
      .callTimeout(timeout)
      .build();
  }

  /**
   * Prevent reuse of any existing connections.
   * <p>
   * Ideally we'd target a specific connection, but OkHttp does not currently support that.
   */
  public void evictAll() {
    client.connectionPool().evictAll();
  }

  @Override
  public void close() {
    client.dispatcher().executorService().shutdown();
    client.dispatcher().cancelAll();
    client.connectionPool().evictAll();
  }

  private static final X509TrustManager insecureTrustManager = new X509TrustManager() {
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) {
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
    }

    @Override
    public String toString() {
      return "InsecureTrustManager";
    }
  };

  private static SSLSocketFactory newInsecureSocketFactory(KeyManager @Nullable [] keyManagers) {
    return newSocketFactory(keyManagers, new TrustManager[]{insecureTrustManager});
  }

  private static SSLSocketFactory newSocketFactory(KeyManager @Nullable [] keyManagers, TrustManagerFactory trustManagerFactory) {
    return newSocketFactory(keyManagers, trustManagerFactory.getTrustManagers());
  }

  private static SSLSocketFactory newSocketFactory(KeyManager @Nullable [] keyManagers, TrustManager[] trustManagers) {
    try {
      SSLContext sslContext = SSLContext.getInstance("SSL");
      sslContext.init(keyManagers, trustManagers, null);
      return sslContext.getSocketFactory();

    } catch (KeyManagementException | NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
}
