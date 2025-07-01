package de.kiaim.cinnamon.test.util;

import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.util.TestSocketUtils;

import java.io.IOException;
import java.lang.reflect.Field;

/**
 * Extension setting up a MockWebServer.
 * Can be used with {@link WithMockWebServer}.
 *
 * @author Daniel Preciado-Marquez
 */
public class MockWebServerExtension implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback {

	private static final int mockBackEndPort = TestSocketUtils.findAvailableTcpPort();
	private static final String MOCK_BACK_END_KEY = "mockBackEnd";

	@Override
	public void beforeAll(final ExtensionContext context) {
		System.setProperty("cinnamon.external-server.technical-evaluation-server.urlServer",
		                   String.format("http://localhost:%s", mockBackEndPort));
		System.setProperty("cinnamon.external-server.synthetization-server.urlServer",
		                   String.format("http://localhost:%s", mockBackEndPort));
		System.setProperty("cinnamon.external-server.anonymization-server.urlServer",
		                   String.format("http://localhost:%s", mockBackEndPort));
	}

	@Override
	public void beforeEach(final ExtensionContext context) throws IOException {
		MockWebServer mockBackEnd = new MockWebServer();
		mockBackEnd.start(mockBackEndPort);
		getStore(context).put(MOCK_BACK_END_KEY, mockBackEnd);

		// Re-inject the MockWebServer instance after it's created
		injectFields(context.getRequiredTestInstance(), mockBackEnd);
	}

	@Override
	public void afterEach(final ExtensionContext context) throws IOException {
		MockWebServer mockBackEnd = getStore(context).remove(MOCK_BACK_END_KEY, MockWebServer.class);
		if (mockBackEnd != null) {
			mockBackEnd.shutdown();
		}
	}

	private void injectFields(final Object testInstance, final MockWebServer mockBackEnd) {
		Class<?> testClass = testInstance.getClass();
		try {
			Field[] fields = testClass.getDeclaredFields();

			for (Field field : fields) {
				if (field.getType() == MockWebServer.class) {
					field.setAccessible(true);
					field.set(testInstance, mockBackEnd);
				} else if (field.getType() == int.class && field.getName().equals("mockBackEndPort")) {
					field.setAccessible(true);
					field.setInt(testInstance, mockBackEndPort);
				}
			}
		} catch (IllegalAccessException e) {
			throw new RuntimeException("Failed to inject MockWebServer", e);
		}
	}

	private ExtensionContext.Store getStore(final ExtensionContext context) {
		return context.getStore(ExtensionContext.Namespace.create(getClass(), context.getRequiredTestInstance()));
	}
}
