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

package com.couchbase.insights.client.java.internal;

import com.couchbase.insights.client.java.internal.utils.security.CbCertificates;

import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Utility methods for reading TLS certificates from various locations.
 */
public class Certificates {
  private Certificates() {
    throw new AssertionError("not instantiable");
  }

  /**
   * Returns the Certificate Authority (CA) certificates used by Couchbase Capella.
   */
  public static List<X509Certificate> getCapellaCertificates() {
    return CbCertificates.getCapellaCaCertificates();
  }

  /**
   * Returns the Certificate Authority (CA) certificates used by
   * Couchbase internal pre-production clusters.
   */
  public static List<X509Certificate> getNonProdCertificates() {
    return CbCertificates.getNonProdCertificates();
  }

}
