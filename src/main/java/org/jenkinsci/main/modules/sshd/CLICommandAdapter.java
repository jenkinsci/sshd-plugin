package org.jenkinsci.main.modules.sshd;

import hudson.CloseProofOutputStream;
import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.model.User;
import org.apache.sshd.server.command.Command;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
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
            public int runCommand() throws IOException {
                User u = getCurrentUser();
                if (u != null) {
                    c.setTransportAuth2(u.impersonate2());
                }

                CommandLine cmds = getCmdLine();

                //TODO: Consider switching to UTF-8
                //TODO: Consider removing the CloseProofOutputStream wrapper when SSHD-1257 is available
                return c.main(cmds.subList(1,cmds.size()), Locale.getDefault(), getInputStream(),
                        new PrintStream(new CloseProofOutputStream(getOutputStream()), false, Charset.defaultCharset().toString()),
                        new PrintStream(new CloseProofOutputStream(getErrorStream()), false, Charset.defaultCharset().toString()));
            }
        };
    }
}
