package org.jenkinsci.main.modules.sshd.random;

import org.apache.sshd.common.random.AbstractRandomFactory;
import org.apache.sshd.common.random.Random;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * RandomFactory that should never block.
 *
 * Adapted for Jenkins from <a href="https://github.com/apache/mina-sshd/blob/sshd-1.7.0/sshd-core/src/main/java/org/apache/sshd/common/util/security/bouncycastle/BouncyCastleRandomFactory.java">BouncyCastleRandomFactory</a>
 */
@Restricted(NoExternalUse.class)
public class BouncyCastleNonBlockingRandomFactory extends AbstractRandomFactory {
    public static final String NAME = "non-blocking-bouncycastle";
    public static final BouncyCastleNonBlockingRandomFactory INSTANCE = new BouncyCastleNonBlockingRandomFactory();

    protected BouncyCastleNonBlockingRandomFactory() {
        super(NAME);
    }

    @Override
    public boolean isSupported() {
        return SecurityUtils.isBouncyCastleRegistered();
    }

    @Override
    public Random create() {
        return new BouncyCastleNonBlockingRandom();
    }
}
