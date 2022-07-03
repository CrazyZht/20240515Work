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
package org.apache.catalina.valves;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Pattern;

import jakarta.servlet.ServletException;

import org.apache.catalina.AccessLog;
import org.apache.catalina.Globals;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.StringUtils;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.http.parser.Host;

/**
 * <p>
 * This Valve currently supports two modes,<b>Legacy</b>and<b>RFC7239</b>. The default is
 * <b>Legacy</b>,and <b>RFC7239</b> mode can be enabled via {@link #setSupportRfc7239Only(boolean)}.
 * </p>
 * <p>
 * Tomcat port of <a href="https://httpd.apache.org/docs/trunk/mod/mod_remoteip.html">mod_remoteip</a>, this valve replaces the apparent
 * client remote IP address and hostname for the request with the IP address list presented by a proxy or a load balancer via a request
 * headers (e.g. "X-Forwarded-For" in legacy mode or Forwarded For directive in RFC7239 mode).
 * </p>
 * <p>
 * Another feature of this valve is to replace the apparent scheme (http/https) and server port with the scheme presented by a proxy or a
 * load balancer via a request header (e.g. "X-Forwarded-Proto" in legacy mode or Forwarded Proto directive in RFC7239 mode).
 * </p>
 * <p>
 * This legacy valve proceeds as follows:
 * </p>
 * <p>
 * If the incoming <code>request.getRemoteAddr()</code> matches the valve's list
 * of internal or trusted proxies:
 * </p>
 * <ul>
 * <li>Loop on the comma delimited list of IPs and hostnames passed by the preceding load balancer or proxy in the given request's Http
 * header named <code>$remoteIpHeader</code> (default value <code>x-forwarded-for</code>). Values are processed in right-to-left order.</li>
 * <li>For each ip/host of the list:
 * <ul>
 * <li>if it matches the internal proxies list, the ip/host is swallowed</li>
 * <li>if it matches the trusted proxies list, the ip/host is added to the created proxies header</li>
 * <li>otherwise, the ip/host is declared to be the remote ip and looping is stopped.</li>
 * </ul>
 * </li>
 * <li>If the request http header named <code>$protocolHeader</code> (e.g. <code>x-forwarded-proto</code>) consists only of forwards that match
 * <code>protocolHeaderHttpsValue</code> configuration parameter (default <code>https</code>) then <code>request.isSecure = true</code>,
 * <code>request.scheme = https</code> and <code>request.serverPort = 443</code>. Note that 443 can be overwritten with the
 * <code>$httpsServerPort</code> configuration parameter.</li>
 * <li>Mark the request with the attribute {@link Globals#REQUEST_FORWARDED_ATTRIBUTE} and value {@code Boolean.TRUE} to indicate
 * that this request has been forwarded by one or more proxies.</li>
 * </ul>
 * <table border="1">
 * <caption>Configuration parameters</caption>
 * <tr>
 * <th>RemoteIpValve property</th>
 * <th>Description</th>
 * <th>Equivalent mod_remoteip directive</th>
 * <th>Format</th>
 * <th>Default Value</th>
 * </tr>
 * <tr>
 * <td>remoteIpHeader</td>
 * <td>Name of the Http Header read by this valve that holds the list of traversed IP addresses starting from the requesting client</td>
 * <td>RemoteIPHeader</td>
 * <td>Compliant http header name</td>
 * <td>x-forwarded-for</td>
 * </tr>
 * <tr>
 * <td>internalProxies</td>
 * <td>Regular expression that matches the IP addresses of internal proxies.
 * If they appear in the <code>remoteIpHeader</code> value, they will be
 * trusted and will not appear
 * in the <code>proxiesHeader</code> value</td>
 * <td>RemoteIPInternalProxy</td>
 * <td>Regular expression (in the syntax supported by
 * {@link java.util.regex.Pattern java.util.regex})</td>
 * <td>10\.\d{1,3}\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|
 *     169\.254\.\d{1,3}\.\d{1,3}|127\.\d{1,3}\.\d{1,3}\.\d{1,3}|
 *     172\.1[6-9]{1}\.\d{1,3}\.\d{1,3}|172\.2[0-9]{1}\.\d{1,3}\.\d{1,3}|
 *     172\.3[0-1]{1}\.\d{1,3}\.\d{1,3}|
 *     0:0:0:0:0:0:0:1|::1
 *     <br>
 * By default, 10/8, 192.168/16, 169.254/16, 127/8, 172.16/12, and ::1 are allowed.</td>
 * </tr>
 * <tr>
 * <td>proxiesHeader</td>
 * <td>Name of the http header created by this valve to hold the list of proxies that have been processed in the incoming
 * <code>remoteIpHeader</code></td>
 * <td>proxiesHeader</td>
 * <td>Compliant http header name</td>
 * <td>x-forwarded-by</td>
 * </tr>
 * <tr>
 * <td>trustedProxies</td>
 * <td>Regular expression that matches the IP addresses of trusted proxies.
 * If they appear in the <code>remoteIpHeader</code> value, they will be
 * trusted and will appear in the <code>proxiesHeader</code> value</td>
 * <td>RemoteIPTrustedProxy</td>
 * <td>Regular expression (in the syntax supported by
 * {@link java.util.regex.Pattern java.util.regex})</td>
 * <td>&nbsp;</td>
 * </tr>
 * <tr>
 * <td>protocolHeader</td>
 * <td>Name of the http header read by this valve that holds the flag that this request </td>
 * <td>N/A</td>
 * <td>Compliant http header name like <code>X-Forwarded-Proto</code>, <code>X-Forwarded-Ssl</code> or <code>Front-End-Https</code></td>
 * <td><code>null</code></td>
 * </tr>
 * <tr>
 * <td>protocolHeaderHttpsValue</td>
 * <td>Value of the <code>protocolHeader</code> to indicate that it is an Https request</td>
 * <td>N/A</td>
 * <td>String like <code>https</code> or <code>ON</code></td>
 * <td><code>https</code></td>
 * </tr>
 * <tr>
 * <td>httpServerPort</td>
 * <td>Value returned by {@link jakarta.servlet.ServletRequest#getServerPort()} when the <code>protocolHeader</code> indicates <code>http</code> protocol</td>
 * <td>N/A</td>
 * <td>integer</td>
 * <td>80</td>
 * </tr>
 * <tr>
 * <td>httpsServerPort</td>
 * <td>Value returned by {@link jakarta.servlet.ServletRequest#getServerPort()} when the <code>protocolHeader</code> indicates <code>https</code> protocol</td>
 * <td>N/A</td>
 * <td>integer</td>
 * <td>443</td>
 * </tr>
 * </table>
 * <p>
 * This Valve may be attached to any Container, depending on the granularity of the filtering you wish to perform.
 * </p>
 * <p>
 * <strong>Regular expression vs. IP address blocks:</strong> <code>mod_remoteip</code> allows to use address blocks (e.g.
 * <code>192.168/16</code>) to configure <code>RemoteIPInternalProxy</code> and <code>RemoteIPTrustedProxy</code> ; as Tomcat doesn't have a
 * library similar to <a
 * href="https://apr.apache.org/docs/apr/1.3/group__apr__network__io.html#gb74d21b8898b7c40bf7fd07ad3eb993d">apr_ipsubnet_test</a>,
 * <code>RemoteIpValve</code> uses regular expression to configure <code>internalProxies</code> and <code>trustedProxies</code> in the same
 * fashion as {@link RequestFilterValve} does.
 * </p>
 * <hr>
 * <p>
 * <strong>Sample with internal proxies</strong>
 * </p>
 * <p>
 * 1.Legacy RemoteIpValve configuration:
 * </p>
 * <code>
 * &lt;Valve
 *   className="org.apache.catalina.valves.RemoteIpValve"
 *   internalProxies="192\.168\.0\.10|192\.168\.0\.11"
 *   remoteIpHeader="x-forwarded-for"
 *   proxiesHeader="x-forwarded-by"
 *   protocolHeader="x-forwarded-proto"
 *   /&gt;</code>
 * <table border="1">
 * <caption>Request Values</caption>
 * <tr>
 * <th>property</th>
 * <th>Value Before RemoteIpValve</th>
 * <th>Value After RemoteIpValve</th>
 * </tr>
 * <tr>
 * <td>request.remoteAddr</td>
 * <td>192.168.0.10</td>
 * <td>140.211.11.130</td>
 * </tr>
 * <tr>
 * <td>request.header['x-forwarded-for']</td>
 * <td>140.211.11.130, 192.168.0.10</td>
 * <td>null</td>
 * </tr>
 * <tr>
 * <td>request.header['x-forwarded-by']</td>
 * <td>null</td>
 * <td>null</td>
 * </tr>
 * <tr>
 * <td>request.header['x-forwarded-proto']</td>
 * <td>https</td>
 * <td>https</td>
 * </tr>
 * <tr>
 * <td>request.scheme</td>
 * <td>http</td>
 * <td>https</td>
 * </tr>
 * <tr>
 * <td>request.secure</td>
 * <td>false</td>
 * <td>true</td>
 * </tr>
 * <tr>
 * <td>request.serverPort</td>
 * <td>80</td>
 * <td>443</td>
 * </tr>
 * </table>
 * <p>
 * Note : <code>x-forwarded-by</code> header is null because only internal proxies as been traversed by the request.
 * <code>x-forwarded-by</code> is null because all the proxies are trusted or internal.
 * </p>
 *
 * <p>
 * 2.RFC7239 RemoteIpValve configuration:
 * </p>
 * <code>
 * &lt;Valve
 *   className="org.apache.catalina.valves.RemoteIpValve"
 *   internalProxies="192\.168\.0\.10|192\.168\.0\.11"
 *   supportRFC7239Only="true"
 *   /&gt;</code>
 * <table border="1">
 * <caption>Request Values</caption>
 * <tr>
 * <th>property</th>
 * <th>Value Before RemoteIpValve</th>
 * <th>Value After RemoteIpValve</th>
 * </tr>
 * <tr>
 * <td>request.remoteAddr</td>
 * <td>192.168.0.10</td>
 * <td>140.211.11.130</td>
 * </tr>
 * <tr>
 * <td>request.header['Forwarded']</td>
 * <td>for=140.211.11.130, for=192.168.0.10;proto=https</td>
 * <td>proto=https</td>
 * </tr>
 * <tr>
 * <td>request.scheme</td>
 * <td>http</td>
 * <td>https</td>
 * </tr>
 * <tr>
 * <td>request.secure</td>
 * <td>false</td>
 * <td>true</td>
 * </tr>
 * <tr>
 * <td>request.serverPort</td>
 * <td>80</td>
 * <td>443</td>
 * </tr>
 * </table>
 * <hr>
 * <p>
 * <strong>Sample with trusted proxies</strong>
 * </p>
 * <p>
 * 1.Legacy RemoteIpValve configuration:
 * </p>
 * <code>
 * &lt;Valve
 *   className="org.apache.catalina.valves.RemoteIpValve"
 *   internalProxies="192\.168\.0\.10|192\.168\.0\.11"
 *   remoteIpHeader="x-forwarded-for"
 *   proxiesHeader="x-forwarded-by"
 *   trustedProxies="proxy1|proxy2"
 *   /&gt;</code>
 * <table border="1">
 * <caption>Request Values</caption>
 * <tr>
 * <th>property</th>
 * <th>Value Before RemoteIpValve</th>
 * <th>Value After RemoteIpValve</th>
 * </tr>
 * <tr>
 * <td>request.remoteAddr</td>
 * <td>192.168.0.10</td>
 * <td>140.211.11.130</td>
 * </tr>
 * <tr>
 * <td>request.header['x-forwarded-for']</td>
 * <td>140.211.11.130, proxy1, proxy2</td>
 * <td>null</td>
 * </tr>
 * <tr>
 * <td>request.header['x-forwarded-by']</td>
 * <td>null</td>
 * <td>proxy1, proxy2</td>
 * </tr>
 * </table>
 * <p>
 * Note : <code>proxy1</code> and <code>proxy2</code> are both trusted proxies that come in <code>x-forwarded-for</code> header, they both
 * are migrated in <code>x-forwarded-by</code> header. <code>x-forwarded-by</code> is null because all the proxies are trusted or internal.
 * </p>
 * <p>
 * 2.RFC7239 RemoteIpValve configuration:
 * </p>
 * <code>
 * &lt;Valve
 *   className="org.apache.catalina.valves.RemoteIpValve"
 *   internalProxies="192\.168\.0\.10|192\.168\.0\.11"
 *   trustedProxies="proxy1|proxy2"
 *   supportRFC7239Only="true"
 *   /&gt;</code>
 * <table border="1">
 * <caption>Request Values</caption>
 * <tr>
 * <th>property</th>
 * <th>Value Before RemoteIpValve</th>
 * <th>Value After RemoteIpValve</th>
 * </tr>
 * <tr>
 * <td>request.remoteAddr</td>
 * <td>192.168.0.10</td>
 * <td>140.211.11.130</td>
 * </tr>
 * <tr>
 * <td>request.header['Forwarded']</td>
 * <td>for=140.211.11.130, for=proxy1, for=proxy2</td>
 * <td>by=proxy1,by=proxy2</td>
 * </tr>
 * </table>
 * <hr>
 * <p>
 * <strong>Sample with internal and trusted proxies</strong>
 * </p>
 * <p>
 * RemoteIpValve configuration:
 * </p>
 * <code>
 * &lt;Valve
 *   className="org.apache.catalina.valves.RemoteIpValve"
 *   internalProxies="192\.168\.0\.10|192\.168\.0\.11"
 *   remoteIpHeader="x-forwarded-for"
 *   proxiesHeader="x-forwarded-by"
 *   trustedProxies="proxy1|proxy2"
 *   /&gt;</code>
 * <table border="1">
 * <caption>Request Values</caption>
 * <tr>
 * <th>property</th>
 * <th>Value Before RemoteIpValve</th>
 * <th>Value After RemoteIpValve</th>
 * </tr>
 * <tr>
 * <td>request.remoteAddr</td>
 * <td>192.168.0.10</td>
 * <td>140.211.11.130</td>
 * </tr>
 * <tr>
 * <td>request.header['x-forwarded-for']</td>
 * <td>140.211.11.130, proxy1, proxy2, 192.168.0.10</td>
 * <td>null</td>
 * </tr>
 * <tr>
 * <td>request.header['x-forwarded-by']</td>
 * <td>null</td>
 * <td>proxy1, proxy2</td>
 * </tr>
 * </table>
 * <p>
 * Note : <code>proxy1</code> and <code>proxy2</code> are both trusted proxies that come in <code>x-forwarded-for</code> header, they both
 * are migrated in <code>x-forwarded-by</code> header. As <code>192.168.0.10</code> is an internal proxy, it does not appear in
 * <code>x-forwarded-by</code>. <code>x-forwarded-by</code> is null because all the proxies are trusted or internal.
 * </p>
 * <hr>
 * <p>
 * <strong>Sample with an untrusted proxy</strong>
 * </p>
 * <p>
 * RemoteIpValve configuration:
 * </p>
 * <code>
 * &lt;Valve
 *   className="org.apache.catalina.valves.RemoteIpValve"
 *   internalProxies="192\.168\.0\.10|192\.168\.0\.11"
 *   remoteIpHeader="x-forwarded-for"
 *   proxiesHeader="x-forwarded-by"
 *   trustedProxies="proxy1|proxy2"
 *   /&gt;</code>
 * <table border="1">
 * <caption>Request Values</caption>
 * <tr>
 * <th>property</th>
 * <th>Value Before RemoteIpValve</th>
 * <th>Value After RemoteIpValve</th>
 * </tr>
 * <tr>
 * <td>request.remoteAddr</td>
 * <td>192.168.0.10</td>
 * <td>untrusted-proxy</td>
 * </tr>
 * <tr>
 * <td>request.header['x-forwarded-for']</td>
 * <td>140.211.11.130, untrusted-proxy, proxy1</td>
 * <td>140.211.11.130</td>
 * </tr>
 * <tr>
 * <td>request.header['x-forwarded-by']</td>
 * <td>null</td>
 * <td>proxy1</td>
 * </tr>
 * </table>
 * <p>
 * Note : <code>x-forwarded-by</code> holds the trusted proxy <code>proxy1</code>. <code>x-forwarded-by</code> holds
 * <code>140.211.11.130</code> because <code>untrusted-proxy</code> is not trusted and thus, we cannot trust that
 * <code>untrusted-proxy</code> is the actual remote ip. <code>request.remoteAddr</code> is <code>untrusted-proxy</code> that is an IP
 * verified by <code>proxy1</code>.
 * </p>
 */
