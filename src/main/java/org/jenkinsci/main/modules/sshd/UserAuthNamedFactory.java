package org.jenkinsci.main.modules.sshd;

import jenkins.model.Jenkins;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.server.UserAuth;
import org.apache.sshd.server.auth.UserAuthNone;
import org.apache.sshd.server.auth.UserAuthPublicKey;

/**
 * Depending on the current security configuration, activate either public key auth or no auth.
 *
 * @author Kohsuke Kawaguchi
 */
class UserAuthNamedFactory implements NamedFactory<UserAuth> {
    NamedFactory<UserAuth> publicKey = new UserAuthPublicKey.Factory();
    NamedFactory<UserAuth> none = new UserAuthNone.Factory();

    private NamedFactory<UserAuth> select() {
        return Jenkins.getInstance().isUseSecurity() ? publicKey : none;
    }

    public String getName() {
        return select().getName();
    }

    public UserAuth create() {
        return select().create();
    }
}
