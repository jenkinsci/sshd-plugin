package org.jenkinsci.main.modules.sshd;

import hudson.model.User;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SessionAware;
import org.apache.sshd.server.session.ServerSession;
import org.jenkinsci.main.modules.sshd.SshCommandFactory.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;

import org.acegisecurity.context.SecurityContext;

import javax.annotation.CheckForNull;

/**
 * Partial {@link Command} implementation that uses a thread to run a command.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AsynchronousCommand implements Command, SessionAware, Runnable {
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

            // run the command in the context of the authenticated user
            SecurityContext old = SecurityContextHolder.getContext();
            User user = getCurrentUser();
            if (user!=null)
                ACL.impersonate(user.impersonate());

            try {
                i = AsynchronousCommand.this.runInt();
            } finally {
                out.flush(); // working around SSHD-154
                err.flush();
                SecurityContextHolder.setContext(old);
            }
            callback.onExit(i);
        } catch (Exception e) {
            // report the cause of the death to the client
            //TODO: Consider switching to UTF-8
            PrintWriter ps = new PrintWriter(new OutputStreamWriter(err, Charset.defaultCharset()));
            e.printStackTrace(ps);
            ps.flush();

            callback.onExit(255,e.getMessage());
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
