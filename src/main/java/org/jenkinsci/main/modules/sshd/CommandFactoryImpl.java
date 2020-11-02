package org.jenkinsci.main.modules.sshd;

import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.command.CommandFactory;
import org.jenkinsci.main.modules.sshd.SshCommandFactory.CommandLine;

/**
 * Use {@link SshCommandFactory}s to find the right implementation for the exec request from SSH clients.
 *
 * @author Kohsuke Kawaguchi
 */
public class CommandFactoryImpl implements CommandFactory {

    @Override
    public Command createCommand(ChannelSession channel, String command) {
        CommandLine cmd = new CommandLine(command);
        for (SshCommandFactory scf : SshCommandFactory.all()) {
            Command c = scf.create(cmd);
            if (c!=null)
                return c;
        }
        return new InvalidCommand(command);
    }
}
