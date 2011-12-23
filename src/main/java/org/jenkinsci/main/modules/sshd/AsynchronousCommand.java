package org.jenkinsci.main.modules.sshd;

import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.jenkinsci.main.modules.sshd.SshCommandFactory.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Partial {@link Command} implementation that uses a thread to run a command.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AsynchronousCommand implements Command {
    private InputStream in;
    private OutputStream out;
    private OutputStream err;
    private ExitCallback callback;
    private CommandLine cmdLine;
    private Thread thread;

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

    public void start(Environment env) throws IOException {
        thread = new Thread(new Runnable() {
            public void run() {
                try {
                    int i;
                    try {
                        i = AsynchronousCommand.this.run();
                    } finally {
                        out.flush(); // working around SSHD-154
                        err.flush();
                    }
                    callback.onExit(i);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "SSH command execution failed: "+e.getMessage(), e);
                    callback.onExit(255,e.getMessage());
                }
            }
        });
        thread.setName("SSH command: " + cmdLine.getSingleLine());
        thread.start();
    }

    protected abstract int run() throws Exception;

    public void destroy() {
        if (thread!=null)
            thread.interrupt();
    }

    private static final Logger LOGGER = Logger.getLogger(AsynchronousCommand.class.getName());
}
