package de.kiaim.cinnamon.anonymization.exception;

public class ArxDataSetProcessingException extends AnonymizationException {
  public ArxDataSetProcessingException(String message) {
    super("ANON_2_1_2", "Failed to process the dataset in ARX."+message);
  }
}
