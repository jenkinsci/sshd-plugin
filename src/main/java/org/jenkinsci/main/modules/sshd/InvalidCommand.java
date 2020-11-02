package org.jenkinsci.main.modules.sshd;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * {@link Command} implementation that exits by complaining that there's no such command.
 * 
 * @author Kohsuke Kawaguchi
 */
public class InvalidCommand implements Command {
    private final String command;
    private ExitCallback callback;
    private OutputStream err;

    public InvalidCommand(String command) {
        this.command = command;
    }

    public void setInputStream(InputStream in) {
    }

    public void setOutputStream(OutputStream out) {
    }

    public void setErrorStream(OutputStream err) {
        this.err = err;
    }

    public void setExitCallback(ExitCallback callback) {
        this.callback = callback;
    }

    @Override
    public void start(ChannelSession channel, Environment env) throws IOException {
        //TODO: Consider switching to UTF-8
        err.write(("Unknown command: "+command+"\n").getBytes(Charset.defaultCharset()));
        err.flush(); // working around SSHD-154
        err.close();
        callback.onExit(255,"Unknown command: "+command);
    }

    @Override
    public void destroy(ChannelSession channel) throws Exception {

    }
}
