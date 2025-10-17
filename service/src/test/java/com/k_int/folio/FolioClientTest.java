package com.k_int.folio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;


class TestResponse {
  public String data;
  public TestResponse(String data) { this.data = data; }
}

public class FolioClientTest {
  private TestableFolioClient client;
  private HttpClient mockHttpClient;
  private HttpResponse<TestResponse> mockResponse;

  private final String PATH = "/erm/sas";
  private final String[] REQUEST_HEADERS = {"X-Okapi-Tenant", "testValue"};
  private final Map<String, String> QUERY_PARAMS = Map.of("filters", "title==foo", "perPage", "20");

  @BeforeEach
  void setUp() {
    mockHttpClient = mock(HttpClient.class);
    mockResponse = (HttpResponse<TestResponse>) mock(HttpResponse.class);

    // use Mockito spy to be able to check what methods are called on the client
    client = spy(new TestableFolioClient(
      "http://okapi.example.com:9130", "diku", "patron123", "user", "pass", mockHttpClient
    ));

    // Stub the mock response for successful network calls
    when(mockResponse.body()).thenReturn(new TestResponse("Success"));
    when(mockResponse.statusCode()).thenReturn(200);

    // Respond with a completed future containing the mock response for async calls
    when(mockHttpClient.sendAsync(
      any(HttpRequest.class),
      any(HttpResponse.BodyHandler.class)) // We're not checking that the BodyHandler works as expected here
    )
    .thenReturn(CompletableFuture.completedFuture(mockResponse));
  }

  // --------------------------------------------------------------------------------
  // Synchronous HTTP Methods Tests (Verification that the actual helper is used)
  // --------------------------------------------------------------------------------

  @Test
  @DisplayName("get: Should execute the underlying async method via asyncFolioClientExceptionHelper")
  void testGet_Success() {
    // When we call the synchronous get method
    TestResponse result = client.get(PATH, REQUEST_HEADERS, QUERY_PARAMS, TestResponse.class);

    // Then we return the expected result
    assertEquals("Success", result.data);

    // Verify the path taken from get -> sendAsync
    verify(client, times(1)).get(any(), any(), any(), any());
    verify(client, times(1)).getAsync(any(), any(), any(), any());
    verify(client, times(1)).getAsyncWithResponse(any(), any(), any(), any());
    verify(mockResponse, times(1)).body();
    verify(client, times(1)).asyncFolioClientExceptionHelper(any());
  }

  /**
   * Provides different Throwable causes that should be wrapped by the async helper.
   * Note: This simulates errors that lead to ExecutionException/CompletionException.
   */
  private static Stream<Arguments> testGet_HTTP_Failure_arguments() {
    return Stream.of(
      // An unhandled RuntimeException (should be wrapped in FolioClientException)
      Arguments.of(
        Named.of( // We'll name the test case against the first argument for clarity in test reports
        "Throwing a RuntimeException from sendAsync HTTP call",
          new RuntimeException("Simulated network issue") // A standard Java exception (should be wrapped in FolioClientException)
        ),
        null,
        FolioClientException.GENERIC_ERROR, // Should map to GENERIC_ERROR
        "GET request failed: Simulated network issue", // Expected message
        RuntimeException.class, // Expected cause class
        "Simulated network issue" // Expected cause message
      ),
      Arguments.of(
        Named.of(
          "Throwing an ExecutionException wrapping a FolioClientException from CompletableFuture get()",
          null
        ),
        new ExecutionException(new FolioClientException("A faked FolioClientException", FolioClientException.FAILED_REQUEST)),
        FolioClientException.FAILED_REQUEST,
        "A faked FolioClientException",
        null,
        null
      ),
      Arguments.of(
        Named.of(
          "Throwing an ExecutionException wrapping a RuntimeException from CompletableFuture get()",
          null
        ),
        new ExecutionException(new RuntimeException("Faked RuntimeException within an ExecutionException")),
        FolioClientException.GENERIC_ERROR,
        "Unhandled async execution error",
        RuntimeException.class,
        "Faked RuntimeException within an ExecutionException"
      ),
      Arguments.of(
        Named.of(
          "Throwing a TimeoutException from CompletableFuture get()",
          null
        ),
        new TimeoutException("Faked TimeoutException"),
        FolioClientException.TIMEOUT_ERROR,
        "Async call timed out",
        TimeoutException.class,
        "Faked TimeoutException"
      ),
      Arguments.of(
        Named.of(
          "Throwing an InterruptedException from CompletableFuture get()",
          null
        ),
        new InterruptedException("Faked InterruptedException"),
        FolioClientException.INTERRUPTED_ERROR,
        "Async call interrupted",
        InterruptedException.class,
        "Faked InterruptedException"
      ),
      Arguments.of(
        Named.of(
          "Throwing an RuntimeException from CompletableFuture get()",
          null
        ),
        new RuntimeException("This should do something"),
        FolioClientException.GENERIC_ERROR,
        "Unhandled error",
        RuntimeException.class,
        "This should do something"
      )
    );
  }

