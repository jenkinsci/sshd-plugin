package org.jenkinsci.main.modules.sshd;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import java.io.IOException;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins.MasterComputer;
import jenkins.util.ServerTcpPort;
import net.sf.json.JSONObject;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.cipher.BuiltinCiphers;
import org.apache.sshd.common.cipher.Cipher;
import org.apache.sshd.common.keyprovider.AbstractKeyPairProvider;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.UserAuth;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension
public class SSHD extends GlobalConfiguration {

    @Override
    public GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Security.class);
    }

    @GuardedBy("this")
    private transient SshServer sshd;

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
    public synchronized int getActualPort() {
        if (port==-1)   return -1;
        if (sshd!=null)
            return sshd.getPort();
        return port;
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

    public synchronized void start() throws IOException, InterruptedException {
        if (port<0) return; // don't start it

        stop();
        sshd = SshServer.setUpDefaultServer();

        sshd.setUserAuthFactories(Arrays.<NamedFactory<UserAuth>>asList(new UserAuthNamedFactory()));
                
        sshd.setCipherFactories(Arrays.<NamedFactory<Cipher>>asList(// AES 256 and 192 requires unlimited crypto, so don't use that
                                              // CBC modes are not secure, so they have been dropped (see JENKINS-39805)
                                              BuiltinCiphers.aes128ctr));

        sshd.setPort(port);

        sshd.setKeyPairProvider(new AbstractKeyPairProvider() {
            @Override
            public Iterable<KeyPair> loadKeys() {
                return Collections.singletonList(new KeyPair(identity.getPublic(),identity.getPrivate()));
            }
        });

        sshd.setShellFactory(null); // no shell support
        sshd.setCommandFactory(new CommandFactoryImpl());

        sshd.setPublickeyAuthenticator(new PublicKeyAuthenticatorImpl());

        // Allow to configure idle timeout with a system property
        String idleTimeoutPropertyName = SSHD.class.getName() + "." + IDLE_TIMEOUT_KEY;
        String idleTimeout = System.getProperty(idleTimeoutPropertyName);
        if (idleTimeout != null) {
            try {
                // In sshd-core 0.8.0 it must be an int
                // https://github.com/apache/mina-sshd/blob/sshd-0.8.0/sshd-core/src/main/java/org/apache/sshd/server/session/ServerSession.java#L68
                Integer.parseInt(idleTimeout);
                sshd.getProperties().put(org.apache.sshd.server.ServerFactoryManager.IDLE_TIMEOUT, idleTimeout);
            } catch (NumberFormatException nfe) {
                LOGGER.warning("SSHD Idle Timeout configuration skipped. " + idleTimeoutPropertyName + " value (" +
                        idleTimeout + ") isn't an integer.");
            }
        }

        sshd.start();
        LOGGER.info("Started SSHD at port " + sshd.getPort());
    }

    public synchronized void restart() {
        try {
            if (sshd!=null) {
                sshd.stop(false);
                sshd = null;
            }
            start();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to restart SSHD", e);
        }
    }

    public synchronized void stop() throws IOException, InterruptedException {
        if (sshd!=null) {
            sshd.stop(true);
            sshd = null;
        }
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        setPort(new ServerTcpPort(json.getJSONObject("port")).getPort());
        return true;
    }

    /**
     * Key used to retrieve the value of idle timeout after which
     * the server will close the connection.  In milliseconds.
     */
    public static final String IDLE_TIMEOUT_KEY = "idle-timeout";

    private static final Logger LOGGER = Logger.getLogger(SSHD.class.getName());

    public static SSHD get() {
        return ExtensionList.lookup(GlobalConfiguration.class).get(SSHD.class);
    }

    @Initializer(after= InitMilestone.JOB_LOADED,fatal=false)
    public static void init() throws IOException, InterruptedException {
        get().start();
    }

    private static Logger MINA_LOGGER = Logger.getLogger("org.apache.sshd");

    static {
        // logging is way too verbose at INFO level, so trim it down to WARNING
        // unless someone has already set that value, in which case we honor that
        if (MINA_LOGGER.getLevel()==null)
            MINA_LOGGER.setLevel(Level.WARNING);
    }
}
