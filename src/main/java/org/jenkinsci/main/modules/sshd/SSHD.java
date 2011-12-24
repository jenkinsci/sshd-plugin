package org.jenkinsci.main.modules.sshd;

import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import jenkins.model.Jenkins.MasterComputer;
import jenkins.util.ServerTcpPort;
import net.sf.json.JSONObject;
import org.apache.sshd.SshServer;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.cipher.AES128CBC;
import org.apache.sshd.common.cipher.BlowfishCBC;
import org.apache.sshd.common.cipher.TripleDESCBC;
import org.apache.sshd.common.keyprovider.AbstractKeyPairProvider;
import org.apache.sshd.server.UserAuth;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.kohsuke.stapler.StaplerRequest;

import javax.inject.Inject;
import java.io.IOException;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension
public class SSHD extends GlobalConfiguration {
    private transient final SshServer sshd = SshServer.setUpDefaultServer();

    @Inject
    private transient InstanceIdentity identity;

    private volatile int port;

    public SSHD() {
        load();
    }

    /**
     * Returns the configured port to run SSHD.
     *
     * @return
     *      -1 to disable this, 0 to run with a random port, otherwise the port number.
     */
    public int getPort() {
        return port;
    }

    /**
     * Gets the current TCP/IP port that this daemon is running with.
     *
     * @return -1 if disabled, but never null.
     */
    public int getActualPort() {
        if (port==-1)   return -1;
        return sshd.getPort();
    }

    public void setPort(int port) {
        if (this.port!=port) {
            this.port = port;
            MasterComputer.threadPoolForRemoting.submit(new Runnable() {
                public void run() {
                    restart();
                }
            });
            save();
        }
    }

    public synchronized void start() throws IOException {
        if (port<0) return; // don't start it

        sshd.setUserAuthFactories(Arrays.<NamedFactory<UserAuth>>asList(new UserAuthNamedFactory()));
                
        sshd.setCipherFactories(Arrays.asList(// AES 256 and 192 requires unlimited crypto, so don't use that
                new AES128CBC.Factory(),
                new TripleDESCBC.Factory(),
                new BlowfishCBC.Factory()));

        sshd.setPort(port);

        sshd.setKeyPairProvider(new AbstractKeyPairProvider() {
            @Override
            protected KeyPair[] loadKeys() {
                return new KeyPair[] {
                        new KeyPair(identity.getPublic(),identity.getPrivate())
                };
            }
        });

        sshd.setShellFactory(null); // no shell support
        sshd.setCommandFactory(new CommandFactoryImpl());

        sshd.setPublickeyAuthenticator(new PublicKeyAuthenticatorImpl());

        sshd.start();
        LOGGER.info("Started SSHD at port " + sshd.getPort());
    }

    public synchronized void restart() {
        try {
            sshd.stop(false);
            start();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to restart SSHD", e);
        }
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        setPort(new ServerTcpPort(json.getJSONObject("port")).getPort());
        return true;
    }

    private static final Logger LOGGER = Logger.getLogger(SSHD.class.getName());

    public static SSHD get() {
        return Jenkins.getInstance().getExtensionList(GlobalConfiguration.class).get(SSHD.class);
    }

    @Initializer(after= InitMilestone.JOB_LOADED)
    public static void init() throws IOException {
        get().start();
    }

}