public class RemoteIpValve extends ValveBase {

    /**
     * {@link Pattern} for a comma delimited string that support whitespace characters
     */
    private static final Pattern commaSeparatedValuesPattern = Pattern.compile("\\s*,\\s*");

    /**
     * Logger
     */
    private static final Log log = LogFactory.getLog(RemoteIpValve.class);


    /**
     * Header specified by RFC7239
     */
    private static final String forwardedHeader = "Forwarded";

    /**
     * The four directives specified in RFC 7239
     */
    private static final String BY = "by";
    private static final String FOR = "for";
    private static final String HOST = "host";
    private static final String PROTO = "proto";

    /**
     * Convert a given comma delimited String into an array of String
     * @param commaDelimitedStrings The string to convert
     * @return array of String (non <code>null</code>)
     */
    protected static String[] commaDelimitedListToStringArray(String commaDelimitedStrings) {
        return (commaDelimitedStrings == null || commaDelimitedStrings.length() == 0) ? new String[0] : commaSeparatedValuesPattern
            .split(commaDelimitedStrings);
    }

    private String hostHeader = null;

    private boolean changeLocalName = false;

    /**
     * @see #setSupportRfc7239Only(boolean)
     */
    private boolean supportRfc7239Only = false;

    /**
     * @see #setHttpServerPort(int)
     */
    private int httpServerPort = 80;

