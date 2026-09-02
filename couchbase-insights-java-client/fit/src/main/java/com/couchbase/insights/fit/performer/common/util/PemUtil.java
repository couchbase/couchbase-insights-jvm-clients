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

package com.couchbase.insights.fit.performer.common.util;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.List;

import static java.util.Objects.requireNonNull;

public class PemUtil {
  private PemUtil() {
  }

  private static final Provider BOUNCY_CASTLE = new BouncyCastleProvider();

  public static RSAPrivateCrtKey parseRsaPrivateCrtKey(String pem) {
    try (PemReader pemReader = new PemReader(new StringReader(pem))) {
      PemObject pemObject = pemReader.readPemObject();
      byte[] keyBytes = pemObject.getContent();
      PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);

      // The standard RSAKeyFactory refuses to parse our PEM-encoded RSAPrivateCrtKey,
      // so let Bouncy Castle do the heavy lifting.
      KeyFactory kf = KeyFactory.getInstance("RSA", BOUNCY_CASTLE);

      PrivateKey result = kf.generatePrivate(keySpec);
      return (RSAPrivateCrtKey) result;

    } catch (IOException | GeneralSecurityException e) {
      throw new RuntimeException("Failed to parse RSA private certificate key", e);
    }
  }

  public static List<X509Certificate> decodeCertificates(InputStream pem) {
    requireNonNull(pem);
    try {
      //noinspection unchecked
      return (List<X509Certificate>) getX509CertificateFactory()
        .generateCertificates(pem);
    } catch (CertificateException e) {
      throw new IllegalArgumentException("Failed to decode certificates", e);
    }
  }

  private static CertificateFactory getX509CertificateFactory() {
    try {
      return CertificateFactory.getInstance("X.509");
    } catch (CertificateException e) {
      throw new RuntimeException("Could not instantiate X.509 CertificateFactory", e);
    }
  }

  public static KeyStore newKeyStore(
    PrivateKey privateKey,
    List<X509Certificate> chain,
    String password
  ) {
    try {
      KeyStore keyStore = KeyStore.getInstance("PKCS12");
      keyStore.load(null, null); // Initialize empty keystore

      var keyAlias = "1";
      var passwordAsCharArray = password.toCharArray();
      var certChain = chain.toArray(new Certificate[0]);
      keyStore.setKeyEntry(keyAlias, privateKey, passwordAsCharArray, certChain);
      return keyStore;

    } catch (IOException | GeneralSecurityException e) {
      throw new RuntimeException("Failed to write PKCS12 file", e);
    }
  }
}
