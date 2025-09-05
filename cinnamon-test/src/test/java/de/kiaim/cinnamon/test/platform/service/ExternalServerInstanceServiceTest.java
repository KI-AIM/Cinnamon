package de.kiaim.cinnamon.test.platform.service;

import de.kiaim.cinnamon.platform.model.configuration.ExternalHost;
import de.kiaim.cinnamon.platform.model.configuration.ExternalServer;
import de.kiaim.cinnamon.platform.model.configuration.ExternalServerInstance;
import de.kiaim.cinnamon.platform.repository.BackgroundProcessRepository;
import de.kiaim.cinnamon.platform.service.ExternalServerInstanceService;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.TestSocketUtils;

import java.io.IOException;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ExternalServerInstanceServiceTest {

	ExternalHost host;
	ExternalServer es;
	ExternalServerInstance esi1;
	ExternalServerInstance esi2;
	ExternalServerInstance esi3;

	@BeforeEach
	public void setup() {
		host = new ExternalHost();
		host.setName("localhost");
		host.setUrl("http://localhost");

		es = createConfig();
		esi1 = es.getInstances().get("esi1");
		esi2 = es.getInstances().get("esi2");
		esi3 = es.getInstances().get("esi3");
	}

	@Test
	public void findAvailable() {
		var service = createService(0, 0, 0);

		var esi = service.findAvailableExternalServerInstance(es, true);

		assertNotNull(esi);
		assertEquals(esi3.getName(), esi.getName());
	}

	@Test
	public void findAvailableLeastUsage() {
		var service = createService(1, 0, 1);

		var esi = service.findAvailableExternalServerInstance(es, true);

		assertNotNull(esi);
		assertEquals(esi2.getName(), esi.getName());
	}

	@Test
	public void findAvailableMaxProcesses() {
		var service = createService(2, 1, 3);

		esi2.setMaxParallelProcess(1);
		var esi = service.findAvailableExternalServerInstance(es, false);

		assertNotNull(esi);
		assertEquals(esi1.getName(), esi.getName());
	}

	@Test
	public void findAvailableHealthy() {
		withMockBackend((mockBackEnd) -> {
			// Ordered by the number of running processes
			enqueueStatusCheck(mockBackEnd, 200, "DOWN");
			enqueueStatusCheck(mockBackEnd, 404, "");
			enqueueStatusCheck(mockBackEnd, 200, "UP");

			var service = createService(2, 1, 3);

			es.setMinUp(1);
			var esi = service.findAvailableExternalServerInstance(es, false);

			assertNotNull(esi);
			assertEquals(esi3.getName(), esi.getName());
		});
	}

	@Test
	public void findAvailableExtendedTimeout() {
		withMockBackend((mockBackEnd) -> {
			enqueueStatusCheck(mockBackEnd, 200, "DOWN", 1_000);
			enqueueStatusCheck(mockBackEnd, 404, "", 1_000);
			enqueueStatusCheck(mockBackEnd, 200, "UP", 1_000);

			var service = createService(2, 1, 3);
			es.setHealthTimeout(2_000);
			es.setMinUp(1);
			var esi = service.findAvailableExternalServerInstance(es, false);

			assertNotNull(esi);
			assertEquals(esi3.getName(), esi.getName());
		});
	}

	@Test
	public void findAvailableTimeout() {
		withMockBackend((mockBackEnd) -> {
			enqueueStatusCheck(mockBackEnd, 200, "DOWN", 1_000);
			enqueueStatusCheck(mockBackEnd, 404, "", 1_000);
			enqueueStatusCheck(mockBackEnd, 200, "UP", 1_000);

			var service = createService(2, 1, 3);
			es.setHealthTimeout(500);
			es.setMinUp(1);
			var esi = service.findAvailableExternalServerInstance(es, false);

			assertNull(esi);
		});
	}

	private ExternalServer createConfig() {
		var es = new ExternalServer();
		es.setName("es");

		addInstance("esi1", es);
		addInstance("esi2", es);
		addInstance("esi3", es);

		es.setMinUp(es.getInstances().size());

		return es;
	}

	private void addInstance(String name, ExternalServer es) {
		var esi = new ExternalServerInstance();
		esi.setName(name);

		esi.setServer(es);
		es.getInstances().put(esi.getName(), esi);

		esi.setHost(host);
		host.getInstances().add(esi);
	}

	private ExternalServerInstanceService createService(final long count1, final long count2, final long count3) {
		BackgroundProcessRepository repo = mock(BackgroundProcessRepository.class);
		when(repo.countByServerInstance(eq(esi1.getId()))).thenReturn(count1);
		when(repo.countByServerInstance(eq(esi2.getId()))).thenReturn(count2);
		when(repo.countByServerInstance(eq(esi3.getId()))).thenReturn(count3);
		return new ExternalServerInstanceService(repo);
	}

	private void enqueueStatusCheck(final MockWebServer mockBackEnd, final int httpStatus, final String status) {
		enqueueStatusCheck(mockBackEnd, httpStatus, status, 0);
	}

	private void enqueueStatusCheck(final MockWebServer mockBackEnd, final int httpStatus, final String status,
	                                final long delay) {
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .headersDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
				                    .code(httpStatus)
				                    .body("{\"status\": \"" + status + "\"}")
				                    .build());
	}

	private void withMockBackend(final Consumer<MockWebServer> runnable) {
		try (MockWebServer mockBackEnd = new MockWebServer()) {
			final int mockBackEndPort = TestSocketUtils.findAvailableTcpPort();
			mockBackEnd.start(mockBackEndPort);

			esi1.setPort(mockBackEndPort);
			esi2.setPort(mockBackEndPort);
			esi3.setPort(mockBackEndPort);

			runnable.accept(mockBackEnd);
		} catch (IOException e) {
			fail(e);
		}
	}

}
