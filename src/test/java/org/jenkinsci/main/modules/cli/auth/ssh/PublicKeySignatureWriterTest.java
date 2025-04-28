package org.jenkinsci.main.modules.cli.auth.ssh;

import com.trilead.ssh2.packets.TypesWriter;
import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

@For(PublicKeySignatureWriter.class)
class PublicKeySignatureWriterTest {

    // regression in 1.6
    @Test
    @Issue("JENKINS-43669")
    void shouldBeSimilarToTrileadSSH() throws Exception {
        byte[] publicBytes = Base64.decodeBase64(PUBLIC_RSA_KEY);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey key = keyFactory.generatePublic(keySpec);

        String trileadString = new TrileadPublicKeySignatureWriter().asString(key);
        String actualString = new PublicKeySignatureWriter().asString(key);
        assertThat("Encoded public key is different from TrileadSSH", actualString, equalTo(trileadString));
    }

    private static class TrileadPublicKeySignatureWriter {

        public String asString(PublicKey key) {
            if (key instanceof RSAPublicKey) return asString((RSAPublicKey) key);
            if (key instanceof DSAPublicKey) return asString((DSAPublicKey) key);
            throw new IllegalArgumentException("Unknown key type: " + key);
        }

        public String asString(DSAPublicKey key) {
            TypesWriter tw = new TypesWriter();
            tw.writeString("ssh-dss");
            DSAParams p = key.getParams();
            tw.writeMPInt(p.getP());
            tw.writeMPInt(p.getQ());
            tw.writeMPInt(p.getG());
            tw.writeMPInt(key.getY());
            return encode(tw);
        }

        public String asString(RSAPublicKey key) {
            TypesWriter tw = new TypesWriter();
            tw.writeString("ssh-rsa");
            tw.writeMPInt(key.getPublicExponent());
            tw.writeMPInt(key.getModulus());
            return encode(tw);
        }

        private String encode(TypesWriter tw) {
            return new String(com.trilead.ssh2.crypto.Base64.encode(tw.getBytes()));
        }
    }

    private static final String PUBLIC_RSA_KEY = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDH+wPrKYG1KVlzQUVtBghR8n9d" +
            "zcShSZo0+3KgyVdOea7Ei7vQ1U4wRn1zlI5rSqHDzFitblmqnB2anzVvdQxLQ3Uq" +
            "EBKBfMihnLgCSW8Xf7MCH+DSGHNvBg2xSNhcfEmnbLPLnbuz4ySn1UB0lH2eqxy5" +
            "0zstxhTY0binD9Y+rwIDAQAB";

}
