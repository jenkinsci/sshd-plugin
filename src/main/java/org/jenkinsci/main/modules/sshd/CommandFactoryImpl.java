package org.jenkinsci.main.modules.sshd;

import org.apache.sshd.server.Command;
import org.apache.sshd.server.CommandFactory;

/**
 * @author Kohsuke Kawaguchi
 */
public class CommandFactoryImpl implements CommandFactory {
    public Command createCommand(String command) {
        return new InvalidCommand(command);
    }
}
