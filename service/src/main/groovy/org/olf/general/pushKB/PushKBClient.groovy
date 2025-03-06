package org.olf.general.pushKB

import groovy.util.logging.Slf4j

// Allow us to parse as JsonObjects for now instead of fully typing all responses
import org.grails.web.json.JSONObject;

// Swapping to Micronaut's Http low level client builder (declarative would be a big shift, instead build requests up)
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.uri.UriBuilder



import grails.converters.JSON;

// Client to handle PUBLIC API calls to a pushKB
@Slf4j
class PushKBClient {
  private HttpClient client;

  public static String HEALTH_ENDPOINT = "/health"
  public static String TEMPORARY_PUSHTASK_ENDPOINT = "/public/temporarypushtask"

  public PushKBClient(String baseUrl) {
    this.client = HttpClient.create(baseUrl.toURL());
  }

  public HttpClient getClient() {
    return this.client;
  }

  public void health() {
    HttpRequest request = HttpRequest.GET(
        UriBuilder.of(HEALTH_ENDPOINT).build()
    )

    HttpResponse<String> resp = client.toBlocking().exchange(request, JSONObject)
    JSONObject json = resp.body()
  }

  // Validateable TemporaryPushTaskPostBody may be overkill, but is here to help us with the eventual move
  public void temporaryPushTask(TemporaryPushTaskPostBody body) {
    String jsonBody = new JSON(body).toString();
    HttpRequest request = HttpRequest.POST(
      UriBuilder.of(TEMPORARY_PUSHTASK_ENDPOINT).build(),
      jsonBody
    )

    try {
      HttpResponse<JSONObject> resp = client.toBlocking().exchange(request, JSONObject)
      JSONObject json = resp.body()

      log.info("PushKBClient::temporaryPushTask POST succeeded: ${json}")
    } catch (HttpClientResponseException hcre) {
      HttpResponse<JSONObject> errResp = hcre.getResponse();
      JSONObject json = errResp.body()
      log.error("PushKBClient::temporaryPushTask POST failed: ${json}")
    }
  }

  public void temporaryPushTask(String pushTaskId, String filterContext = null) {
    TemporaryPushTaskPostBody body = new TemporaryPushTaskPostBody([
        pushTaskId: pushTaskId,
        filterContext: filterContext
    ]);

    temporaryPushTask(body);
  }
}