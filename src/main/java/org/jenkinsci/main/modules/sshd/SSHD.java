package org.jenkinsci.main.modules.sshd;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import java.io.IOException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
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

    //TODO: Make Ciphers configurable from UI, with logic similar to Remoting protocols?
    /**
     * Lists Built-in Ciphers, which are enabled by default in SSH Core
     */
    private static final List<NamedFactory<Cipher>> ENABLED_CIPHERS = Arrays.<NamedFactory<Cipher>>asList(
        BuiltinCiphers.aes128ctr, BuiltinCiphers.aes192ctr, BuiltinCiphers.aes256ctr
    );
    
    @Override
    public GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Security.class);
    }

    @GuardedBy("this")
    private transient SshServer sshd;

    @Inject
    private transient InstanceIdentity identity;

    private volatile int port = -1;

    public SSHD() {
        load();
    }

    /**
     * Returns the configured port to run SSHD.
     *
     * @return
     *      -1 if disabled, 0 if random port is selected, otherwise the port number configured.
     */
    public int getPort() {
        return port;
    }

    /**
     * Gets the current TCP/IP port that this daemon is running with.
     *
     * @return Actual port number or -1 if disabled.
     */
    public synchronized int getActualPort() {
        if (port==-1)   return -1;
        if (sshd!=null)
            return sshd.getPort();
        return port;
    }

    /**
     * Set the port number to be used.
     *
     * @param port -1 to disable this, 0 to run with a random port, otherwise the port number.
     */
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

    /**
     * Provides a list of Cipher factories, which can be activated on the instance.
     * Cyphers will be considered as activated if they are defined in {@link #ENABLED_CIPHERS} and supported in the current JVM.
     * @return List of factories
     */
    @Nonnull
    /*package*/ static List<NamedFactory<Cipher>> getActivatedCiphers() {
        final List<NamedFactory<Cipher>> activatedCiphers = new ArrayList<>(ENABLED_CIPHERS.size());
        for (NamedFactory<Cipher> cipher : ENABLED_CIPHERS) {
            if (cipher instanceof BuiltinCiphers) {
                final BuiltinCiphers c = (BuiltinCiphers)cipher;
                if (c.isSupported()) {
                    activatedCiphers.add(cipher);
                } else {
                    LOGGER.log(Level.FINE, "Discovered unsupported built-in Cipher: {0}. It will not be enabled", c);
                }
            } else {
                // We cannot determine if the cipher is supported, but the default configuration lists only Built-in ciphers.
                // So somebody explicitly added it on his own risk.
                activatedCiphers.add(cipher);
            }
         }
        return activatedCiphers;
    }
    
    public synchronized void start() throws IOException, InterruptedException {
        int port = this.port; // Capture local copy to prevent race conditions. Setting port to -1 after the check would blow up later.
        if (port<0) return; // don't start it

        stop();
        sshd = SshServer.setUpDefaultServer();

        sshd.setUserAuthFactories(Arrays.<NamedFactory<UserAuth>>asList(new UserAuthNamedFactory()));
        
        sshd.setCipherFactories(getActivatedCiphers());
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
