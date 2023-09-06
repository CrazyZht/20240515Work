/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tomcat.util.openssl;

import java.lang.foreign.MemorySegment;

import static org.apache.tomcat.util.openssl.openssl_h.*;

/**
 * Functional macros not handled by jextract.
 */
public class openssl_h_Macros {


    /**
     * Set maximum protocol version on the given context.
     * # define SSL_CTX_set_max_proto_version(sslCtx, version) \
     *          SSL_CTX_ctrl(sslCtx, SSL_CTRL_SET_MAX_PROTO_VERSION, version, NULL)
     * @param sslCtx the SSL context
     * @param version the maximum version
     * @return > 0 if successful
     */
    public static long SSL_CTX_set_max_proto_version(MemorySegment sslCtx, long version) {
        return SSL_CTX_ctrl(sslCtx, SSL_CTRL_SET_MAX_PROTO_VERSION(), version, MemorySegment.NULL);
    }


    /**
     * Set minimum protocol version on the given context.
     * # define SSL_CTX_set_min_proto_version(sslCtx, version) \
     *          SSL_CTX_ctrl(sslCtx, SSL_CTRL_SET_MIN_PROTO_VERSION, version, NULL)
     * @param sslCtx the SSL context
     * @param version the maximum version
     * @return > 0 if successful
     */
    public static long SSL_CTX_set_min_proto_version(MemorySegment sslCtx, long version) {
        return SSL_CTX_ctrl(sslCtx, SSL_CTRL_SET_MIN_PROTO_VERSION(), version, MemorySegment.NULL);
    }


    /**
     * Get the session cache size.
     * # define SSL_CTX_sess_get_cache_size(sslCtx) \
     *          SSL_CTX_ctrl(sslCtx, SSL_CTRL_GET_SESS_CACHE_SIZE, 0, NULL)
     * @param sslCtx the SSL context
     * @param cacheSize the session cache size
     * @return > 0 if successful
     */
    public static long SSL_CTX_sess_get_cache_size(MemorySegment sslCtx) {
        return SSL_CTX_ctrl(sslCtx, SSL_CTRL_GET_SESS_CACHE_SIZE(), 0, MemorySegment.NULL);
    }


    /**
     * Set the session cache size.
     * # define SSL_CTX_sess_set_cache_size(sslCtx, t) \
     *          SSL_CTX_ctrl(sslCtx, SSL_CTRL_SET_SESS_CACHE_SIZE, t, NULL)
     * @param sslCtx the SSL context
     * @param cacheSize the session cache size
     * @return > 0 if successful
     */
    public static long SSL_CTX_sess_set_cache_size(MemorySegment sslCtx, long cacheSize) {
        return SSL_CTX_ctrl(sslCtx, SSL_CTRL_SET_SESS_CACHE_SIZE(), cacheSize, MemorySegment.NULL);
    }


    /**
     * Get the session cache mode.
     * # define SSL_CTX_get_session_cache_mode(sslCtx) \
     *          SSL_CTX_ctrl(sslCtx, SSL_CTRL_GET_SESS_CACHE_MODE, 0, NULL)
     * @param sslCtx the SSL context
     * @return > 0 if successful
     */
    public static long SSL_CTX_get_session_cache_mode(MemorySegment sslCtx) {
        return SSL_CTX_ctrl(sslCtx, SSL_CTRL_GET_SESS_CACHE_MODE(), 0, MemorySegment.NULL);
    }


    /**
     * Set the session cache mode.
     * # define SSL_CTX_set_session_cache_mode(sslCtx, m) \
     *          SSL_CTX_ctrl(sslCtx, SSL_CTRL_SET_SESS_CACHE_MODE, m, NULL)
     * @param sslCtx the SSL context
     * @param cacheMode the cache mode, SSL_SESS_CACHE_OFF to disable
     * @return > 0 if successful
     */
    public static long SSL_CTX_set_session_cache_mode(MemorySegment sslCtx, long cacheMode) {
        return SSL_CTX_ctrl(sslCtx, SSL_CTRL_SET_SESS_CACHE_MODE(), cacheMode, MemorySegment.NULL);
    }


    /**
     * Set the certificate.
     * # define SSL_CTX_add0_chain_cert(sslCtx,x509) \
     *          SSL_CTX_ctrl(sslCtx, SSL_CTRL_CHAIN_CERT, 0, (char *)(x509))
     * @param sslCtx the SSL context
     * @param x509 the certificate
     * @return > 0 if successful
     */
    public static long SSL_CTX_add0_chain_cert(MemorySegment sslCtx, MemorySegment x509) {
        return SSL_CTX_ctrl(sslCtx, SSL_CTRL_CHAIN_CERT(), 0, x509);
    }


    /**
     * Set ticket keys.
     * # define SSL_CTX_set_tlsext_ticket_keys(ctx, keys, keylen) \
     *          SSL_CTX_ctrl((ctx),SSL_CTRL_SET_TLSEXT_TICKET_KEYS, (keylen), (keys))
     * @param sslCtx the SSL context
     * @param keys the keys
     * @param keyLength the length
     * @return > 0 if successful
     */
    public static long SSL_CTX_set_tlsext_ticket_keys(MemorySegment sslCtx, MemorySegment keys, long keyLength) {
        return SSL_CTX_ctrl(sslCtx, SSL_CTRL_SET_TLSEXT_TICKET_KEYS(), keyLength, keys);
    }


}


