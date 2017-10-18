package org.jenkinsci.main.modules.sshd;

import hudson.model.User;
import jenkins.model.Jenkins;
import jenkins.security.SecurityListener;
import org.acegisecurity.userdetails.UserDetails;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.jenkinsci.main.modules.cli.auth.ssh.PublicKeySignatureWriter;
import org.jenkinsci.main.modules.cli.auth.ssh.UserPropertyImpl;

import java.security.PublicKey;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * {@link PublickeyAuthenticator} that uses ssh-cli-auth Jenkins module to match up
 * the public key against what the user has registered.
 *
 * @author Kohsuke Kawaguchi
 */
class PublicKeyAuthenticatorImpl implements PublickeyAuthenticator {

    private final PublicKeySignatureWriter signatureWriter = new PublicKeySignatureWriter();

    public boolean authenticate(String username, PublicKey key, ServerSession session) {
        boolean authenticated = this.processAuthenticate(username, key);

        if(authenticated){
            UserDetails userDetails = Jenkins.getInstance().getSecurityRealm().loadUserByUsername(username);
            SecurityListener.fireAuthenticated(userDetails);
        }else{
            SecurityListener.fireFailedToAuthenticate(username);
        }

        return authenticated;
    }

    private boolean processAuthenticate(String username, PublicKey key) {
        LOGGER.fine("Authentication attempted from "+username+" with "+key);
        User u = User.get(username, false, Collections.emptyMap());
        if (u==null) {
            LOGGER.fine("No such user exists: "+username);
            return false;
        }

        UserPropertyImpl sshKey = u.getProperty(UserPropertyImpl.class);
        if (sshKey==null) {
            LOGGER.fine("No SSH key registered for user: "+username);
            return false;
        }

        String signature = signatureWriter.asString(key);
        if (!sshKey.isAuthorizedKey(signature)) {
            LOGGER.fine("Key signature didn't match for the user: "+username+" : " + signature);
            return false;
        }

        return true;
    }

    private static final Logger LOGGER = Logger.getLogger(PublicKeyAuthenticatorImpl.class.getName());
}
