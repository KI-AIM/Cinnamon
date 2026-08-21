package de.kiaim.cinnamon.platform.model.enumeration;

import lombok.Getter;
import org.springframework.lang.Nullable;

import java.time.Duration;

/**
 * Presets for formatting a resolved date/time selector value (e.g. {@code ${invitation.expiresAt}}) into a
 * user-friendly string.
 * Requested by appending {@code |presetName} (case-insensitive) to the selector, e.g.
 * {@code ${invitation.expiresAt|smart}}; {@link #COMBINED} is used when no format is requested.
 *
 * @author Daniel Preciado-Marquez
 */
@Getter
public enum DateFormatPreset {

	/**
	 * Always shows both the relative and the absolute time, e.g. "in 3 days (23.08.2026, 09:00)".
	 */
	COMBINED(null),

	/**
	 * Shows the relative time while it is less than a week away, the absolute time otherwise.
	 */
	SMART(Duration.ofDays(7)),

	/**
	 * Prefers the relative time; only switches to the absolute time about a month out.
	 */
	RELATIVE(Duration.ofDays(30)),

	/**
	 * Prefers the absolute time; only shows the relative time within the next day.
	 */
	ABSOLUTE(Duration.ofDays(1)),
	;

	/**
	 * How far in the future the value may still be for the relative wording to be used; beyond this, the absolute
	 * time is shown instead. {@code null} for {@link #COMBINED}, which always shows both.
	 */
	@Nullable
	private final Duration relativeThreshold;

	DateFormatPreset(@Nullable final Duration relativeThreshold) {
		this.relativeThreshold = relativeThreshold;
	}
}
