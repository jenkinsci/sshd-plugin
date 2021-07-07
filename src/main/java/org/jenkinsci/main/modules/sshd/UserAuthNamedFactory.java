package org.jenkinsci.main.modules.sshd;

import java.io.IOException;
import jenkins.model.Jenkins;
import org.apache.sshd.server.auth.UserAuth;
import org.apache.sshd.server.auth.UserAuthFactory;
import org.apache.sshd.server.auth.UserAuthNoneFactory;
import org.apache.sshd.server.auth.pubkey.UserAuthPublicKeyFactory;
import org.apache.sshd.server.session.ServerSession;

/**
 * Depending on the current security configuration, activate either public key auth or no auth.
 *
 * @author Kohsuke Kawaguchi
 */
class UserAuthNamedFactory implements UserAuthFactory {
    UserAuthFactory publicKey = UserAuthPublicKeyFactory.INSTANCE;
    UserAuthFactory none = UserAuthNoneFactory.INSTANCE;

    private UserAuthFactory select() {
        final Jenkins jenkins = Jenkins.getInstance();
        return (jenkins != null && jenkins.isUseSecurity()) ? publicKey : none;
    }

    public String getName() {
        return select().getName();
    }

    @Override
    public UserAuth createUserAuth(ServerSession session) throws IOException {
        return select().createUserAuth(session);
    }
}