    /**
     * @see #setHttpsServerPort(int)
     */
    private int httpsServerPort = 443;

    private String portHeader = null;

    private boolean changeLocalPort = false;

    /**
     * @see #setInternalProxies(String)
     */
    private Pattern internalProxies = Pattern.compile(
            "10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|" +
            "192\\.168\\.\\d{1,3}\\.\\d{1,3}|" +
            "169\\.254\\.\\d{1,3}\\.\\d{1,3}|" +
            "127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|" +
            "172\\.1[6-9]{1}\\.\\d{1,3}\\.\\d{1,3}|" +
            "172\\.2[0-9]{1}\\.\\d{1,3}\\.\\d{1,3}|" +
            "172\\.3[0-1]{1}\\.\\d{1,3}\\.\\d{1,3}|" +
            "0:0:0:0:0:0:0:1|::1");

    /**
     * @see #setProtocolHeader(String)
     */
    private String protocolHeader = "X-Forwarded-Proto";

    /**
     * @see #setProtocolHeaderHttpsValue(String)
     */
    private String protocolHeaderHttpsValue = "https";

    /**
     * @see #setProxiesHeader(String)
     */
    private String proxiesHeader = "X-Forwarded-By";

    /**
     * @see #setRemoteIpHeader(String)
     */
    private String remoteIpHeader = "X-Forwarded-For";

