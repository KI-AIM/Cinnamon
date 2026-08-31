package de.kiaim.cinnamon.test.util;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.util.TestSocketUtils;

import java.lang.reflect.Field;

/**
 * Extension setting up a GreenMail SMTP server for testing mail sending.
 * Injects the started {@link GreenMail} instance into a field of type {@link GreenMail} and the port it is
 * listening on into a field annotated with {@link GreenMailPort}.
 * Can be used with {@link WithGreenMail}.
 *
 * @author Daniel Preciado-Marquez
 */
public class GreenMailExtension implements BeforeEachCallback, AfterEachCallback {

	private static final int greenMailPort = TestSocketUtils.findAvailableTcpPort();
	private static final String GREEN_MAIL_KEY = "greenMail";

	@Override
	public void beforeEach(final ExtensionContext context) {
		final GreenMail greenMail = new GreenMail(new ServerSetup(greenMailPort, null, ServerSetup.PROTOCOL_SMTP));
		greenMail.start();
		getStore(context).put(GREEN_MAIL_KEY, greenMail);

		injectFields(context.getRequiredTestInstance(), greenMail);
	}

	@Override
	public void afterEach(final ExtensionContext context) {
		final GreenMail greenMail = getStore(context).remove(GREEN_MAIL_KEY, GreenMail.class);
		if (greenMail != null) {
			greenMail.stop();
		}
	}

	private void injectFields(final Object testInstance, final GreenMail greenMail) {
		final Class<?> testClass = testInstance.getClass();
		try {
			final Field[] fields = testClass.getDeclaredFields();

			for (final Field field : fields) {
				if (field.getType() == GreenMail.class) {
					field.setAccessible(true);
					field.set(testInstance, greenMail);
				} else if (field.isAnnotationPresent(GreenMailPort.class)) {
					field.setAccessible(true);
					field.set(testInstance, greenMailPort);
				}
			}
		} catch (IllegalAccessException e) {
			throw new RuntimeException("Failed to inject GreenMail", e);
		}
	}

	private ExtensionContext.Store getStore(final ExtensionContext context) {
		return context.getStore(ExtensionContext.Namespace.create(getClass(), context.getRequiredTestInstance()));
	}
}
