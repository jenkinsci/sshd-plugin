package org.jenkinsci.main.modules.sshd;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.model.PageDecorator;
import jenkins.model.Jenkins;

import javax.inject.Inject;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Advertises SSH endpoint through HTTP header.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class PortAdvertiser extends PageDecorator {
    @Inject
    public SSHD sshd;

    @CheckForNull
    public String getEndpoint() {
        try {
            int p = sshd.getActualPort();
            if (p>0) {
                final Jenkins jenkins = Jenkins.get();
                return (host != null ? host : new URL(jenkins.getRootUrl()).getHost()) + ":" + p;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to advertise SSH port",e);
        }
        return null;
    }
    
    public String host = System.getProperty(SSHD.class.getName()+".hostName");

    private static final Logger LOGGER = Logger.getLogger(PortAdvertiser.class.getName());
}