  @ParameterizedTest(name = "{0}") // Use the named first argument and ignore the rest for test case naming
  @MethodSource("testGet_HTTP_Failure_arguments")
  @DisplayName("When the HTTP call fails, should throw correct FolioClientException from async helper")
  void testGet_HTTP_Failure(
    Throwable httpAsyncException,
    Throwable completableFutureException,
    Long expectedTopCode,
    String expectedTopMessage,
    Class<? extends Exception> expectedCauseClass,
    String expectedCauseMessage
  ) throws ExecutionException, InterruptedException, TimeoutException { // Need to declare these exceptions as they are thrown by CompletableFuture::get
    if (completableFutureException != null) {
      // If we're testing a specific exception from CompletableFuture::get, then we need to mock CompleteableFuture,
      // and have it throw that exception when get() is called
      // Finally we ensure that this mocked future is what is returned from sendAsync
      CompletableFuture<TestResponse> failedFuture = mock(CompletableFuture.class);

      //doThrow(completableFutureException).when(failedFuture).get(any(Long.class), any(TimeUnit.class));
      when(failedFuture.get(any(Long.class), any(TimeUnit.class))).thenThrow(completableFutureException); // Mock the get() call to throw our exception

      CompletableFuture<HttpResponse<TestResponse>> asyncFailedFuture = mock(CompletableFuture.class);
      when(asyncFailedFuture.thenApply(any(Function.class))).thenReturn(failedFuture);

      // Client is SPY not MOCK, so we need to use doReturn when stubbing methods
      doReturn(asyncFailedFuture).when(client).getAsyncWithResponse(any(), any(), any(), any());
    } else {
      // If we're not doing a completable future exception, we just have sendAsync throw the desired exception directly
      // This SHOULD get wrapped by the exceptionally block into a FolioClientException
      when(mockHttpClient.sendAsync(
        any(HttpRequest.class),
        any(HttpResponse.BodyHandler.class)) // We're not checking that the BodyHandler works as expected here
      )
      .thenReturn(CompletableFuture.failedFuture(httpAsyncException));
    }


    // We expect the get call to throw a FolioClientException, having gone through the async helper
    FolioClientException ex = assertThrows(
      FolioClientException.class,
      () -> client.get(PATH, REQUEST_HEADERS, QUERY_PARAMS, TestResponse.class),
      "Expected get to throw FolioClientException"
    );

    // Verify the path taken from get -> sendAsync
    verify(client, times(1)).get(any(), any(), any(), any());
    verify(client, times(1)).getAsync(any(), any(), any(), any());
    verify(client, times(1)).getAsyncWithResponse(any(), any(), any(), any());
    verify(mockResponse, times(0)).body(); // Body does NOT get called because we threw an exception
    verify(client, times(1)).asyncFolioClientExceptionHelper(any());

    // We expect the error to be a GENERIC_ERROR, as it should have gotten to the last catch-all block in the async helper
    assertEquals(expectedTopCode, ex.code);
    assertEquals(expectedTopMessage, ex.getMessage());
    assertEquals(FolioClientException.class, ex.getClass());

    // Handle null cause cases as well
    if (expectedCauseClass == null) {
      assertNull(ex.getCause());
    } else {
      assertEquals(expectedCauseClass, ex.getCause().getClass());
      if (expectedCauseMessage != null) {
        assertEquals(expectedCauseMessage, ex.getCause().getMessage());
      }
    }
  }

  // TODO we need to test the rest of FolioClient as well, but from hereon in we can mock the async helper directly
}
