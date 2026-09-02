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

import com.couchbase.insights.client.java.internal.ThreadSafe;
import okhttp3.Credentials;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Supplier;

import static com.couchbase.insights.client.java.internal.utils.lang.CbCollections.listCopyOf;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * Create an instance like this:
 * <pre>
 * Credential.of(username, password)
 * </pre>
 * Alternatively, to use a client certificate:
 * <pre>
 * Credential.fromKeyStore(
 *     Paths.get("/path/to/client-cert.p12"),
 *     "password"
 * )
 * </pre>
 *
 * @see Cluster#credential(Credential)
 */
@ThreadSafe
public abstract class Credential {

  private static class UsernameAndPassword extends Credential {
    private final String authHeaderValue;

    UsernameAndPassword(String username, String password) {
      this.authHeaderValue = Credentials.basic(username, password, UTF_8);
    }

    @Override
    String httpAuthorizationHeaderValue() {
      return authHeaderValue;
    }

    @Override
    void addHeldCertificate(HandshakeCertificates.Builder builder) {
    }
  }

  private static class Jwt extends Credential {
    private final String authHeaderValue;

    Jwt(String jwt) {
      this.authHeaderValue = "Bearer " + jwt.trim();
    }

    @Override
    String httpAuthorizationHeaderValue() {
      return authHeaderValue;
    }

    @Override
    void addHeldCertificate(HandshakeCertificates.Builder builder) {
    }
  }

  private static class ClientCertificate extends Credential {
    private final HeldCertificate heldCertificate;
    private final List<X509Certificate> intermediates;

    public ClientCertificate(HeldCertificate heldCertificate, List<X509Certificate> intermediates) {
      this.heldCertificate = requireNonNull(heldCertificate);
      this.intermediates = listCopyOf(intermediates);
    }

    @Override
    @Nullable String httpAuthorizationHeaderValue() {
      return null;
    }

    @Override
    void addHeldCertificate(HandshakeCertificates.Builder builder) {
      builder.heldCertificate(heldCertificate, intermediates.toArray(new X509Certificate[0]));
    }
  }

  private static class Dynamic extends Credential {
    private final Supplier<Credential> supplier;

    public Dynamic(Supplier<Credential> supplier) {
      this.supplier = requireNonNull(supplier);
    }

    @Override
    @Nullable String httpAuthorizationHeaderValue() {
      return supplier.get().httpAuthorizationHeaderValue();
    }

    @Override
    void addHeldCertificate(HandshakeCertificates.Builder builder) {
      supplier.get().addHeldCertificate(builder);
    }
  }

  /**
   * Returns a new instance that holds the given username and password.
   */
  public static Credential of(String username, String password) {
    return new UsernameAndPassword(username, password);
  }

  /**
   * Returns a new instance that holds the given JSON Web Token (JWT).
   * <p>
   * Requires Operational Insights 2.2 or later.
   * <p>
   * <b>NOTE:</b> This kind of credential often has a relatively short validity period.
   * To prevent authentication failures caused by stale credentials,
   * periodically pass a fresh credential to {@link Cluster#credential(Credential)}.
   */
  public static Credential ofJwt(String jwt) {
    return new Jwt(jwt);
  }

  /**
   * Returns a new instance that holds a client certificate loaded from the specified PKCS#12 key store file.
   * <p>
   * The key store must have a single entry which must contain a private key and certificate chain.
   * The same password must be used for integrity and encryption.
   * <p>
   * <b>TIP:</b>
   * One way to create a suitable PKCS#12 file from a PEM-encoded private key and certificate
   * is to concatenate the key and certificate into a file named "client-cert.pem", then run this command:
   * <pre>
   * openssl pkcs12 -export -in client-cert.pem -out client-cert.p12 -passout pass:password
   * </pre>
   * This creates a PKCS#12 file named "client-cert.p12" protected by the password "password".
   *
   * @param password for verifying key store integrity and decrypting the private key
   */
  public static Credential fromKeyStore(Path pkcs12Path, @Nullable String password) {
    KeyStore keyStore = loadKeyStore(pkcs12Path, password);
    return fromKeyStore(keyStore, password);
  }

  /**
   * Returns a new instance that holds a client certificate loaded from the specified key store.
   * <p>
   * The key store must have a single entry which must contain a private key and certificate chain.
   *
   * @param password for decrypting the private key
   */
  public static Credential fromKeyStore(KeyStore keyStore, @Nullable String password) {
    try {
      List<String> aliases = toList(keyStore.aliases());
      if (aliases.size() != 1) {
        throw new IllegalArgumentException("Expected the key store to contain exactly one entry, but got aliases: " + aliases);
      }
      String alias = aliases.get(0);

      PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, password == null ? null : password.toCharArray());
      Certificate[] chain = keyStore.getCertificateChain(alias);
      X509Certificate userCert = (X509Certificate) chain[0];

      HeldCertificate heldCertificate = new HeldCertificate(
        new KeyPair(userCert.getPublicKey(), privateKey),
        userCert
      );

      List<X509Certificate> intermediates = new ArrayList<>();
      for (int i = 1; i < chain.length; i++) { // skip zero-th because that's the user's certificate
        intermediates.add((X509Certificate) chain[i]);
      }

      return new ClientCertificate(heldCertificate, intermediates);

    } catch (ClassCastException | GeneralSecurityException e) {
      throw new RuntimeException("Failed to read client certificate from key store.", e);
    }
  }

  private static KeyStore loadKeyStore(Path keyStorePath, @Nullable String password) {
    try (InputStream keyStoreInputStream = Files.newInputStream(keyStorePath)) {
      final KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
      store.load(
        keyStoreInputStream,
        password != null ? password.toCharArray() : null
      );
      return store;
    } catch (Exception ex) {
      throw new RuntimeException("Failed to read key store.", ex);
    }
  }

  private static <T> List<T> toList(Enumeration<T> e) {
    List<T> result = new ArrayList<>();
    while (e.hasMoreElements()) {
      result.add(e.nextElement());
    }
    return result;
  }

  /**
   * Returns a new instance of a dynamic credential that invokes the given supplier
   * every time a credential is required.
   * <p>
   * This enables updating a credential without having to restart your application.
   *
   * @deprecated This method is not compatible with client certificate credentials.
   * Instead, please update the credential by calling {@link Cluster#credential(Credential)}.
   */
  @Deprecated
  public static Credential ofDynamic(Supplier<Credential> supplier) {
    return new Dynamic(supplier);
  }

  abstract @Nullable String httpAuthorizationHeaderValue();

  abstract void addHeldCertificate(HandshakeCertificates.Builder builder);

  /**
   * @see #of
   * @see #fromKeyStore
   */
  private Credential() {
  }
}
