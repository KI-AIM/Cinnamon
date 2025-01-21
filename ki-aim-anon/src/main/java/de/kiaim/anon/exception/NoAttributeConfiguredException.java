package de.kiaim.anon.exception;

public class NoAttributeConfiguredException extends AnonymizationException {
  public NoAttributeConfiguredException() {
    super("ANON_1_2_2", "At least one attribute configuration must be defined.");
  }
}