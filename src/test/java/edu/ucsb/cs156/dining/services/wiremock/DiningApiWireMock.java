package edu.ucsb.cs156.dining.services.wiremock;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.junit.Stubbing;

/**
 * WireMock stand-in for the UCSB Dining API used by end-to-end tests.
 *
 * <p>This server is separate from the OAuth WireMock server on port 8090. The {@code integration}
 * profile points {@code app.ucsb.api.host} at {@link #BASE_URL}, so tests never need a real UCSB
 * API key. Call {@link #server()} to register extra stubs for a particular test.
 */
public final class DiningApiWireMock {

  /** Port for the dining API WireMock server. Must not be the OAuth port (8090). */
  public static final int PORT = 8091;

  /** Base URL that {@code app.ucsb.api.host} should use during integration tests. */
  public static final String BASE_URL = "http://localhost:" + PORT;

  private static WireMockServer server;

  private DiningApiWireMock() {}

  /**
   * Start the dining API WireMock server and install the basic stubs.
   *
   * <p>Does nothing if the server is already running.
   */
  public static synchronized void start() {
    if (server != null && server.isRunning()) {
      return;
    }
    server = new WireMockServer(options().port(PORT));
    stubBasicResponses(server);
    server.start();
  }

  /** Stop the dining API WireMock server if it is running. */
  public static synchronized void stop() {
    if (server != null) {
      server.stop();
      server = null;
    }
  }

  /** Replace every stub with the basic dining commons, meal, and menu item responses. */
  public static synchronized void resetToBasicStubs() {
    if (server == null) {
      throw new IllegalStateException("Dining API WireMock server is not running");
    }
    server.resetAll();
    stubBasicResponses(server);
  }

  /**
   * The running server, so a test can register additional stubs.
   *
   * @return the WireMock server, or null if {@link #start()} has not been called
   */
  public static WireMockServer server() {
    return server;
  }

  /**
   * Stub the UCSB dining endpoints needed to open a menu and create a review.
   *
   * <p>Responses cover Ortega only: the commons list, that commons' meals for any date, and the
   * lunch menu. Lunch is registered at a higher priority than the meal-times stub so a request for
   * {@code /ortega/lunch} is not served the meal list.
   *
   * @param stubbing the WireMock server or extension to stub
   */
  public static void stubBasicResponses(Stubbing stubbing) {
    stubbing.stubFor(
        get(urlPathEqualTo("/dining/commons/v1/")).willReturn(okJson(DINING_COMMONS_JSON)));
    stubbing.stubFor(
        get(urlPathEqualTo("/dining/commons/v1")).willReturn(okJson(DINING_COMMONS_JSON)));

    stubbing.stubFor(
        get(urlPathMatching("/dining/menu/v1/[^/]+/ortega"))
            .atPriority(2)
            .willReturn(okJson(MEALS_JSON)));

    stubbing.stubFor(
        get(urlPathMatching("/dining/menu/v1/[^/]+/ortega/lunch"))
            .atPriority(1)
            .willReturn(okJson(LUNCH_ITEMS_JSON)));
  }

  static final String DINING_COMMONS_JSON =
      """
      [
        {
          "name": "Ortega",
          "code": "ortega",
          "hasDiningCam": true,
          "hasSackMeal": true,
          "hasTakeOutMeal": true,
          "location": {
            "latitude": 34.410987,
            "longitude": -119.84709
          }
        }
      ]
      """;

  static final String MEALS_JSON =
      """
      [
        {"name": "Breakfast", "code": "breakfast"},
        {"name": "Lunch", "code": "lunch"},
        {"name": "Dinner", "code": "dinner"}
      ]
      """;

  static final String LUNCH_ITEMS_JSON =
      """
      [
        {"name": "Chicken Caesar Salad", "station": "Entrees"}
      ]
      """;
}
