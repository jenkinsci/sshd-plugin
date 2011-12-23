package org.jenkinsci.main.modules.sshd;

import hudson.Extension;
import hudson.cli.BaseCLICommand;
import org.apache.sshd.server.Command;

import java.io.PrintStream;
import java.util.Locale;

/**
 * {@link SshCommandFactory} that invokes {@link BaseCLICommand}s.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class BaseCLICommandAdapter extends SshCommandFactory {
    @Override
    public Command create(CommandLine commandLine) {
        String cmd = commandLine.get(0);
        final BaseCLICommand c = BaseCLICommand.clone(cmd);
        if (c==null)        return null;    // no such command

        return new AsynchronousCommand(commandLine) {
            @Override
            protected int run() throws Exception {
                // TODO: is there any way to learn the locale of the client?
                return c.main(getCmdLine(), Locale.getDefault(), getInputStream(), new PrintStream(getOutputStream()), new PrintStream(getErrorStream()));
            }
        };
    }
}
