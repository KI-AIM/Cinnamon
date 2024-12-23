package de.kiaim.anon.exception;

public class NoOptimumFoundException extends AnonymizationException {
  public NoOptimumFoundException() {
    super("ANON_1_1_1", "No solution found for the anonymization with the given configuration.");
  }}
