package de.kiaim.cinnamon.test.platform.service;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ExternalServerInstanceServiceTest {

	ExternalServer es;
	ExternalServerInstance esi1;
	ExternalServerInstance esi2;
	ExternalServerInstance esi3;

	@BeforeEach
	public void setup() {
		es = createConfig();
		esi1 = es.getInstances().get("esi1");
		esi2 = es.getInstances().get("esi2");
		esi3 = es.getInstances().get("esi3");
	}

	@Test
	public void findAvailable() {
		BackgroundProcessRepository repo = mock(BackgroundProcessRepository.class);
		when(repo.countByServerInstance(any())).thenReturn(0L);
		var service = new ExternalServerInstanceService(repo);
		var es = createConfig();

		var esi = service.findAvailableExternalServerInstance(es, true);

		assertNotNull(esi);
		assertEquals(esi3.getName(), esi.getName());
	}

	@Test
	public void findAvailableLeastUsage() {
		BackgroundProcessRepository repo = mock(BackgroundProcessRepository.class);
		when(repo.countByServerInstance(eq(esi1.getId()))).thenReturn(1L);
		when(repo.countByServerInstance(eq(esi2.getId()))).thenReturn(0L);
		when(repo.countByServerInstance(eq(esi3.getId()))).thenReturn(1L);
		var service = new ExternalServerInstanceService(repo);

		var esi = service.findAvailableExternalServerInstance(es, true);

		assertNotNull(esi);
		assertEquals(esi2.getName(), esi.getName());
	}

	@Test
	public void findAvailableMaxProcesses() {
		BackgroundProcessRepository repo = mock(BackgroundProcessRepository.class);
		when(repo.countByServerInstance(eq(esi1.getId()))).thenReturn(2L);
		when(repo.countByServerInstance(eq(esi2.getId()))).thenReturn(1L);
		when(repo.countByServerInstance(eq(esi3.getId()))).thenReturn(3L);
		var service = new ExternalServerInstanceService(repo);

		esi2.setMaxParallelProcess(1);
		var esi = service.findAvailableExternalServerInstance(es, false);

		assertNotNull(esi);
		assertEquals(esi1.getName(), esi.getName());
	}

	@Test
	public void findAvailableHealthy() throws IOException {
		try (MockWebServer mockBackEnd = new MockWebServer()) {
			final int mockBackEndPort = TestSocketUtils.findAvailableTcpPort();
			mockBackEnd.start(mockBackEndPort);

			esi1.setUrl(String.format("http://localhost:%s", mockBackEndPort));
			esi2.setUrl(String.format("http://localhost:%s", mockBackEndPort));
			esi3.setUrl(String.format("http://localhost:%s", mockBackEndPort));

			// Ordered by the number of running processes
			enqueueStatusCheck(mockBackEnd, 200, "DOWN");
			enqueueStatusCheck(mockBackEnd, 404, "");
			enqueueStatusCheck(mockBackEnd, 200, "UP");

			BackgroundProcessRepository repo = mock(BackgroundProcessRepository.class);
			when(repo.countByServerInstance(eq(esi1.getId()))).thenReturn(2L);
			when(repo.countByServerInstance(eq(esi2.getId()))).thenReturn(1L);
			when(repo.countByServerInstance(eq(esi3.getId()))).thenReturn(3L);
			var service = new ExternalServerInstanceService(repo);

			es.setMinUp(1);
			var esi = service.findAvailableExternalServerInstance(es, false);

			assertNotNull(esi);
			assertEquals(esi3.getName(), esi.getName());
		}
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
	}

	private void enqueueStatusCheck(final MockWebServer mockBackEnd, final int httpStatus, final String status) {
		mockBackEnd.enqueue(new MockResponse.Builder()
				                    .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                    .code(httpStatus)
				                    .body("{\"status\": \"" + status + "\"}")
				                    .build());
	}

}
