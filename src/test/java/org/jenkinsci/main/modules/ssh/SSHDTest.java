package org.jenkinsci.main.modules.ssh;

import org.jenkinsci.main.modules.sshd.SSHD;
import org.jvnet.hudson.test.HudsonTestCase;

import javax.inject.Inject;

/**
 * @author Kohsuke Kawaguchi
 */
public class SSHDTest extends HudsonTestCase {
    @Inject
    SSHD sshd;

    /**
     * Makes sure 3 mode of the value round-trips correctly
     */
    public void testConfig() throws Exception {
        jenkins.getInjector().injectMembers(this);

        for (int i : new int[]{-1,0,100}) {
            sshd.setPort(i);
            configRoundtrip();
            assertEquals(i,sshd.getPort());
        }
    }
}
