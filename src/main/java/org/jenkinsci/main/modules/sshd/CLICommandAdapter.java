package org.jenkinsci.main.modules.sshd;

import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.model.User;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Locale;
import org.apache.sshd.server.command.Command;

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
        if (c == null) {
            return null;    // no such command
        }

        return new AsynchronousCommand(commandLine) {

            @Override
            protected int run() throws IOException {
                User u = getCurrentUser();
                if (u!=null){
                    c.setTransportAuth(u.impersonate());
                }

                CommandLine cmds = getCmdLine();

                //TODO: Consider switching to UTF-8
                return c.main(cmds.subList(1,cmds.size()), Locale.getDefault(), getInputStream(),
                              new PrintStream(getOutputStream(), false, Charset.defaultCharset().toString()),
                              new PrintStream(getErrorStream(), false, Charset.defaultCharset().toString()));
            }
        };
    }
}
