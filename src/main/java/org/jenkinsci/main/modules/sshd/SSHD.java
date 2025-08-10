package org.jenkinsci.main.modules.sshd;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.util.ServerTcpPort;
import jenkins.util.SystemProperties;
import jenkins.util.Timer;
import net.jcip.annotations.GuardedBy;
import net.sf.json.JSONObject;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.cipher.BuiltinCiphers;
import org.apache.sshd.common.cipher.Cipher;
import org.apache.sshd.common.kex.KeyExchangeFactory;
import org.apache.sshd.common.keyprovider.AbstractKeyPairProvider;
import org.apache.sshd.common.mac.Mac;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.UserAuthFactory;
import org.jenkinsci.main.modules.instance_identity.InstanceIdentity;
import org.kohsuke.stapler.StaplerRequest2;

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

    /**
     * Comma-separated string of key exchange names to disable. Defaults to a list of DH SHA1 key exchanges, gets its value from {@link SystemProperties}.
     */
    private static final String EXCLUDED_KEY_EXCHANGES = SystemProperties.getString(SSHD.class.getName() + ".excludedKeyExchanges",
            "diffie-hellman-group-exchange-sha1, diffie-hellman-group14-sha1, diffie-hellman-group1-sha1");

    /**
     * Comma-separated string of key exchange names to disable. Defaults to a list of MD5 and truncated SHA-1 HMACs, gets its value from {@link SystemProperties}.
     */
    private static final String EXCLUDED_MACS = SystemProperties.getString(SSHD.class.getName() + ".excludedMacs",
            "hmac-md5, hmac-md5-96, hmac-sha1-96");

    @NonNull
    @Override
    public GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Security.class);
    }

    @GuardedBy("this")
    private transient SshServer sshd;

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
            Timer.get().submit(new Runnable() {
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
    @NonNull
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
        LOGGER.fine("starting SSHD");

        stop();
        sshd = SshServer.setUpDefaultServer();
        sshd.setUserAuthFactories(Arrays.<UserAuthFactory>asList(new UserAuthNamedFactory()));
        
        sshd.setCipherFactories(getActivatedCiphers());
        sshd.setKeyExchangeFactories(filterKeyExchanges(sshd.getKeyExchangeFactories()));
        sshd.setMacFactories(filterMacs(sshd.getMacFactories()));
        sshd.setPort(port);
        sshd.setKeyPairProvider(new AbstractKeyPairProvider() {
            @Override
            public Iterable<KeyPair> loadKeys(SessionContext session) throws IOException, GeneralSecurityException {
                InstanceIdentity identity = InstanceIdentity.get();
                return Collections.singletonList(new KeyPair(identity.getPublic(), identity.getPrivate()));
            }
        });

        sshd.setShellFactory(null); // no shell support
        sshd.setCommandFactory(new CommandFactoryImpl());
        sshd.setPublickeyAuthenticator(new PublicKeyAuthenticatorImpl());

        // Allow to configure idle timeout with a system property
        String idleTimeoutPropertyName = SSHD.class.getName() + "." + IDLE_TIMEOUT_KEY;
        IdleTimeout.fromSystemProperty(idleTimeoutPropertyName).apply(sshd);

        sshd.start();
        LOGGER.info("Started SSHD at port " + sshd.getPort());
    }

    private List<NamedFactory<Mac>> filterMacs(List<NamedFactory<Mac>> macFactories) {
        if (EXCLUDED_MACS == null || EXCLUDED_MACS.isBlank()) {
            return macFactories;
        }

        List<String> excludedNames = Arrays.stream(EXCLUDED_MACS.split(",")).filter(s -> !s.isBlank()).map(String::trim).collect(Collectors.toList());

        List<NamedFactory<Mac>> filtered = new ArrayList<>();
        for (NamedFactory<Mac> macFactory : macFactories) {
            final String name = macFactory.getName();
            if (excludedNames.contains(name)) {
                LOGGER.log(Level.CONFIG, "Excluding " + name);
            } else {
                LOGGER.log(Level.FINE, "Not excluding " + name);
                filtered.add(macFactory);
            }
        }
        return filtered;
    }

    /**
     * Filter key exchanges based on configuration from {@link #EXCLUDED_KEY_EXCHANGES}.
     * @param keyExchangeFactories the full list of key exchange factories
     * @return a filtered list of key exchange factories
     */
    private List<KeyExchangeFactory> filterKeyExchanges(List<KeyExchangeFactory> keyExchangeFactories) {
        if (EXCLUDED_KEY_EXCHANGES.isBlank()) {
            return keyExchangeFactories;
        }

        List<String> excludedNames = Arrays.stream(EXCLUDED_KEY_EXCHANGES.split(",")).filter(s -> !s.isBlank()).map(String::trim).collect(Collectors.toList());

        List<KeyExchangeFactory> filtered = new ArrayList<>();
        for (KeyExchangeFactory keyExchangeNamedFactory : keyExchangeFactories) {
            final String name = keyExchangeNamedFactory.getName();
            if (excludedNames.contains(name)) {
                LOGGER.log(Level.CONFIG, "Excluding " + name);
            } else {
                LOGGER.log(Level.FINE, "Not excluding " + name);
                filtered.add(keyExchangeNamedFactory);
            }
        }
        return filtered;
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
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
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
        return ExtensionList.lookupSingleton(SSHD.class);
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
