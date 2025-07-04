package org.jenkinsci.main.modules.sshd;

import hudson.security.AbstractPasswordBasedSecurityRealm;
import hudson.security.GroupDetails;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.cipher.Cipher;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.KeyGenerationParameters;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jenkinsci.main.modules.cli.auth.ssh.UserPropertyImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link SSHD}.
 *
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
@DisabledOnOs(value = OS.WINDOWS)
class SSHDTest {

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    /**
     * Makes sure 3 mode of the value round-trips correctly
     */
    @Test
    void configRoundtrip() throws Exception {
        SSHD sshd = SSHD.get();

        for (int i : new int[]{-1, 0, 100}) {
            sshd.setPort(i);
            r.configRoundtrip();
            assertEquals(sshd.getPort(), i, "SSHD has not been allocated to the specified port");
        }
    }

    @Test
    @Issue("JENKINS-39738")
    void checkActivatedCiphers() {
        // Just ensure the method does not blow up && that at least one Cipher is available
        List<NamedFactory<Cipher>> activatedCiphers = SSHD.getActivatedCiphers();
        assertFalse(activatedCiphers.isEmpty(), "At least one cipher should be activated");
    }

    @Test
    @Issue("JENKINS-55813")
    void enabledUserShouldBeAuthorized() throws Exception {
        hudson.model.User enabled = hudson.model.User.getOrCreateByIdOrFullName("enabled");
        KeyPair keyPair = generateRSAKeys(enabled);
        r.jenkins.setSecurityRealm(new InvalidUserTypesRealm());
        SSHD server = SSHD.get();
        server.setPort(0);
        server.start();
        try (SshClient client = SshClient.setUpDefaultClient()) {
            client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
            client.start();
            ConnectFuture future = client.connect("enabled", new InetSocketAddress(server.getActualPort()));
            try (ClientSession session = future.verify(10, TimeUnit.SECONDS).getSession()) {
                session.addPublicKeyIdentity(keyPair);
                assertTrue(session.auth().await(10, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void enabledUserEd25519ShouldBeAuthorized() throws Exception {
        hudson.model.User enabled = hudson.model.User.getOrCreateByIdOrFullName("enabled");
        KeyPair keyPair = generateEd25519Keys(enabled);
        r.jenkins.setSecurityRealm(new InvalidUserTypesRealm());
        SSHD server = SSHD.get();
        server.setPort(0);
        server.start();
        try (SshClient client = SshClient.setUpDefaultClient()) {
            client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
            client.start();
            ConnectFuture future = client.connect("enabled", new InetSocketAddress(server.getActualPort()));
            try (ClientSession session = future.verify(10, TimeUnit.SECONDS).getSession()) {
                session.addPublicKeyIdentity(keyPair);
                assertTrue(session.auth().await(10, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    @Issue("JENKINS-55813")
    void disabledUserShouldBeUnauthorized() throws Exception {
        assertUserCannotLoginToSSH("disabled");
    }

    @Test
    @Issue("JENKINS-55813")
    void lockedUserShouldBeUnauthorized() throws Exception {
        assertUserCannotLoginToSSH("locked");
    }

    @Test
    @Issue("JENKINS-55813")
    void expiredUserShouldBeUnauthorized() throws Exception {
        assertUserCannotLoginToSSH("expired");
    }

    @Test
    @Issue("JENKINS-55813")
    void passwordExpiredUserShouldBeUnauthorized() throws Exception {
        assertUserCannotLoginToSSH("password_expired");
    }

    private void assertUserCannotLoginToSSH(String username) throws Exception {
        hudson.model.User user = hudson.model.User.getOrCreateByIdOrFullName(username);
        KeyPair keyPair = generateRSAKeys(user);
        r.jenkins.setSecurityRealm(new InvalidUserTypesRealm());

        SSHD server = SSHD.get();
        server.setPort(0);
        server.start();
        try (SshClient client = SshClient.setUpDefaultClient()) {
            client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
            client.start();
            ConnectFuture future = client.connect(username, new InetSocketAddress(server.getActualPort()));
            try (ClientSession session = future.verify(10, TimeUnit.SECONDS).getSession()) {
                session.addPublicKeyIdentity(keyPair);
                assertThrows(SshException.class, () -> session.auth().verify(10, TimeUnit.SECONDS));
            }
        }
    }

    private static KeyPair generateRSAKeys(hudson.model.User user) throws NoSuchAlgorithmException, IOException {
        // I'd prefer to generate Ed25519 keys here, but the API is too awkward currently
        // ECDSA keys would be even more awkward as we'd need a copy of the curve parameters
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String encodedPublicKey = "ssh-rsa " + Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        user.addProperty(new UserPropertyImpl(encodedPublicKey));
        return keyPair;
    }

    private static KeyPair generateEd25519Keys(hudson.model.User user) throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519", "BC");
        KeyPair keyPair = kpg.generateKeyPair();

        PublicKey publicKey = keyPair.getPublic();
        String encodedPublicKey = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        user.addProperty(new UserPropertyImpl(encodedPublicKey));
        Security.removeProvider("BC");
        return keyPair;
    }

    @Issue("JENKINS-55813")
    private static class InvalidUserTypesRealm extends AbstractPasswordBasedSecurityRealm {
        @Override
        protected UserDetails authenticate2(String user, String pass) throws AuthenticationException {
            return loadUserByUsername2(user);
        }

        @Override
        public UserDetails loadUserByUsername2(String user) throws UsernameNotFoundException {
            return switch (user) {
                case "disabled" -> throw new DisabledException(user);
                case "expired" -> throw new AccountExpiredException(user);
                case "password_expired" -> throw new CredentialsExpiredException(user);
                case "locked" -> throw new LockedException(user);
                default -> new User(user, "", true, true, true, true, List.of());
            };
        }

        @Override
        public GroupDetails loadGroupByGroupname2(String groupname, boolean fetchMembers) throws UsernameNotFoundException {
            throw new UnsupportedOperationException();
        }
    }
}
