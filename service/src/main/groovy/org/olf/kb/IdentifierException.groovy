package org.olf.kb;

// Special exception we can catch and do logic on -- used to coordinate between IdentifierService and TIRSs
public class IdentifierException extends Exception {
  public static final Long GENERIC_ERROR = 0L;
  public static final Long MULTIPLE_IDENTIFIER_MATCHES = 1L;
  public static final Long FIX_IDENTIFIER_ERROR = 2L;


  final Long code;

  public IdentifierException(String errorMessage, Long code) {
    super(errorMessage);
    this.code = code;
  }

  public IdentifierException(String errorMessage) {
    super(errorMessage);
    this.code = GENERIC_ERROR;
  }
}