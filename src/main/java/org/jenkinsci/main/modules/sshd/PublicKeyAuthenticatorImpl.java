package org.jenkinsci.main.modules.sshd;

import com.trilead.ssh2.crypto.Base64;
import com.trilead.ssh2.packets.TypesWriter;
import hudson.model.User;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.jenkinsci.main.modules.cli.auth.ssh.UserPropertyImpl;

import java.security.PublicKey;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.logging.Logger;

/**
 * {@link PublickeyAuthenticator} that uses ssh-cli-auth Jenkins module to match up
 * the public key against what the user has registered.
 *
 * @author Kohsuke Kawaguchi
 */
class PublicKeyAuthenticatorImpl implements PublickeyAuthenticator {
    public boolean authenticate(String username, PublicKey key, ServerSession session) {
        LOGGER.fine("Authentication attempted from "+username+" with "+key);
        User u = User.get(username, false);
        if (u==null) {
            LOGGER.fine("No such user exists: "+username);
            return false;
        }

        UserPropertyImpl sshKey = u.getProperty(UserPropertyImpl.class);
        if (sshKey==null) {
            LOGGER.fine("No SSH key registered for user: "+username);
            return false;
        }

        // TODO: use sshKey.has() when we can depend on 1.446 or later
        if (!sshKey.isAuthorizedKey(getPublicKeySignature(key))) {
            LOGGER.fine("Key signature didn't match for the user: "+username+" : "+getPublicKeySignature(key));
            return false;
        }

        return true;
    }

    private static String getPublicKeySignature(PublicKey pk) {
        TypesWriter tw = new TypesWriter();
        if (pk instanceof RSAPublicKey) {
            RSAPublicKey rpk = (RSAPublicKey) pk;
            tw.writeString("ssh-rsa");
            tw.writeMPInt(rpk.getPublicExponent());
            tw.writeMPInt(rpk.getModulus());
            return new String(Base64.encode(tw.getBytes()));
        }
        if (pk instanceof DSAPublicKey) {
            DSAPublicKey rpk = (DSAPublicKey) pk;
            tw.writeString("ssh-dss");
            DSAParams p = rpk.getParams();
            tw.writeMPInt(p.getP());
            tw.writeMPInt(p.getQ());
            tw.writeMPInt(p.getG());
            tw.writeMPInt(rpk.getY());
            return new String(Base64.encode(tw.getBytes()));
        }
        throw new IllegalArgumentException("Unknown key type: "+pk);
    }

    private static final Logger LOGGER = Logger.getLogger(PublicKeyAuthenticatorImpl.class.getName());
}
