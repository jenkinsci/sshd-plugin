package org.jenkinsci.main.modules.cli.auth.ssh;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import hudson.model.User;
import hudson.model.userproperty.UserPropertyCategory;
import hudson.util.FormValidation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.server.auth.pubkey.KeySetPublickeyAuthenticator;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * @author Kohsuke Kawaguchi
 */
public class UserPropertyImpl extends UserProperty {
    public String authorizedKeys;

    @DataBoundConstructor
    public UserPropertyImpl(String authorizedKeys) {
        this.authorizedKeys = authorizedKeys;
    }

    /**
     * Checks if this user has the given public key in his {@link #authorizedKeys}.
     */
    public boolean has(PublicKey pk) {

        KeySetPublickeyAuthenticator keySetPublickeyAuthenticator =
                new KeySetPublickeyAuthenticator("foo", getPublicKeys(this.authorizedKeys));
        return keySetPublickeyAuthenticator.authenticate(null, pk, null);
    }

    public boolean isAuthorizedKey(String sig) {
        // we should have only one but the API is not documented on what is supported,
        // so we suppose many
        List<PublicKey> keys = getPublicKeys(sig);
        if (keys.isEmpty()) {
            return false;
        }
        for (PublicKey key : keys) {
            boolean authz = has(key);
            if (authz) {
                return true;
            }
        }
        return false;
    }

    private static List<PublicKey> getPublicKeys(String keys) {
        return
                Arrays.stream(keys.split("\n")).map(s -> {
                    try {
                        return PublicKeyEntry.parsePublicKeyEntry(s).resolvePublicKey(null, null, null);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).toList();
    }

    @Extension
    @Symbol("sshPublicKey")
    public static final class DescriptorImpl extends UserPropertyDescriptor {
        @NonNull
        public String getDisplayName() {
            return "SSH Public Keys";
        }

        @Override
        public @NonNull UserPropertyCategory getUserPropertyCategory() {
            return UserPropertyCategory.get(UserPropertyCategory.Security.class);
        }

        public UserProperty newInstance(User user) {
            return null;
        }

        public FormValidation doCheckAuthorizedKeys(@QueryParameter String value) throws IOException {
            // Try to match behavior of isAuthorizedKey as far as parsing.
            final BufferedReader r = new BufferedReader(new StringReader(value));
            String s;
            while ((s = r.readLine()) != null) {
                if(s.isEmpty()){
                    continue;
                }
                try {
                    getPublicKeys(s);
                } catch (Exception ex) {
                    return FormValidation.error(ex.getMessage());
                }
            }
            return FormValidation.ok();
        }

    }

    public static User findUser(PublicKey identity) {
        for (User u : User.getAll()) {
            UserPropertyImpl p = u.getProperty(UserPropertyImpl.class);
            if (p!=null && p.has(identity))
                return u;
        }
        return null;
    }
}
