package org.jenkinsci.main.modules.cli.auth.ssh;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import hudson.model.User;
import hudson.util.FormValidation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.security.PublicKey;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * @author Kohsuke Kawaguchi
 */
public class UserPropertyImpl extends UserProperty {
    private static final PublicKeySignatureWriter signature = new PublicKeySignatureWriter();
    public String authorizedKeys;

    @DataBoundConstructor
    public UserPropertyImpl(String authorizedKeys) {
        this.authorizedKeys = authorizedKeys;
    }

    /**
     * Checks if this user has the given public key in his {@link #authorizedKeys}.
     */
    public boolean has(PublicKey pk) {
        return isAuthorizedKey(signature.asString(pk));
    }

    public boolean isAuthorizedKey(String sig) {
        try {
            final BufferedReader r = new BufferedReader(new StringReader(authorizedKeys));
            String s;
            while ((s=r.readLine())!=null) {
                String[] tokens = s.split("\\s+");
                if (tokens.length>=2 && tokens[1].equals(sig))
                    return true;
            }
            return false;
        } catch (IOException e) {// impossible
            return false;
        }
    }

    @Extension
    @Symbol("sshPublicKey")
    public static final class DescriptorImpl extends UserPropertyDescriptor {
        @NonNull
        public String getDisplayName() {
            return "SSH Public Keys";
        }

        public UserProperty newInstance(User user) {
            return null;
        }

        public FormValidation doCheckAuthorizedKeys(@QueryParameter String value) throws IOException {
            // Try to match behavior of isAuthorizedKey as far as parsing.
            final BufferedReader r = new BufferedReader(new StringReader(value));
            String s;
            while ((s = r.readLine()) != null) {
                String[] tokens = s.split("\\s+");
                if (tokens.length < 2) {
                    if (s.trim().isEmpty()) {
                        continue;
                    } else {
                        return FormValidation.warning("Unexpected line: ‘" + s + "’");
                    }
                }
                if (!tokens[0].matches("ssh-[a-z]+")) {
                    return FormValidation.warning("‘" + tokens[0] + "’ does not look like a valid key type");
                }
                if (!tokens[1].matches("[a-zA-Z0-9/+]+=*")) {
                    return FormValidation.error("‘" + tokens[1] + "’ does not look like a Base64-encoded public key");
                }
            }
            return FormValidation.ok();
        }

    }

    public static User findUser(PublicKey identity) {
        String sig = signature.asString(identity);
        for (User u : User.getAll()) {
            UserPropertyImpl p = u.getProperty(UserPropertyImpl.class);
            if (p!=null && p.isAuthorizedKey(sig))
                return u;
        }
        return null;
    }
}
