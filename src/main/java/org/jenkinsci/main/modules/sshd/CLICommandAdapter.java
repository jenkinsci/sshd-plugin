package org.jenkinsci.main.modules.sshd;

import hudson.Extension;
import hudson.cli.CLICommand;
import org.apache.sshd.server.Command;

import java.io.PrintStream;
import java.util.Locale;

/**
 * {@link SshCommandFactory} that invokes {@link CLICommand}s.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class CLICommandAdapter extends SshCommandFactory {
    @Override
    public Command create(CommandLine commandLine) {
        String cmd = commandLine.get(0);
        final CLICommand c = CLICommand.clone(cmd);
        if (c==null)        return null;    // no such command

        return new AsynchronousCommand(commandLine) {
            @Override
            protected int run() throws Exception {
                // TODO: is there any way to learn the locale of the client?
                c.setTransportAuth(getCurrentUser().impersonate());
                CommandLine cmds = getCmdLine();
                return c.main(cmds.subList(1,cmds.size()), Locale.getDefault(), getInputStream(), new PrintStream(getOutputStream()), new PrintStream(getErrorStream()));
            }
        };
    }
}
