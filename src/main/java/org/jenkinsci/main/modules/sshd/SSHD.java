package org.jenkinsci.main.modules.sshd;

import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.RootAction;
import jenkins.model.Jenkins;
import org.apache.sshd.SshServer;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.cipher.AES128CBC;
import org.apache.sshd.common.cipher.BlowfishCBC;
import org.apache.sshd.common.cipher.TripleDESCBC;
import org.apache.sshd.common.keyprovider.AbstractKeyPairProvider;
import org.apache.sshd.server.UserAuth;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;

import java.io.IOException;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension
public class SSHD implements RootAction {
    private final SshServer sshd = SshServer.setUpDefaultServer();

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return "sshd";
    }

    public void start() throws IOException {
        sshd.setUserAuthFactories(Arrays.<NamedFactory<UserAuth>>asList(new UserAuthNamedFactory()));
                
        sshd.setCipherFactories(Arrays.asList(// AES 256 and 192 requires unlimited crypto, so don't use that
                new AES128CBC.Factory(),
                new TripleDESCBC.Factory(),
                new BlowfishCBC.Factory()));

        sshd.setPort(9922);

        sshd.setKeyPairProvider(new AbstractKeyPairProvider() {
            @Override
            protected KeyPair[] loadKeys() {
                InstanceIdentity ii = InstanceIdentity.get();
                return new KeyPair[] {
                        new KeyPair(ii.getPublic(),ii.getPrivate())
                };
            }
        });

        sshd.setShellFactory(null); // no shell support
        sshd.setCommandFactory(new CommandFactoryImpl());

        sshd.setPublickeyAuthenticator(new PublicKeyAuthenticatorImpl());

        LOGGER.info("Starting SSHD at port "+sshd.getPort());
        sshd.start();
    }

    private static final Logger LOGGER = Logger.getLogger(SSHD.class.getName());

    public static SSHD get() {
        return Jenkins.getInstance().getExtensionList(RootAction.class).get(SSHD.class);
    }
    
    @Initializer(after= InitMilestone.JOB_LOADED)
    public static void init() throws IOException {
        get().start();
    }

}
