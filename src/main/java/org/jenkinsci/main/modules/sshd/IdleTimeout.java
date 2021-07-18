package org.jenkinsci.main.modules.sshd;

import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.server.SshServer;
import java.time.Duration;
import java.util.logging.Logger;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Utility class which properly manages Apache MINA idle timeout, see JENKINS-55978
 * @author Réda Housni Alaoui
 */
@Restricted(NoExternalUse.class)
public class IdleTimeout {

	private static final Logger LOGGER = Logger.getLogger(IdleTimeout.class.getName());
	public static final long ADDITIONAL_TIME_FOR_TIMEOUT = 60000;

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

		CoreModuleProperties.IDLE_TIMEOUT.set(sshd, Duration.ofMillis(timeoutInMilliseconds));
		// Read timeout must also be changed
		long readTimeout = 0;
		if (timeoutInMilliseconds != 0) {
			readTimeout = ADDITIONAL_TIME_FOR_TIMEOUT + timeoutInMilliseconds;
		}
		CoreModuleProperties.NIO2_READ_TIMEOUT.set(sshd, Duration.ofSeconds(readTimeout));
	}

}
