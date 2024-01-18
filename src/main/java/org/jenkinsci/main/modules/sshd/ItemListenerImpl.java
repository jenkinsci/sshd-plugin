package org.jenkinsci.main.modules.sshd;

import hudson.Extension;
import hudson.model.listeners.ItemListener;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension
public class ItemListenerImpl extends ItemListener {
    
    @Override
    public void onBeforeShutdown() {
        try {
            SSHD.get().stop();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to shutdown SSHD",e);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ItemListenerImpl.class.getName());
}
