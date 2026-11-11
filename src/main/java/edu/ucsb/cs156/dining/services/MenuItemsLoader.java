package edu.ucsb.cs156.dining.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ucsb.cs156.dining.models.DiningCommons;
import edu.ucsb.cs156.dining.models.Entree;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Downloads every MenuItem for the current month from the UCSB Dining API and stores them in the
 * database. Intended to be called once at startup, so that users don't depend on someone having
 * visited a given day's menu for its items to exist.
 *
 * <p>Requests that get an HTTP 429 (Too Many Requests) are retried with exponential backoff
 * (honoring a {@code Retry-After} header when present). If the retries are exhausted, the whole
 * load stops rather than continuing to hammer the API. If the API key is not configured, no
 * requests are sent at all.
 */
@Slf4j
@Service
public class MenuItemsLoader {

  /** The default value of app.ucsb.api.consumer_key when UCSB_API_KEY has not been set. */
  public static final String PLACEHOLDER_API_KEY = "see-instructions-in-readme";

  public static final int MAX_ATTEMPTS = 5;
  public static final long BASE_BACKOFF_MS = 1000;
  public static final long MAX_BACKOFF_MS = 60_000;

  /** A call to the UCSB API that may throw. */
  @FunctionalInterface
  interface ApiCall<T> {
    T call() throws Exception;
  }

  /** Abstraction over Thread.sleep so tests don't really wait. */
  @FunctionalInterface
  interface Sleeper {
    void sleep(long millis) throws InterruptedException;
  }

  private final DiningCommonsService diningCommonsService;
  private final UCSBDiningMenuService menuService;
  private final UCSBDiningMenuItemsService menuItemsService;
  private final ObjectMapper objectMapper;
  private final String apiKey;

  Clock clock = Clock.system(ZoneId.of("America/Los_Angeles"));
  Sleeper sleeper = Thread::sleep;

  public MenuItemsLoader(
      DiningCommonsService diningCommonsService,
      UCSBDiningMenuService menuService,
      UCSBDiningMenuItemsService menuItemsService,
      ObjectMapper objectMapper,
      @Value("${app.ucsb.api.consumer_key}") String apiKey) {
    this.diningCommonsService = diningCommonsService;
    this.menuService = menuService;
    this.menuItemsService = menuItemsService;
    this.objectMapper = objectMapper;
    this.apiKey = apiKey;
  }

  boolean apiKeyMissing() {
    return apiKey == null || apiKey.isBlank() || PLACEHOLDER_API_KEY.equals(apiKey);
  }

  /**
   * Loads all MenuItems for every day of the current month, for every dining commons. Never throws:
   * problems are logged.
   */
  public void loadCurrentMonth() {
    if (apiKeyMissing()) {
      log.warn("UCSB API key is not set; skipping startup load of current month's menu items");
      return;
    }

    try {
      YearMonth month = YearMonth.now(clock);
      log.info("Loading menu items for {}", month);
      List<DiningCommons> commons = withRetry(diningCommonsService::get);
      for (int day = 1; day <= month.lengthOfMonth(); day++) {
        LocalDate date = month.atDay(day);
        for (DiningCommons dc : commons) {
          loadDay(date, dc.getCode());
        }
      }
      log.info("Finished loading menu items for {}", month);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Startup load of menu items was interrupted");
    } catch (Exception e) {
      log.error("Startup load of menu items stopped early: {}", e.getMessage());
    }
  }

  private void loadDay(LocalDate date, String commonsCode) throws Exception {
    String dateString = date.toString();
    String mealsJson;
    try {
      mealsJson = withRetry(() -> menuService.getJSON(dateString, commonsCode));
    } catch (HttpClientErrorException.TooManyRequests | InterruptedException e) {
      throw e;
    } catch (Exception e) {
      log.error("Could not get meals for {} on {}: {}", commonsCode, dateString, e.getMessage());
      return;
    }
    if (mealsJson == null || mealsJson.isBlank()) {
      return; // commons is closed that day
    }

    JsonNode meals;
    try {
      meals = objectMapper.readTree(mealsJson);
    } catch (Exception e) {
      log.error("Could not parse meals for {} on {}: {}", commonsCode, dateString, e.getMessage());
      return;
    }
    for (JsonNode meal : meals) {
      loadMeal(dateString, commonsCode, meal.path("code").asText());
    }
  }

  private void loadMeal(String dateString, String commonsCode, String mealCode) throws Exception {
    try {
      List<Entree> entrees =
          withRetry(() -> menuItemsService.get(dateString, commonsCode, mealCode));
      menuItemsService.getOrCreateMenuItems(commonsCode, mealCode, entrees);
    } catch (HttpClientErrorException.TooManyRequests | InterruptedException e) {
      throw e;
    } catch (Exception e) {
      log.error(
          "Could not load items for {} {} on {}: {}",
          commonsCode,
          mealCode,
          dateString,
          e.getMessage());
    }
  }

  /** Runs the call, retrying with backoff on HTTP 429 up to MAX_ATTEMPTS total attempts. */
  <T> T withRetry(ApiCall<T> call) throws Exception {
    for (int attempt = 1; ; attempt++) {
      try {
        return call.call();
      } catch (HttpClientErrorException.TooManyRequests e) {
        if (attempt >= MAX_ATTEMPTS) {
          throw e;
        }
        long delay = backoffMillis(attempt, e);
        log.warn("Rate limited by UCSB API (attempt {}); waiting {} ms", attempt, delay);
        sleeper.sleep(delay);
      }
    }
  }

  static long backoffMillis(int attempt, HttpClientErrorException e) {
    long millis = BASE_BACKOFF_MS << (attempt - 1);
    HttpHeaders headers = e.getResponseHeaders();
    String retryAfter = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
    if (retryAfter != null) {
      try {
        millis = Long.parseLong(retryAfter.trim()) * 1000;
      } catch (NumberFormatException ignored) {
        // not a number of seconds (could be an HTTP date); use exponential backoff instead
      }
    }
    return Math.min(millis, MAX_BACKOFF_MS);
  }
}
