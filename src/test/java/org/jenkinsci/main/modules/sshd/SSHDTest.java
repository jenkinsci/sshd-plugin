package org.jenkinsci.main.modules.sshd;

import java.util.List;
import org.hamcrest.CoreMatchers;
import org.jenkinsci.main.modules.sshd.SSHD;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import javax.inject.Inject;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.cipher.Cipher;
import org.junit.Assert;

import static org.junit.Assert.assertThat;
import org.jvnet.hudson.test.Issue;

/**
 * Tests of {@link SSHD}.
 * @author Kohsuke Kawaguchi
 */
public class SSHDTest {
    @Inject
    SSHD sshd;

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Makes sure 3 mode of the value round-trips correctly
     */
    @Test
    public void configRoundtrip() throws Exception {
        j.jenkins.getInjector().injectMembers(this);

        for (int i : new int[]{-1,0,100}) {
            sshd.setPort(i);
            j.configRoundtrip();
            assertThat("SSHD has not been allocated to the specified port", sshd.getPort(), CoreMatchers.equalTo(i));
        }
    }
    
    @Test
    @Issue("JENKINS-39738")
    public void checkActivatedCiphers() throws Exception {
        // Just ensure the method does not blow up && that at least one Cipher is available
        List<NamedFactory<Cipher>> activatedCiphers = SSHD.getActivatedCiphers();
        Assert.assertTrue("At least one cipher should be activated", activatedCiphers.size() >= 1);
    }
}
