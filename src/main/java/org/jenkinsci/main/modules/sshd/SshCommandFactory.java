package org.jenkinsci.main.modules.sshd;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.util.QuotedStringTokenizer;
import jenkins.model.Jenkins;
import org.apache.sshd.server.Command;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;

/**
 * Extension point for adding commands invokable via SSH.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class SshCommandFactory implements ExtensionPoint {
    /**
     * If this factory understands the specified command, return a non-null {@link Command}
     * and that command will be used to handle the request.
     * 
     * Otherwise return null to let other {@link SshCommandFactory}s handle it.
     * 
     * @param commandLine
     *      Represents the command line the client wants to invoke.
     */
    public abstract Command create(CommandLine commandLine);

    /**
     * Represents a command line.
     *
     * <p>
     * Unlike the rest of Unix, SSH protocol uses a single string (as opposed to string array)
     * to pass around a whole command line between the client and the server,
     * and it's up to the server to interpret that string as an array.
     *
     * <p>
     * This class encapsulates this single-line command arguments and provide both
     * tokenized versions (for typical use) and the direct access to that string (for unusual case),
     * thereby ensuring the consistency across tokenization.
     *
     * <p>
     * This class implements tokenization that correctly handles escaping by quotes.
     */
    public static final class CommandLine extends AbstractList<String> {
        private final String singleLine;
        private final List<String> tokenized;
        public CommandLine(String singleLine) {
            this.singleLine = singleLine;
            tokenized = Arrays.asList(QuotedStringTokenizer.tokenize(singleLine));
        }

        /**
         * Returns unaltered raw string.
         */
        public String getSingleLine() {
            return singleLine;
        }

        @Override
        public String get(int index) {
            return tokenized.get(index);
        }

        @Override
        public int size() {
            return tokenized.size();
        }
    }

    public static ExtensionList<SshCommandFactory> all() {
        return ExtensionList.lookup(SshCommandFactory.class);
    }
}
