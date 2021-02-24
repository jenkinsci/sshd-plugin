/*
 * The MIT License
 *
 * Copyright (c) 2013 Red Hat, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.main.modules.cli.auth.ssh;

import org.apache.commons.codec.binary.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.PublicKey;
import java.util.Objects;


public class PublicKeySignatureWriter {

    public String asString(PublicKey key) {
        if (key instanceof RSAPublicKey) return asString((RSAPublicKey) key);
        if (key instanceof DSAPublicKey) return asString((DSAPublicKey) key);
        throw new IllegalArgumentException("Unknown key type: " + key);
    }

    public String asString(DSAPublicKey key) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            DSAParams keyParams = Objects.requireNonNull(key.getParams(), "No DSA params available");
            KeyEncodeHelper.encodeString(output, "ssh-dss", StandardCharsets.ISO_8859_1);
            KeyEncodeHelper.encodeBigInt(output, keyParams.getP());
            KeyEncodeHelper.encodeBigInt(output, keyParams.getQ());
            KeyEncodeHelper.encodeBigInt(output, keyParams.getG());
            KeyEncodeHelper.encodeBigInt(output, key.getY());
            return Base64.encodeBase64String(output.toByteArray());
        } catch(IOException e) {
            throw new PublicKeySignatureWriterException(e);
        }
    }

    public String asString(RSAPublicKey key) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            KeyEncodeHelper.encodeString(output, "ssh-rsa", StandardCharsets.ISO_8859_1);
            KeyEncodeHelper.encodeBigInt(output, key.getPublicExponent());
            KeyEncodeHelper.encodeBigInt(output, key.getModulus());
            return Base64.encodeBase64String(output.toByteArray());
        } catch(IOException e) {
            throw new PublicKeySignatureWriterException(e);
        }
    }

}
