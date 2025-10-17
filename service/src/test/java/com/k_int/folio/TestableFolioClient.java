package com.k_int.folio;

import java.net.http.HttpClient;

class TestableFolioClient extends FolioClient {
  public TestableFolioClient(
    String baseUrl,
    String tenant,
    String patronId,
    String userLogin,
    String userPassword,
    HttpClient mockHttpClient
  ) {
    super(baseUrl, tenant, patronId, userLogin, userPassword);
    // Use reflection to replace the internally created HttpClient with the mock
    try {
      java.lang.reflect.Field field = FolioClient.class.getDeclaredField("httpClient");
      field.setAccessible(true);
      field.set(this, mockHttpClient);
    } catch (Exception e) {
      throw new RuntimeException("Failed to inject mock HttpClient", e);
    }
  }
}