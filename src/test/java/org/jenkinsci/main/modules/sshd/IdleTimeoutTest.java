package org.jenkinsci.main.modules.sshd;


import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.server.SshServer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author Réda Housni Alaoui
 */
public class IdleTimeoutTest {

	private SshServer sshd;

	@Before
	public void before() {
		sshd = new SshServer();
	}

	@Test
	public void applyEmptyTimeout() {
		IdleTimeout idleTimeout = new IdleTimeout(null);

		idleTimeout.apply(sshd);

		Map<String, Object> properties = sshd.getProperties();
		Assert.assertFalse(properties.containsKey(CoreModuleProperties.IDLE_TIMEOUT.getName()));
		Assert.assertFalse(properties.containsKey(CoreModuleProperties.NIO2_READ_TIMEOUT.getName()));
	}

	@Test
	public void apply24HoursTimeout() {
		long timeoutInMilliseconds = TimeUnit.HOURS.toMillis(24);
		IdleTimeout idleTimeout = new IdleTimeout(timeoutInMilliseconds);

		idleTimeout.apply(sshd);

		Assert.assertEquals(timeoutInMilliseconds, CoreModuleProperties.IDLE_TIMEOUT.get(sshd).get().toMillis());
		Long readTimeout = CoreModuleProperties.NIO2_READ_TIMEOUT.get(sshd).get().toMillis();
		Assert.assertTrue((Long) readTimeout > timeoutInMilliseconds);
	}

	@Test
	public void applyFromAbsentSystemProperty() {
		IdleTimeout idleTimeout = IdleTimeout.fromSystemProperty(IdleTimeoutTest.class.getName());

		idleTimeout.apply(sshd);

		Map<String, Object> properties = sshd.getProperties();
		Assert.assertFalse(properties.containsKey(CoreModuleProperties.IDLE_TIMEOUT.getName()));
		Assert.assertFalse(properties.containsKey(CoreModuleProperties.NIO2_READ_TIMEOUT.getName()));
	}

}
