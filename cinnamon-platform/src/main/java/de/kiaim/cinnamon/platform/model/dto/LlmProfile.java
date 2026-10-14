package de.kiaim.cinnamon.platform.model.dto;

/**
 * Public, non-secret description of an LLM profile.
 *
 * Credentials and connection details are deliberately not exposed to the
 * frontend. They remain available only to the services that execute the LLM
 * request.
 */
public record LlmProfile(String name) {
}
