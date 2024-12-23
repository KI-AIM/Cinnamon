package de.kiaim.anon.exception;

public class ArxDataSetProcessingException extends AnonymizationException {
  public ArxDataSetProcessingException(String message) {
    super("ANON_3_1", "Failed to process the dataset in ARX."+message);
  }
}