    /**
     * @see #setRequestAttributesEnabled(boolean)
     */
    private boolean requestAttributesEnabled = true;

    /**
     * @see RemoteIpValve#setTrustedProxies(String)
     */
    private Pattern trustedProxies = null;


    /**
     * Default constructor that ensures {@link ValveBase#ValveBase(boolean)} is
     * called with <code>true</code>.
     */
    public RemoteIpValve() {
        // Async requests are supported with this valve
        super(true);
    }

    /**
     * Use legacy mode if not specified, otherwise use RFC7239
     * @param rfc7239Only Whether to use RFC7239
     */
    public void setSupportRfc7239Only(boolean rfc7239Only) {
        this.supportRfc7239Only = rfc7239Only;
    }

    /**
     * Obtain the name of the HTTP header used to override the value returned
     * by {@link Request#getServerName()} and (optionally depending on {link
     * {@link #isChangeLocalName()} {@link Request#getLocalName()}.
     *
     * @return  The HTTP header name
     */
    public String getHostHeader() {
        return hostHeader;
    }

    /**
     * Set the name of the HTTP header used to override the value returned
     * by {@link Request#getServerName()} and (optionally depending on {link
     * {@link #isChangeLocalName()} {@link Request#getLocalName()}.
     *
     * @param   hostHeader  The HTTP header name
     */
    public void setHostHeader(String hostHeader) {
        this.hostHeader = hostHeader;
    }

    public boolean isChangeLocalName() {
        return changeLocalName;
    }

    public void setChangeLocalName(boolean changeLocalName) {
        this.changeLocalName = changeLocalName;
    }

    public int getHttpServerPort() {
        return httpServerPort;
    }

    public int getHttpsServerPort() {
        return httpsServerPort;
    }

    /**
     * Obtain the name of the HTTP header used to override the value returned
     * by {@link Request#getServerPort()} and (optionally depending on {link
     * {@link #isChangeLocalPort()} {@link Request#getLocalPort()}.
     *
     * @return  The HTTP header name
     */
    public String getPortHeader() {
        return portHeader;
    }

    /**
     * Set the name of the HTTP header used to override the value returned
     * by {@link Request#getServerPort()} and (optionally depending on {link
     * {@link #isChangeLocalPort()} {@link Request#getLocalPort()}.
     *
     * @param   portHeader  The HTTP header name
     */
    public void setPortHeader(String portHeader) {
        this.portHeader = portHeader;
    }

    public boolean isChangeLocalPort() {
        return changeLocalPort;
    }

    public void setChangeLocalPort(boolean changeLocalPort) {
        this.changeLocalPort = changeLocalPort;
    }

    /**
     * @see #setInternalProxies(String)
     * @return Regular expression that defines the internal proxies
     */
    public String getInternalProxies() {
        if (internalProxies == null) {
            return null;
        }
        return internalProxies.toString();
    }

    /**
     * @see #setProtocolHeader(String)
     * @return the protocol header (e.g. "X-Forwarded-Proto")
     */
    public String getProtocolHeader() {
        return protocolHeader;
    }

    /**
     * @see RemoteIpValve#setProtocolHeaderHttpsValue(String)
     * @return the value of the protocol header for incoming https request (e.g. "https")
     */
    public String getProtocolHeaderHttpsValue() {
        return protocolHeaderHttpsValue;
    }

    /**
     * @see #setProxiesHeader(String)
     * @return the proxies header name (e.g. "X-Forwarded-By")
     */
    public String getProxiesHeader() {
        return proxiesHeader;
    }

    /**
     * @see #setRemoteIpHeader(String)
     * @return the remote IP header name (e.g. "X-Forwarded-For")
     */
    public String getRemoteIpHeader() {
        return remoteIpHeader;
    }

    /**
     * @see #setRequestAttributesEnabled(boolean)
     * @return <code>true</code> if the attributes will be logged, otherwise
     *         <code>false</code>
     */
    public boolean getRequestAttributesEnabled() {
        return requestAttributesEnabled;
    }

