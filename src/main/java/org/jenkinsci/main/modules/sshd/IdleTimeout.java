package org.jenkinsci.main.modules.sshd;

import org.apache.sshd.server.ServerFactoryManager;
import org.apache.sshd.server.SshServer;

import java.util.logging.Logger;

/**
 * @author Réda Housni Alaoui
 */
public class IdleTimeout {

	private static final Logger LOGGER = Logger.getLogger(IdleTimeout.class.getName());

	private final Long timeoutInMilliseconds;

	IdleTimeout(Long timeoutInMilliseconds) {
		this.timeoutInMilliseconds = timeoutInMilliseconds;
	}

	public static IdleTimeout fromSystemProperty(String propertyName) {
		String propertyValue = System.getProperty(propertyName);
		if (propertyValue == null) {
			return new IdleTimeout(null);
		}

		try {
			return new IdleTimeout(Long.parseLong(propertyValue));
		} catch (NumberFormatException nfe) {
			LOGGER.warning("SSHD Idle Timeout configuration skipped. " + propertyName + " value (" +
					propertyValue + ") isn't a long.");
		}
		return new IdleTimeout(null);
	}

	public void apply(SshServer sshd) {
		if (timeoutInMilliseconds == null) {
			return;
		}

		sshd.getProperties().put(ServerFactoryManager.IDLE_TIMEOUT, timeoutInMilliseconds);
		// Read timeout must also be changed
		long readTimeout = 0;
		if (timeoutInMilliseconds != 0) {
			readTimeout = ServerFactoryManager.DEFAULT_NIO2_READ_TIMEOUT - ServerFactoryManager.DEFAULT_IDLE_TIMEOUT + timeoutInMilliseconds;
		}
		sshd.getProperties().put(ServerFactoryManager.NIO2_READ_TIMEOUT, readTimeout);
	}

}
