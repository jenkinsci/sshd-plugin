package org.jenkinsci.main.modules.sshd;

import hudson.model.User;
import jenkins.security.SecurityListener;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.jenkinsci.main.modules.cli.auth.ssh.PublicKeySignatureWriter;
import org.jenkinsci.main.modules.cli.auth.ssh.UserPropertyImpl;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.security.PublicKey;
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

        UserDetails userDetails = this.validateUserDetails(user);
        if (userDetails == null) {
            SecurityListener.fireFailedToAuthenticate(username);
            return false;
        }

        SecurityListener.fireAuthenticated(userDetails);
        return true;
    }

    private @CheckForNull User retrieveOnlyKeyValidatedUser(String username, PublicKey key) {
        LOGGER.log(Level.FINE, "Authentication attempted from {0} with {1}", new Object[]{ username, key });
        User u = User.getById(username, false);
        if (u == null) {
            LOGGER.log(Level.FINE, "No such user exists: {0}", new Object[]{ username });
            return null;
        }

        UserPropertyImpl sshKey = u.getProperty(UserPropertyImpl.class);
        if (sshKey == null) {
            LOGGER.log(Level.FINE, "No SSH key registered for user: {0}", new Object[]{ username });
            return null;
        }

        String signature = signatureWriter.asString(key);
        if (!sshKey.isAuthorizedKey(signature)) {
            LOGGER.log(Level.FINE,"Key signature did not match for the user: {0} : {1}", new Object[]{ username, signature });
            return null;
        }

        return u;
    }

    private @CheckForNull UserDetails validateUserDetails(@Nonnull User user) {
        try {
            UserDetails userDetails = user.getUserDetailsForImpersonation();
            if (!userDetails.isEnabled()) {
                LOGGER.fine(() -> user.getId() + " account is disabled");
                return null;
            } else if (!userDetails.isAccountNonLocked()) {
                LOGGER.fine(() -> user.getId() + " account is locked");
                return null;
            } else if (!userDetails.isAccountNonExpired()) {
                LOGGER.fine(() -> user.getId() + " account is expired");
                return null;
            } else if (!userDetails.isCredentialsNonExpired()) {
                LOGGER.fine(() -> user.getId() + " account credentials are expired");
                return null;
            } else {
                return userDetails;
            }
        } catch (UsernameNotFoundException e) {
            LOGGER.log(Level.FINE, e, () -> user.getId() + " is not a real user according to SecurityRealm");
            return null;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(PublicKeyAuthenticatorImpl.class.getName());

}
