package de.kiaim.anon.exception;

public class ArxGeneralException extends AnonymizationException {
  public ArxGeneralException(String message) {
    super("ANON_2_1_1", "An error occured while running ARX."+message);
  }
}
