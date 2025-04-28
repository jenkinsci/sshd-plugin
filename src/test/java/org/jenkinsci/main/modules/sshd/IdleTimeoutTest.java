package org.jenkinsci.main.modules.sshd;


import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.server.SshServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Réda Housni Alaoui
 */
class IdleTimeoutTest {

    private SshServer sshd;

    @BeforeEach
    void setUp() {
        sshd = new SshServer();
    }

    @Test
    void applyEmptyTimeout() {
        IdleTimeout idleTimeout = new IdleTimeout(null);

        idleTimeout.apply(sshd);

        Map<String, Object> properties = sshd.getProperties();
        assertFalse(properties.containsKey(CoreModuleProperties.IDLE_TIMEOUT.getName()));
        assertFalse(properties.containsKey(CoreModuleProperties.NIO2_READ_TIMEOUT.getName()));
    }

    @Test
    void apply24HoursTimeout() {
        long timeoutInMilliseconds = TimeUnit.HOURS.toMillis(24);
        IdleTimeout idleTimeout = new IdleTimeout(timeoutInMilliseconds);

        idleTimeout.apply(sshd);

        assertEquals(timeoutInMilliseconds, CoreModuleProperties.IDLE_TIMEOUT.get(sshd).orElseThrow().toMillis());
        long readTimeout = CoreModuleProperties.NIO2_READ_TIMEOUT.get(sshd).orElseThrow().toMillis();
        assertTrue(readTimeout > timeoutInMilliseconds);
    }

    @Test
    void applyFromAbsentSystemProperty() {
        IdleTimeout idleTimeout = IdleTimeout.fromSystemProperty(IdleTimeoutTest.class.getName());

        idleTimeout.apply(sshd);

        Map<String, Object> properties = sshd.getProperties();
        assertFalse(properties.containsKey(CoreModuleProperties.IDLE_TIMEOUT.getName()));
        assertFalse(properties.containsKey(CoreModuleProperties.NIO2_READ_TIMEOUT.getName()));
    }

}
