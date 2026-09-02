/*
 * Copyright 2024 Couchbase, Inc.
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

package com.couchbase.insights.fit.performer.cluster;


import com.couchbase.insights.client.java.Cluster;
import com.couchbase.insights.client.java.Credential;
import com.couchbase.insights.fit.performer.common.util.PemUtil;
import com.couchbase.insights.fit.performer.util.Durations;

import java.util.ArrayList;
import java.util.List;

import static com.couchbase.insights.fit.performer.common.util.PemUtil.decodeCertificates;


public class AnalyticsClusterConnection {
  private final Cluster cluster;
  private final fit.columnar.ClusterNewInstanceRequest request;
  // Commands to run when this ColumnarClusterConnection is being closed.  Allows closing other related resources that have
  // the same lifetime.
  private final List<Runnable> onClusterConnectionClose;

  public AnalyticsClusterConnection(fit.columnar.ClusterNewInstanceRequest request,
                                    ArrayList<Runnable> onClusterConnectionClose) {
    this.request = request;
    this.onClusterConnectionClose = onClusterConnectionClose;

    this.cluster = Cluster.newInstance(
      request.getConnectionString(),
      credentialFromProto(request.getCredential()),
      env -> {
        if (!request.hasOptions()) {
          return;
        }
        var options = request.getOptions();

        if (options.hasDeserializer()) {
          // Ignore. FIT driver can't specify alternate deserializers in a meaningful way.
        }

        env.timeout(timeout -> {
          if (options.hasTimeout()) {
            var timeoutOptions = options.getTimeout();
            if (timeoutOptions.hasConnectTimeout()) {
              timeout.connectTimeout(Durations.toJava(timeoutOptions.getConnectTimeout()));
            }
            if (timeoutOptions.hasDispatchTimeout()) {
              // todo - check with David if intentional
              throw new UnsupportedOperationException("dispatchTimeout not exposed in SDK");
            }
            if (timeoutOptions.hasQueryTimeout()) {
              timeout.queryTimeout(Durations.toJava(timeoutOptions.getQueryTimeout()));
            }
            if (timeoutOptions.hasHandleTimeout()) {
              timeout.handleRequestTimeout(Durations.toJava(timeoutOptions.getHandleTimeout()));
            }
          }
        })
        .security(sec -> {
          if (options.hasSecurity()) {
            var secOptions = options.getSecurity();
            if (secOptions.hasTrustOnlyCapella()) {
              sec.trustOnlyCapella();
            }
            if (secOptions.hasTrustOnlyPemString()) {
              sec.trustOnlyPemString(secOptions.getTrustOnlyPemString());
            }
            if (secOptions.hasTrustOnlyPlatform()) {
              sec.trustOnlyJvm();
            }
            if (secOptions.hasDisableServerCertificateVerification()) {
              sec.disableServerCertificateVerification(secOptions.getDisableServerCertificateVerification());
            }
            if (secOptions.getCipherSuitesCount() > 0) {
              sec.cipherSuites(secOptions.getCipherSuitesList());
            }
          }
        });
        if (options.hasMaxRetries()) {
          env.maxRetries(options.getMaxRetries());
        }
      });
  }

  /**
   * Converts a protobuf Credential message to the SDK's Credential type.
   * Switch is exhaustive on the oneof type case; adding a new variant to the
   * proto will fail to compile here, which is the point.
   * <p>
   * FIT-internal: {@code public} only because {@link com.couchbase.insights.fit.performer.rpc.JavaAnalyticsService} lives in a sibling package.
   */
  public static Credential credentialFromProto(fit.columnar.ClusterNewInstanceRequest.Credential proto) {
    return switch (proto.getTypeCase()) {
      case USERNAME_AND_PASSWORD -> Credential.of(
        proto.getUsernameAndPassword().getUsername(),
        proto.getUsernameAndPassword().getPassword()
      );
      case JWT_AUTH -> Credential.ofJwt(proto.getJwtAuth().getJwt());
      case CERTIFICATE_AUTH -> clientCertCredential(proto.getCertificateAuth());
      case TYPE_NOT_SET -> throw new IllegalArgumentException("FIT request did not specify a credential.");
    };
  }

  private static Credential clientCertCredential(fit.columnar.ClusterNewInstanceRequest.Credential.CertificateAuth keyAndCert) {
    var key = PemUtil.parseRsaPrivateCrtKey(keyAndCert.getKey());
    var certChain = decodeCertificates(keyAndCert.getCertBytes().newInput());
    String password = "password";
    var keystoreFile = PemUtil.newKeyStore(key, certChain, password);
    return Credential.fromKeyStore(keystoreFile, password);
  }

  public Cluster cluster() {
    return cluster;
  }

  public void close() {
    cluster.close();
    onClusterConnectionClose.forEach(Runnable::run);
  }

  public fit.columnar.ClusterNewInstanceRequest request() {
    return request;
  }
}
