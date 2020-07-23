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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
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

    @Override
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

        UserDetails userDetails = user.getUserDetailsForImpersonation();
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

    private @CheckForNull Authentication verifyUserUsingSecurityRealm(@NonNull User user) {
        try {
            return user.impersonate();
        } catch (UsernameNotFoundException e) {
            LOGGER.log(Level.FINE, user.getId() + " is not a real user according to SecurityRealm", e);
            return null;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(PublicKeyAuthenticatorImpl.class.getName());

}
