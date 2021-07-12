package org.jenkinsci.main.modules.sshd;


import org.apache.sshd.server.ServerFactoryManager;
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
		Assert.assertFalse(properties.containsKey(ServerFactoryManager.IDLE_TIMEOUT));
		Assert.assertFalse(properties.containsKey(ServerFactoryManager.NIO2_READ_TIMEOUT));
	}

	@Test
	public void apply24HoursTimeout() {
		long timeoutInMilliseconds = TimeUnit.HOURS.toMillis(24);
		IdleTimeout idleTimeout = new IdleTimeout(timeoutInMilliseconds);

		idleTimeout.apply(sshd);

		Map<String, Object> properties = sshd.getProperties();
		Assert.assertEquals(timeoutInMilliseconds, properties.get(ServerFactoryManager.IDLE_TIMEOUT));
		Object readTimeout = properties.get(ServerFactoryManager.NIO2_READ_TIMEOUT);
		Assert.assertTrue(readTimeout instanceof Long);
		Assert.assertTrue((Long) readTimeout > timeoutInMilliseconds);
	}

	@Test
	public void applyFromAbsentSystemProperty() {
		IdleTimeout idleTimeout = IdleTimeout.fromSystemProperty(IdleTimeoutTest.class.getName());

		idleTimeout.apply(sshd);

		Map<String, Object> properties = sshd.getProperties();
		Assert.assertFalse(properties.containsKey(ServerFactoryManager.IDLE_TIMEOUT));
		Assert.assertFalse(properties.containsKey(ServerFactoryManager.NIO2_READ_TIMEOUT));
	}

}
