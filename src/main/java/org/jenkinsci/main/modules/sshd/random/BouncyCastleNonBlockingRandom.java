/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jenkinsci.main.modules.sshd.random;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import org.apache.sshd.common.random.AbstractRandom;
import org.apache.sshd.common.util.ValidateUtils;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.bouncycastle.crypto.prng.RandomGenerator;
import org.bouncycastle.crypto.prng.VMPCRandomGenerator;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * BouncyCastle <code>Random</code>.
 * This pseudo random number generator uses the a very fast PRNG from BouncyCastle.
 * The JRE random will be used when creating a new generator to add some random
 * data to the seed.
 *
 * Adapted for Jenkins from <a href="https://github.com/apache/mina-sshd/blob/sshd-1.7.0/sshd-core/src/main/java/org/apache/sshd/common/util/security/bouncycastle/BouncyCastleRandom.java">BouncyCastleRandom</a> 
 */
@Restricted(NoExternalUse.class)
public final class BouncyCastleNonBlockingRandom extends AbstractRandom {
    public static final String NAME = SecurityUtils.BOUNCY_CASTLE;
    private final RandomGenerator random;

    public BouncyCastleNonBlockingRandom() {
        ValidateUtils.checkTrue(SecurityUtils.isBouncyCastleRegistered(), "BouncyCastle not registered");
        this.random = new VMPCRandomGenerator();
        // BEGIN JENKINS MODIFICATIONS
        byte[] seed = new byte[8];
        try {
            // Uses /dev/urandom, which should not block.
            SecureRandom.getInstance("NativePRNGNonBlocking").generateSeed(8);
        } catch (NoSuchAlgorithmException e) {
            // Original code from SSHD. This path will always be taken on Windows.
            seed = new SecureRandom().generateSeed(8);
        }
        // END JENKINS MODIFICATIONS
        this.random.addSeedMaterial(seed);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void fill(byte[] bytes, int start, int len) {
        this.random.nextBytes(bytes, start, len);
    }

    /**
     * Returns a pseudo-random uniformly distributed {@code int}
     * in the half-open range [0, n).
     */
    @Override
    public int random(int n) {
        ValidateUtils.checkTrue(n > 0, "Limit must be positive: %d", n);
        if ((n & -n) == n) {
            return (int) ((n * (long) next(31)) >> 31);
        }

        int bits;
        int val;
        do {
            bits = next(31);
            val = bits % n;
        } while (bits - val + (n - 1) < 0);
        return val;
    }

    private int next(int numBits) {
        int bytes = (numBits + 7) / 8;
        byte next[] = new byte[bytes];
        int ret = 0;
        random.nextBytes(next);
        for (int i = 0; i < bytes; i++) {
            ret = (next[i] & 0xFF) | (ret << 8);
        }
        return ret >>> (bytes * 8 - numBits);
    }
}