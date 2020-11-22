/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.jenkinsci.main.modules.cli.auth.ssh;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.Charset;


/**
 * This class help to encode RSA and DSA keys to OpenSSH format
 * Methods came from Apache Mina SSH classes
 * https://github.com/apache/mina-sshd/blob/master/sshd-common/src/main/java/org/apache/sshd/common/config/keys/impl/DSSPublicKeyEntryDecoder.java
 * https://github.com/apache/mina-sshd/blob/master/sshd-common/src/main/java/org/apache/sshd/common/config/keys/impl/RSAPublicKeyDecoder.java
 * https://github.com/apache/mina-sshd/blob/master/sshd-common/src/main/java/org/apache/sshd/common/config/keys/KeyEntryResolver.java
 */
public class KeyEncodeHelper {

    public static int encodeBigInt(OutputStream s, BigInteger v) throws IOException {
        byte[] bytes = v.toByteArray();
        return writeRLEBytes(s, bytes, 0, bytes.length);
    }

    public static byte[] encodeInt(OutputStream s, int v) throws IOException {
        byte[] bytes = {
                (byte) ((v >> 24) & 0xFF),
                (byte) ((v >> 16) & 0xFF),
                (byte) ((v >> 8) & 0xFF),
                (byte) (v & 0xFF)
        };
        s.write(bytes);
        return bytes;
    }

    public static int encodeString(OutputStream s, String v, Charset cs) throws IOException {
        byte[] bytes = v.getBytes(cs);
        return writeRLEBytes(s, bytes, 0, bytes.length);
    }

    public static int writeRLEBytes(OutputStream s, byte[] bytes, int off, int len) throws IOException {
        byte[] lenBytes = encodeInt(s, len);
        s.write(bytes, off, len);
        return lenBytes.length + len;
    }
}