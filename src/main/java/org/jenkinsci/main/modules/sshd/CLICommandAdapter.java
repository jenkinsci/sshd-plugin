package org.jenkinsci.main.modules.sshd;

import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.model.User;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Locale;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
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
            private ExitCallback callback;

            public void setExitCallback(ExitCallback callback) {
                this.callback = callback;
            }

            @Override
            public void start(ChannelSession channel, Environment env) throws IOException {
                // run as the authenticated user if we've actually authenticated the user,
                // or otherwise run as anonymous
                User u = getCurrentUser();
                if (u!=null){
                    c.setTransportAuth(u.impersonate());
                }

                CommandLine cmds = getCmdLine();

                //TODO: Consider switching to UTF-8
                int ret = c.main(cmds.subList(1,cmds.size()), Locale.getDefault(), getInputStream(),
                              new PrintStream(getOutputStream(), false, Charset.defaultCharset().toString()),
                              new PrintStream(getErrorStream(), false, Charset.defaultCharset().toString()));
                callback.onExit(ret);
                channel.close();
            }

            @Override
            public void destroy(ChannelSession channel) throws Exception {
            }

            //TODO run is no longer needed
            @Override
            protected int run() throws Exception {
                return 0;
            }
        };
    }
}
