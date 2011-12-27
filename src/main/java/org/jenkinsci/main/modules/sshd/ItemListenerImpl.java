package org.jenkinsci.main.modules.sshd;

import hudson.Extension;
import hudson.model.listeners.ItemListener;

import javax.inject.Inject;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension
public class ItemListenerImpl extends ItemListener {
    @Inject
    public SSHD sshd;
    
    public void onBeforeShutdown() {
        try {
            sshd.stop();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to shutdown SSHD",e);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ItemListenerImpl.class.getName());
}
