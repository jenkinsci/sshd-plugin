package org.jenkinsci.main.modules.sshd;

import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import jenkins.model.Jenkins;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.session.ServerSessionAware;
import org.jenkinsci.main.modules.sshd.SshCommandFactory.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;

import javax.annotation.CheckForNull;

/**
 * Partial {@link Command} implementation that uses a thread to run a command.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AsynchronousCommand implements Command, ServerSessionAware, Runnable {
    private InputStream in;
    private OutputStream out;
    private OutputStream err;
    private ExitCallback callback;
    private CommandLine cmdLine;
    private Thread thread;
    private ServerSession session;
    private Environment environment;

    protected AsynchronousCommand(CommandLine cmdLine) {
        this.cmdLine = cmdLine;
    }

    public void setInputStream(InputStream in) {
        this.in = in;
    }
    
    public void setOutputStream(OutputStream out) {
        this.out = out;
    }

    public void setErrorStream(OutputStream err) {
        this.err = err;
    }

    public InputStream getInputStream() {
        return in;
    }

    public OutputStream getOutputStream() {
        return out;
    }

    public OutputStream getErrorStream() {
        return err;
    }

    public CommandLine getCmdLine() {
        return cmdLine;
    }

    public void setExitCallback(ExitCallback callback) {
        this.callback = callback;
    }

    public ServerSession getSession() {
        return session;
    }

    public void setSession(ServerSession session) {
        this.session = session;
    }

    @CheckForNull
    protected User getCurrentUser() {
        final Jenkins jenkins = Jenkins.getInstance();
        if (jenkins != null && jenkins.isUseSecurity()) {
            return User.get(getSession().getUsername());    // then UserAuthNamedFactory must have done public key auth
        } else {
            return null;    // not authenticated. anonymous.
        }
    }

    public Environment getEnvironment() {
        return environment;
    }

    public void start(ChannelSession channel, Environment env) throws IOException {
        start(env);
    }

    public void start(Environment env) throws IOException {
        this.environment = env;
        thread = new Thread(this);
        thread.setName("SSH command: " + cmdLine.getSingleLine());
        thread.start();
    }

    protected abstract int runCommand() throws Exception;

    public void run() {
        try {
            int i;
            User user = getCurrentUser();
            if (user != null) {
              try (ACLContext ctx = ACL.as(user)) {
                i = AsynchronousCommand.this.runCommand();
              }
            } else {
              i = AsynchronousCommand.this.runCommand();
            }
            flushOutputs();
            callback.onExit(i);
        } catch (Exception e) {
            // report the cause of the death to the client
            //TODO: Consider switching to UTF-8
            PrintWriter ps = new PrintWriter(new OutputStreamWriter(err, Charset.defaultCharset()));
            e.printStackTrace(ps);
            ps.flush();
            flushOutputs();
            callback.onExit(255,e.getMessage());
        }
    }

    /**
     *  working around SSHD-154
     */
    private void flushOutputs() {
        try {
            out.flush();
            err.flush();
        } catch (IOException ioException) {
           //NOOP
        }
    }

    @Override
    public void destroy(ChannelSession channel) throws Exception {
        destroy();
    }

    public void destroy() {
        Thread.currentThread().interrupt();
    }
}
