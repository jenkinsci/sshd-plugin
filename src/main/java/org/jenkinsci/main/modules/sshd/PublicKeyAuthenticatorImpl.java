package org.jenkinsci.main.modules.sshd;

import hudson.model.User;
import jenkins.security.SecurityListener;
import org.acegisecurity.Authentication;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.jenkinsci.main.modules.cli.auth.ssh.PublicKeySignatureWriter;
import org.jenkinsci.main.modules.cli.auth.ssh.UserPropertyImpl;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.security.PublicKey;
import java.util.Collections;
import java.util.logging.Level;
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
        User user = this.retrieveOnlyKeyValidatedUser(username, key);

        if (user == null) {
            SecurityListener.fireFailedToAuthenticate(username);
            return false;
        }

        Authentication auth = this.verifyUserUsingSecurityRealm(user);
        if (auth == null) {
            SecurityListener.fireFailedToAuthenticate(username);
            return false;
        }

        UserDetails userDetails = new SSHUserDetails(username, auth);
        SecurityListener.fireAuthenticated(userDetails);
        return true;
    }

    private @CheckForNull User retrieveOnlyKeyValidatedUser(String username, PublicKey key) {
        LOGGER.fine("Authentication attempted from " + username + " with " + key);
        User u = User.get(username, false, Collections.emptyMap());
        if (u == null) {
            LOGGER.fine("No such user exists: " + username);
            return null;
        }

        UserPropertyImpl sshKey = u.getProperty(UserPropertyImpl.class);
        if (sshKey == null) {
            LOGGER.fine("No SSH key registered for user: " + username);
            return null;
        }

        String signature = signatureWriter.asString(key);
        if (!sshKey.isAuthorizedKey(signature)) {
            LOGGER.fine("Key signature didn't match for the user: " + username + " : " + signature);
            return null;
        }

        return u;
    }

    private @CheckForNull Authentication verifyUserUsingSecurityRealm(@Nonnull User user) {
        try {
            return user.impersonate();
        } catch (UsernameNotFoundException e) {
            LOGGER.log(Level.FINE, user.getId() + " is not a real user accordingly to SecurityRealm", e);
            return null;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(PublicKeyAuthenticatorImpl.class.getName());

    public static class SSHUserDetails extends org.acegisecurity.userdetails.User {
        private SSHUserDetails(@Nonnull String username, @Nonnull Authentication auth) {
            super(
                    username, "",
                    // account validity booleans
                    true, true, true, true,
                    auth.getAuthorities()
            );
        }
    }
}
