package de.kiaim.cinnamon.test.platform;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Makes every request performed through the autoconfigured {@link org.springframework.test.web.servlet.MockMvc}
 * carry a valid CSRF token by default, so existing tests do not need to add {@code .with(csrf())} to every
 * {@code mockMvc.perform(...)} call individually.
 * <p>
 * This mirrors how the real Angular frontend behaves: it always has a CSRF cookie by the time it issues a
 * state-changing request, see {@link de.kiaim.cinnamon.platform.config.SecurityConfig}. Tests that specifically
 * exercise CSRF protection (e.g. for endpoints exempted from it) should override this with an explicit,
 * token-less request.
 *
 * @author Daniel Preciado-Marquez
 */
@TestConfiguration
public class TestMockMvcCsrfConfig {

	@Bean
	public MockMvcBuilderCustomizer csrfMockMvcBuilderCustomizer() {
		return builder -> builder.defaultRequest(get("/").with(csrf()));
	}
}
