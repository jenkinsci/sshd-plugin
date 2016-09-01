package org.jenkinsci.main.modules.ssh;

import org.hamcrest.CoreMatchers;
import org.jenkinsci.main.modules.sshd.SSHD;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import javax.inject.Inject;

import static org.junit.Assert.assertThat;

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
}