    /**
     * @see #setTrustedProxies(String)
     * @return Regular expression that defines the trusted proxies
     */
    public String getTrustedProxies() {
        if (trustedProxies == null) {
            return null;
        }
        return trustedProxies.toString();
    }

    public boolean isInternalProxy(String originalRemoteAddr) {
        return internalProxies != null &&
            internalProxies.matcher(originalRemoteAddr).matches();
    }

    public boolean isTrustedProxy(String originalRemoteAddr) {
        return (trustedProxies != null &&
            trustedProxies.matcher(originalRemoteAddr).matches());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        final String originalRemoteAddr = request.getRemoteAddr();
        final String originalRemoteHost = request.getRemoteHost();
        final String originalScheme = request.getScheme();
        final boolean originalSecure = request.isSecure();
        final String originalServerName = request.getServerName();
        final String originalLocalName = isChangeLocalName() ? request.getLocalName() : null;
        final int originalServerPort = request.getServerPort();
        final int originalLocalPort = request.getLocalPort();
        final String originalProxiesHeader = request.getHeader(proxiesHeader);
        final String originalRemoteIpHeader = request.getHeader(remoteIpHeader);
        Map<String,List<String>> originalForwardedValue = null;

        boolean isInternal = isInternalProxy(originalRemoteAddr);

        if (isInternal || isTrustedProxy(originalRemoteAddr)) {
            Map<String, List<String>> forwardedValue = new HashMap<>();
            String remoteIp = null;
            LinkedList<String> proxiesHeaderValue = new LinkedList<>();
            String[] remoteIpHeaderValue;
            if (supportRfc7239Only) {
                for (Enumeration<String> e = request.getHeaders(forwardedHeader); e.hasMoreElements();) {
                    parseRfc7239(e.nextElement(), forwardedValue);
                }
                if (forwardedValue.containsKey(FOR)) {
                    remoteIpHeaderValue = forwardedValue.get(FOR).toArray(new String[0]);
                } else {
                    remoteIpHeaderValue = new String[0];
                }
                originalForwardedValue = new HashMap<>(forwardedValue);
            } else {
                StringBuilder concatRemoteIpHeaderValue = new StringBuilder();
                for (Enumeration<String> e = request.getHeaders(remoteIpHeader); e.hasMoreElements();) {
                    if (concatRemoteIpHeaderValue.length() > 0) {
                        concatRemoteIpHeaderValue.append(", ");
                    }
                    concatRemoteIpHeaderValue.append(e.nextElement());
                }
                remoteIpHeaderValue = commaDelimitedListToStringArray(concatRemoteIpHeaderValue.toString());
            }

            int idx;
            if (!isInternal) {
                proxiesHeaderValue.addFirst(originalRemoteAddr);
            }
            // loop on remoteIpHeaderValue to find the first trusted remote ip and to build the proxies chain
            for (idx = remoteIpHeaderValue.length - 1; idx >= 0; idx--) {
                String currentRemoteIp = remoteIpHeaderValue[idx];
                remoteIp = currentRemoteIp;
                if (isInternalProxy(currentRemoteIp)) {
                    // do nothing, internalProxies IPs are not appended to the
                } else if (isTrustedProxy(currentRemoteIp)) {
                    proxiesHeaderValue.addFirst(currentRemoteIp);
                } else {
                    idx--; // decrement idx because break statement doesn't do it
                    break;
                }
            }
            // continue to loop on remoteIpHeaderValue to build the new value of the remoteIpHeader
            LinkedList<String> newRemoteIpHeaderValue = new LinkedList<>();
            for (; idx >= 0; idx--) {
                String currentRemoteIp = remoteIpHeaderValue[idx];
                newRemoteIpHeaderValue.addFirst(currentRemoteIp);
            }
            if (remoteIp != null) {

                request.setRemoteAddr(remoteIp);
                if (request.getConnector().getEnableLookups()) {
                    // This isn't a lazy lookup but that would be a little more
                    // invasive - mainly in Request.getRemoteHost() - and if
                    // enableLookups is true it seems reasonable that the
                    // hotsname will be required so look it up here.
                    try {
                        InetAddress inetAddress = InetAddress.getByName(remoteIp);
                        // We know we need a DNS look up so use getCanonicalHostName()
                        request.setRemoteHost(inetAddress.getCanonicalHostName());
                    } catch (UnknownHostException e) {
                        log.debug(sm.getString("remoteIpValve.invalidRemoteAddress", remoteIp), e);
                        request.setRemoteHost(remoteIp);
                    }
                } else {
                    request.setRemoteHost(remoteIp);
                }

                if (supportRfc7239Only) {
                    if (proxiesHeaderValue.size() == 0) {
                        forwardedValue.remove(BY);
                    } else {
                        forwardedValue.put(BY, proxiesHeaderValue);
                    }

                    if (newRemoteIpHeaderValue.size() == 0) {
                        forwardedValue.remove(FOR);
                    } else {
                        forwardedValue.put(FOR, newRemoteIpHeaderValue);
                    }
                    request.getCoyoteRequest().getMimeHeaders().setValue(forwardedHeader).setString(spliceRfc7239(forwardedValue));
                } else {
                    String commaDelimitedRemoteIpHeaderValue = StringUtils.join(newRemoteIpHeaderValue);
                    String commaDelimitedListOfProxies = StringUtils.join(proxiesHeaderValue);
                    setProxiesHeaderAndRemoteIpHeader(request, commaDelimitedListOfProxies, commaDelimitedRemoteIpHeaderValue);
                }

            }

            if (supportRfc7239Only) {
                handleForwardedProtoAndHost(request, forwardedValue);
            } else {

                if (protocolHeader != null) {
                    String protocolHeaderValue = request.getHeader(protocolHeader);
                    if (protocolHeaderValue != null) {
                        int port = 0;
                        if (isForwardedProtoHeaderValueSecure(protocolHeaderValue)) {
                            request.setSecure(true);
                            request.getCoyoteRequest().scheme().setString("https");
                            port = httpsServerPort;
                        } else {
                            request.setSecure(false);
                            request.getCoyoteRequest().scheme().setString("http");
                            port = httpServerPort;
                        }

                        if (portHeader != null) {
                            String portHeaderValue = request.getHeader(portHeader);
                            if (portHeaderValue != null) {
                                try {
                                    port = Integer.parseInt(portHeaderValue);
                                } catch (NumberFormatException nfe) {
                                    if (log.isDebugEnabled()) {
                                        log.debug(sm.getString(
                                            "remoteIpValve.invalidPortHeader",
                                            portHeaderValue, portHeader), nfe);
                                    }
                                }
                            }
                        }
                        setPort(request, port);
                    }
                }

                if (hostHeader != null) {
                    String hostHeaderValue = request.getHeader(hostHeader);
                    if (hostHeaderValue != null) {
                        try {
                            int portIndex = Host.parse(hostHeaderValue);
                            if (portIndex > -1) {
                                log.debug(sm.getString("remoteIpValve.invalidHostWithPort", hostHeaderValue, hostHeader));
                                hostHeaderValue = hostHeaderValue.substring(0, portIndex);
                            }

                            request.getCoyoteRequest().serverName().setString(hostHeaderValue);
                            if (isChangeLocalName()) {
                                request.getCoyoteRequest().localName().setString(hostHeaderValue);
                            }

                        } catch (IllegalArgumentException iae) {
                            log.debug(sm.getString("remoteIpValve.invalidHostHeader", hostHeaderValue, hostHeader));
                        }
                    }

                }

           }

            request.setAttribute(Globals.REQUEST_FORWARDED_ATTRIBUTE, Boolean.TRUE);

            if (log.isDebugEnabled()) {
                log.debug("Incoming request " + request.getRequestURI() + " with originalRemoteAddr [" + originalRemoteAddr +
                          "], originalRemoteHost=[" + originalRemoteHost + "], originalSecure=[" + originalSecure +
                          "], originalScheme=[" + originalScheme + "], originalServerName=[" + originalServerName +
                          "], originalServerPort=[" + originalServerPort +
                          "] will be seen as newRemoteAddr=[" + request.getRemoteAddr() +
                          "], newRemoteHost=[" + request.getRemoteHost() + "], newSecure=[" + request.isSecure() +
                          "], newScheme=[" + request.getScheme() + "], newServerName=[" + request.getServerName() +
                          "], newServerPort=[" + request.getServerPort() + "]");
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Skip RemoteIpValve for request " + request.getRequestURI() + " with originalRemoteAddr '"
                        + request.getRemoteAddr() + "'");
            }
        }
        if (requestAttributesEnabled) {
            request.setAttribute(AccessLog.REMOTE_ADDR_ATTRIBUTE,
                    request.getRemoteAddr());
            request.setAttribute(Globals.REMOTE_ADDR_ATTRIBUTE,
                    request.getRemoteAddr());
            request.setAttribute(AccessLog.REMOTE_HOST_ATTRIBUTE,
                    request.getRemoteHost());
            request.setAttribute(AccessLog.PROTOCOL_ATTRIBUTE,
                    request.getProtocol());
            request.setAttribute(AccessLog.SERVER_NAME_ATTRIBUTE,
                    request.getServerName());
            request.setAttribute(AccessLog.SERVER_PORT_ATTRIBUTE,
                    Integer.valueOf(request.getServerPort()));
        }
        try {
            getNext().invoke(request, response);
        } finally {
            if (!request.isAsync()) {
                request.setRemoteAddr(originalRemoteAddr);
                request.setRemoteHost(originalRemoteHost);
                request.setSecure(originalSecure);
                request.getCoyoteRequest().scheme().setString(originalScheme);
                request.getCoyoteRequest().serverName().setString(originalServerName);
                if (isChangeLocalName()) {
                    request.getCoyoteRequest().localName().setString(originalLocalName);
                }
                request.setServerPort(originalServerPort);
                request.setLocalPort(originalLocalPort);

                MimeHeaders headers = request.getCoyoteRequest().getMimeHeaders();
                if (supportRfc7239Only && originalForwardedValue != null) {
                    headers.setValue(forwardedHeader).setString(spliceRfc7239(originalForwardedValue));
                } else {
                    setProxiesHeaderAndRemoteIpHeader(request, originalProxiesHeader, originalRemoteIpHeader);
                }
            }
        }
    }

    private void setProxiesHeaderAndRemoteIpHeader(Request request, String proxiesHeaderValue, String remoteIpHeaderValue) {
        MimeHeaders headers = request.getCoyoteRequest().getMimeHeaders();
        if (proxiesHeaderValue == null || proxiesHeaderValue.length() == 0) {
            headers.removeHeader(proxiesHeader);
        } else {
            headers.setValue(proxiesHeader).setString(proxiesHeaderValue);
        }

        if (remoteIpHeaderValue == null || remoteIpHeaderValue.length() == 0) {
            headers.removeHeader(remoteIpHeader);
        } else {
            headers.setValue(remoteIpHeader).setString(remoteIpHeaderValue);
        }
    }

    /**
     * Splice rfc7239 directives
     * @param directives  Forwarded Directive -> [v1,v2...]
     * @return rfc7239 Forwarded value
     */
    public static String spliceRfc7239(Map<String, List<String>> directives) {
        StringBuilder result = new StringBuilder();
        boolean semicolonFirst = true;
        for (Map.Entry<String, List<String>> entry : directives.entrySet()) {
            if (semicolonFirst) {
                semicolonFirst = false;
            } else {
                result.append(";");
            }
            switch (entry.getKey().toLowerCase(Locale.ROOT)) {
                case FOR:
                case HOST:
                case BY:
                    StringBuilder forwardedPair = new StringBuilder();
                    boolean first = true;
                    // ipv6 need to be wrapped in quotes
                    for (String value : entry.getValue()) {
                        boolean isIpv6 = false;
                        if (value.startsWith("[")) {
                            isIpv6 = true;
                        } else {
                            // Converting legacy ipv6 formats to ipv6 as specified in RFC 7239.
                            // eg: 2400:dd01:103a:4041::101:8080 -> "[2400:dd01:103a:4041::101]:8080"
                            int lastColonIndex = value.lastIndexOf(":");
                            if (lastColonIndex > 0) {
                                int colonIndex = value.indexOf(":");
                                // ipv6
                                if (colonIndex != lastColonIndex) {
                                    value = "[" + value.substring(0, lastColonIndex) + "]" + value.substring(lastColonIndex);
                                    isIpv6 = true;
                                }
                            }
                        }
                        if (first) {
                            first = false;
                        } else {
                            forwardedPair.append(", ");
                        }
                        forwardedPair.append(entry.getKey()).append("=");
                        if (isIpv6) {
                            forwardedPair.append("\"").append(value).append("\"");
                        } else {
                            forwardedPair.append(value);
                        }
                    }
                    result.append(forwardedPair);
                    break;
                case PROTO:
                default:
                    forwardedPair = new StringBuilder();
                    first = true;
                    for (String value : entry.getValue()) {
                        if (first) {
                            first = false;
                        } else {
                            forwardedPair.append(", ");
                        }
                        forwardedPair.append(entry.getKey()).append("=").append(value);
                    }
                    result.append(forwardedPair);
            }
        }
        return result.toString();
    }

    /**
     * Parse rfc7239 Forwarded values, only the 4 commands specified in rfc7239 are parsed,
     * user-defined information will not be processed, but put directly into the result.
     *
     * @param forwardedValue rfc7239 Forwarded values
     * @param result         Forwarded Directive -> [v1,v2...]
     */
    public static void parseRfc7239(String forwardedValue, Map<String, List<String>> result) {
        if (forwardedValue == null || forwardedValue.length() == 0) {
            return;
        }
        char[] chars = forwardedValue.toCharArray();
        StringBuilder key = new StringBuilder();
        StringBuilder value = new StringBuilder();
        boolean isKey = true;
        for (char c : chars) {
            switch (c) {
                case '=':
                    isKey = false;
                    break;
                case ',':
                case ';':
                    isKey = true;
                    parseForwardedDirective(key.toString(), value.toString(), result);
                    key = new StringBuilder();
                    value = new StringBuilder();
                    break;
                case ' ':
                case '\"':
                    break;
                default:
                    if (isKey) {
                        key.append(c);
                    } else {
                        value.append(c);
                    }
                    break;
            }
        }
        if (key.length() != 0 && value.length() != 0) {
            parseForwardedDirective(key.toString(), value.toString(), result);
        }
    }

    public static void parseForwardedDirective(String directive, String value, Map<String, List<String>> result) {
        directive = directive.toLowerCase(Locale.ROOT);
        switch (directive) {
            case FOR:
                // To reuse the legacy matching logic for InternalProxies and TrustedProxies
                // so that the ipv6 format specified by RFC 7239 is converted to legacy format.
                // Only for Forwarded For Directive.
                // eg: "[2400:dd01:103a:4041::101]:8080" -> 2400:dd01:103a:4041::101:8080
                if (value.startsWith("[")) {
                    int portIndex = Host.parse(value);
                    if (portIndex > -1) {
                        String ip = value.substring(1, portIndex - 1);
                        String port = value.substring(portIndex);
                        if (port.length() >= 1) {
                            value = ip + port;
                        } else {
                            value = ip;
                        }
                    }
                }
                if (value.startsWith("_") || "unknown".equals(value)) {
                    return;
                }
            case BY:
            case HOST:
                if (value.startsWith("_") || "unknown".equals(value)) {
                    return;
                }
                break;
            case PROTO:
                // Only have two types, so far.
                if (!"http".equalsIgnoreCase(value) && !"https".equalsIgnoreCase(value)) {
                    value = null;
                }
                break;
            default:
        }
        if (value != null) {
            List<String> item = result.get(directive);
            if (item == null) {
                item = new LinkedList<>();
                result.put(directive, item);
            }
            item.add(value);
        }
    }
    /*
     * Considers the value to be secure if it exclusively holds forwards for
     * {@link #protocolHeaderHttpsValue}.
     */
    private boolean isForwardedProtoHeaderValueSecure(String protocolHeaderValue) {
        if (!protocolHeaderValue.contains(",")) {
            return protocolHeaderHttpsValue.equalsIgnoreCase(protocolHeaderValue);
        }
        String[] forwardedProtocols = commaDelimitedListToStringArray(protocolHeaderValue);
        return isForwardedProtoHeaderValueSecure(forwardedProtocols);
    }

    private boolean isForwardedProtoHeaderValueSecure(String[] forwardedProtocols) {
        if (forwardedProtocols.length == 0) {
            return false;
        }
        for (String forwardedProtocol : forwardedProtocols) {
            if (!protocolHeaderHttpsValue.equalsIgnoreCase(forwardedProtocol)) {
                return false;
            }
        }
        return true;
    }

    private void handleForwardedProtoAndHost(Request request, Map<String, List<String>> forwardedValue) {
        if (forwardedValue.containsKey(PROTO)) {
            List<String> forwardedProtos = forwardedValue.get(PROTO);
            if (isForwardedProtoHeaderValueSecure(forwardedProtos.toArray(new String[0]))) {
                request.setSecure(true);
                request.getCoyoteRequest().scheme().setString("https");
                setPort(request, httpsServerPort);
            } else {
                request.setSecure(false);
                request.getCoyoteRequest().scheme().setString("http");
                setPort(request, httpServerPort);
            }
        }

        if (forwardedValue.containsKey(HOST)) {
            // There must be only one.
            String forwardedHost = forwardedValue.get(HOST).get(0);
            String hostHeaderValue = null;
            try {
                int portIndex = Host.parse(forwardedHost);
                if (portIndex > -1) {
                    hostHeaderValue = forwardedHost.substring(0, portIndex);
                    String portStr = forwardedHost.substring(portIndex + 1);
                    // mybe it is a obfport
                    if (!portStr.startsWith("_")) {
                        setPort(request, Integer.parseInt(portStr));
                    }
                } else {
                    hostHeaderValue = forwardedHost;
                }

                request.getCoyoteRequest().serverName().setString(hostHeaderValue);
                if (isChangeLocalName()) {
                    request.getCoyoteRequest().localName().setString(hostHeaderValue);
                }

            } catch (Exception e) {
                if (log.isDebugEnabled()) {
                    log.debug("Invalid value [" + forwardedHost + "] found in HTTP Forwarded Host");
                }
            }
        }
    }

    private void setPort(Request request, int port) {
        request.setServerPort(port);
        if (isChangeLocalPort()) {
            request.setLocalPort(port);
        }
    }

    /**
     * <p>
     * Server Port value if the {@link #protocolHeader} is not <code>null</code> and does not indicate HTTP
     * </p>
     * <p>
     * Default value : 80
     * </p>
     * @param httpServerPort The server port
     */
    public void setHttpServerPort(int httpServerPort) {
        this.httpServerPort = httpServerPort;
    }

    /**
     * <p>
     * Server Port value if the {@link #protocolHeader} indicates HTTPS
     * </p>
     * <p>
     * Default value : 443
     * </p>
     * @param httpsServerPort The server port
     */
    public void setHttpsServerPort(int httpsServerPort) {
        this.httpsServerPort = httpsServerPort;
    }

    /**
     * <p>
     * Regular expression that defines the internal proxies.
     * </p>
     * <p>
     * Default value : 10\.\d{1,3}\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|169\.254.\d{1,3}.\d{1,3}|127\.\d{1,3}\.\d{1,3}\.\d{1,3}|0:0:0:0:0:0:0:1
     * </p>
     * @param internalProxies The proxy regular expression
     */
    public void setInternalProxies(String internalProxies) {
        if (internalProxies == null || internalProxies.length() == 0) {
            this.internalProxies = null;
        } else {
            this.internalProxies = Pattern.compile(internalProxies);
        }
    }

    /**
     * <p>
     * Header that holds the incoming protocol, usually named <code>X-Forwarded-Proto</code>. If <code>null</code>, request.scheme and
     * request.secure will not be modified.
     * </p>
     * <p>
     * Default value : <code>null</code>
     * </p>
     * @param protocolHeader The header name
     */
    public void setProtocolHeader(String protocolHeader) {
        this.protocolHeader = protocolHeader;
    }

    /**
     * <p>
     * Case insensitive value of the protocol header to indicate that the incoming http request uses SSL.
     * </p>
     * <p>
     * Default value : <code>https</code>
     * </p>
     * @param protocolHeaderHttpsValue The header name
     */
    public void setProtocolHeaderHttpsValue(String protocolHeaderHttpsValue) {
        this.protocolHeaderHttpsValue = protocolHeaderHttpsValue;
    }

    /**
     * <p>
     * The proxiesHeader directive specifies a header into which mod_remoteip will collect a list of all of the intermediate client IP
     * addresses trusted to resolve the actual remote IP. Note that intermediate RemoteIPTrustedProxy addresses are recorded in this header,
     * while any intermediate RemoteIPInternalProxy addresses are discarded.
     * </p>
     * <p>
     * Name of the http header that holds the list of trusted proxies that has been traversed by the http request.
     * </p>
     * <p>
     * The value of this header can be comma delimited.
     * </p>
     * <p>
     * Default value : <code>X-Forwarded-By</code>
     * </p>
     * @param proxiesHeader The header name
     */
    public void setProxiesHeader(String proxiesHeader) {
        this.proxiesHeader = proxiesHeader;
    }

    /**
     * <p>
     * Name of the http header from which the remote ip is extracted.
     * </p>
     * <p>
     * The value of this header can be comma delimited.
     * </p>
     * <p>
     * Default value : <code>X-Forwarded-For</code>
     * </p>
     *
     * @param remoteIpHeader The header name
     */
    public void setRemoteIpHeader(String remoteIpHeader) {
        this.remoteIpHeader = remoteIpHeader;
    }

    /**
     * Should this valve set request attributes for IP address, Hostname,
     * protocol and port used for the request? This are typically used in
     * conjunction with the {@link AccessLog} which will otherwise log the
     * original values. Default is <code>true</code>.
     *
     * The attributes set are:
     * <ul>
     * <li>org.apache.catalina.AccessLog.RemoteAddr</li>
     * <li>org.apache.catalina.AccessLog.RemoteHost</li>
     * <li>org.apache.catalina.AccessLog.Protocol</li>
     * <li>org.apache.catalina.AccessLog.ServerPort</li>
     * <li>org.apache.tomcat.remoteAddr</li>
     * </ul>
     *
     * @param requestAttributesEnabled  <code>true</code> causes the attributes
     *                                  to be set, <code>false</code> disables
     *                                  the setting of the attributes.
     */
    public void setRequestAttributesEnabled(boolean requestAttributesEnabled) {
        this.requestAttributesEnabled = requestAttributesEnabled;
    }

    /**
     * <p>
     * Regular expression defining proxies that are trusted when they appear in
     * the {@link #remoteIpHeader} header.
     * </p>
     * <p>
     * Default value : empty list, no external proxy is trusted.
     * </p>
     * @param trustedProxies The regular expression
     */
    public void setTrustedProxies(String trustedProxies) {
        if (trustedProxies == null || trustedProxies.length() == 0) {
            this.trustedProxies = null;
        } else {
            this.trustedProxies = Pattern.compile(trustedProxies);
        }
    }
}
