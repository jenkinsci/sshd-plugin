package org.jenkinsci.main.modules.sshd;

import jenkins.model.Jenkins;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.server.auth.UserAuth;
import org.apache.sshd.server.auth.UserAuthNoneFactory;
import org.apache.sshd.server.auth.pubkey.UserAuthPublicKeyFactory;

/**
 * Depending on the current security configuration, activate either public key auth or no auth.
 *
 * @author Kohsuke Kawaguchi
 */
class UserAuthNamedFactory implements NamedFactory<UserAuth> {
    NamedFactory<UserAuth> publicKey = UserAuthPublicKeyFactory.INSTANCE;
    NamedFactory<UserAuth> none = UserAuthNoneFactory.INSTANCE;

    private NamedFactory<UserAuth> select() {
        final Jenkins jenkins = Jenkins.getInstance();
        return (jenkins != null && jenkins.isUseSecurity()) ? publicKey : none;
    }

    public String getName() {
        return select().getName();
    }

    public UserAuth create() {
        return select().create();
    }
}
