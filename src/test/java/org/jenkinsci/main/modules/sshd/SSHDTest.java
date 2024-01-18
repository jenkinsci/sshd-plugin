package org.jenkinsci.main.modules.sshd;

import hudson.Functions;
import hudson.security.AbstractPasswordBasedSecurityRealm;
import hudson.security.GroupDetails;
import org.acegisecurity.AccountExpiredException;
import org.acegisecurity.AuthenticationException;
import org.acegisecurity.CredentialsExpiredException;
import org.acegisecurity.DisabledException;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.LockedException;
import org.acegisecurity.userdetails.User;
import org.acegisecurity.userdetails.UserDetails;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.cipher.Cipher;
import org.jenkinsci.main.modules.cli.auth.ssh.PublicKeySignatureWriter;
import org.jenkinsci.main.modules.cli.auth.ssh.UserPropertyImpl;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.springframework.dao.DataAccessException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

/**
 * Tests of {@link SSHD}.
 * @author Kohsuke Kawaguchi
 */
public class SSHDTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Before
    public void setUp() {
        assumeFalse(Functions.isWindows());
    }

    /**
     * Makes sure 3 mode of the value round-trips correctly
     */
    @Test
    public void configRoundtrip() throws Exception {
        SSHD sshd = SSHD.get();

        for (int i : new int[]{-1,0,100}) {
            sshd.setPort(i);
            j.configRoundtrip();
            assertEquals("SSHD has not been allocated to the specified port", sshd.getPort(), i);
        }
    }

    @Test
    @Issue("JENKINS-39738")
    public void checkActivatedCiphers() throws Exception {
        // Just ensure the method does not blow up && that at least one Cipher is available
        List<NamedFactory<Cipher>> activatedCiphers = SSHD.getActivatedCiphers();
        assertTrue("At least one cipher should be activated", activatedCiphers.size() >= 1);
    }

    @Test
    @Issue("JENKINS-55813")
    public void enabledUserShouldBeAuthorized() throws Exception {
        hudson.model.User enabled = hudson.model.User.getOrCreateByIdOrFullName("enabled");
        KeyPair keyPair = generateKeys(enabled);
        j.jenkins.setSecurityRealm(new InvalidUserTypesRealm());
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
    public void disabledUserShouldBeUnauthorized() throws Exception {
        assertUserCannotLoginToSSH("disabled");
    }

    @Test
    @Issue("JENKINS-55813")
    public void lockedUserShouldBeUnauthorized() throws Exception {
        assertUserCannotLoginToSSH("locked");
    }

    @Test
    @Issue("JENKINS-55813")
    public void expiredUserShouldBeUnauthorized() throws Exception {
        assertUserCannotLoginToSSH("expired");
    }

    @Test
    @Issue("JENKINS-55813")
    public void passwordExpiredUserShouldBeUnauthorized() throws Exception {
        assertUserCannotLoginToSSH("password_expired");
    }

    private void assertUserCannotLoginToSSH(String username) throws Exception {
        hudson.model.User user = hudson.model.User.getOrCreateByIdOrFullName(username);
        KeyPair keyPair = generateKeys(user);
        j.jenkins.setSecurityRealm(new InvalidUserTypesRealm());

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

    private static KeyPair generateKeys(hudson.model.User user) throws NoSuchAlgorithmException, IOException {
        // I'd prefer to generate Ed25519 keys here, but the API is too awkward currently
        // ECDSA keys would be even more awkward as we'd need a copy of the curve parameters
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String encodedPublicKey = "ssh-rsa " + new PublicKeySignatureWriter().asString(keyPair.getPublic());
        user.addProperty(new UserPropertyImpl(encodedPublicKey));
        return keyPair;
    }

    @Issue("JENKINS-55813")
    private static class InvalidUserTypesRealm extends AbstractPasswordBasedSecurityRealm {
        @Override
        protected UserDetails authenticate(String user, String pass) throws AuthenticationException {
            return loadUserByUsername(user);
        }

        @Override
        public UserDetails loadUserByUsername(String user) throws UsernameNotFoundException, DataAccessException {
            switch (user) {
                case "disabled":
                    throw new DisabledException(user);

                case "expired":
                    throw new AccountExpiredException(user);

                case "password_expired":
                    throw new CredentialsExpiredException(user);

                case "locked":
                    throw new LockedException(user);

                default:
                    return new User(user, "", true, true, true, true, new GrantedAuthority[0]);
            }
        }

        @Override
        public GroupDetails loadGroupByGroupname(String group) throws UsernameNotFoundException, DataAccessException {
            throw new UnsupportedOperationException();
        }
    }
}
