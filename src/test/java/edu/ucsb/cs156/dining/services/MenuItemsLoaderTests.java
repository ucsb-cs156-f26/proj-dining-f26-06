package edu.ucsb.cs156.dining.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ucsb.cs156.dining.models.DiningCommons;
import edu.ucsb.cs156.dining.models.Entree;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

class MenuItemsLoaderTests {

  private DiningCommonsService commonsService;
  private UCSBDiningMenuService menuService;
  private UCSBDiningMenuItemsService itemsService;
  private List<Long> sleeps;

  @BeforeEach
  void setUp() {
    commonsService = mock(DiningCommonsService.class);
    menuService = mock(UCSBDiningMenuService.class);
    itemsService = mock(UCSBDiningMenuItemsService.class);
    sleeps = new ArrayList<>();
  }

  /** A loader whose clock is fixed in February 2025 (28 days) and which records sleeps. */
  private MenuItemsLoader loader(String apiKey) {
    MenuItemsLoader loader =
        new MenuItemsLoader(commonsService, menuService, itemsService, new ObjectMapper(), apiKey);
    loader.clock =
        Clock.fixed(Instant.parse("2025-02-10T20:00:00Z"), ZoneId.of("America/Los_Angeles"));
    loader.sleeper = sleeps::add;
    return loader;
  }

  private static HttpClientErrorException tooMany(String retryAfter) {
    HttpHeaders headers = new HttpHeaders();
    if (retryAfter != null) {
      headers.set(HttpHeaders.RETRY_AFTER, retryAfter);
    }
    return HttpClientErrorException.create(
        HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", headers, new byte[0], null);
  }

  private static DiningCommons commons(String code) {
    return DiningCommons.builder().code(code).name(code).build();
  }

  // ---- API key handling ----

  @Test
  void blank_null_and_placeholder_keys_send_no_requests() {
    for (String key : new String[] {null, "", "   ", MenuItemsLoader.PLACEHOLDER_API_KEY}) {
      loader(key).loadCurrentMonth();
    }
    verifyNoInteractions(commonsService, menuService, itemsService);
  }

  @Test
  void real_key_is_not_missing() {
    assertFalse(loader("abc123").apiKeyMissing());
  }

  // ---- loading ----

  @Test
  void loads_every_day_of_the_month_for_every_commons_and_meal() throws Exception {
    when(commonsService.get()).thenReturn(List.of(commons("ortega"), commons("carrillo")));
    when(menuService.getJSON(anyString(), anyString()))
        .thenReturn(
            "[{\"name\":\"Lunch\",\"code\":\"lunch\"},{\"name\":\"Dinner\",\"code\":\"dinner\"}]");
    List<Entree> entrees = List.of(new Entree("Chicken", "Grill"));
    when(itemsService.get(anyString(), anyString(), anyString())).thenReturn(entrees);

    loader("abc123").loadCurrentMonth();

    verify(menuService, times(28 * 2)).getJSON(anyString(), anyString());
    verify(menuService).getJSON("2025-02-01", "ortega");
    verify(menuService).getJSON("2025-02-28", "carrillo");
    verify(menuService, never()).getJSON("2025-03-01", "ortega");
    verify(menuService, never()).getJSON("2025-01-31", "ortega");
    verify(itemsService, times(28 * 2 * 2)).get(anyString(), anyString(), anyString());
    verify(itemsService).get("2025-02-15", "ortega", "dinner");
    verify(itemsService, times(28)).getOrCreateMenuItems("ortega", "lunch", entrees);
    verify(itemsService, times(28)).getOrCreateMenuItems("carrillo", "dinner", entrees);
    assertTrue(sleeps.isEmpty());
  }

  @Test
  void closed_days_and_empty_meal_lists_are_skipped() throws Exception {
    when(commonsService.get()).thenReturn(List.of(commons("ortega")));
    when(menuService.getJSON("2025-02-01", "ortega")).thenReturn(null);
    when(menuService.getJSON("2025-02-02", "ortega")).thenReturn("  ");
    when(menuService.getJSON("2025-02-03", "ortega")).thenReturn("[]");

    loader("abc123").loadCurrentMonth();

    verify(menuService, times(28)).getJSON(anyString(), anyString());
    verify(itemsService, never()).get(anyString(), anyString(), anyString());
  }

  @Test
  void bad_meals_json_for_one_day_does_not_stop_the_load() throws Exception {
    when(commonsService.get()).thenReturn(List.of(commons("ortega")));
    when(menuService.getJSON(anyString(), anyString())).thenReturn("not json{");

    loader("abc123").loadCurrentMonth();

    verify(menuService, times(28)).getJSON(anyString(), anyString());
    verify(itemsService, never()).get(anyString(), anyString(), anyString());
  }

  @Test
  void error_getting_meals_for_one_day_does_not_stop_the_load() throws Exception {
    when(commonsService.get()).thenReturn(List.of(commons("ortega")));
    when(menuService.getJSON(anyString(), anyString())).thenThrow(new RuntimeException("boom"));

    loader("abc123").loadCurrentMonth();

    verify(menuService, times(28)).getJSON(anyString(), anyString());
  }

  @Test
  void error_getting_items_for_one_meal_does_not_stop_the_load() throws Exception {
    when(commonsService.get()).thenReturn(List.of(commons("ortega")));
    when(menuService.getJSON(anyString(), anyString())).thenReturn("[{\"code\":\"lunch\"}]");
    when(itemsService.get(anyString(), anyString(), anyString()))
        .thenThrow(new RuntimeException("boom"));

    loader("abc123").loadCurrentMonth();

    verify(itemsService, times(28)).get(anyString(), anyString(), anyString());
    verify(itemsService, never()).getOrCreateMenuItems(anyString(), anyString(), any());
  }

  @Test
  void error_getting_commons_is_logged_not_thrown() throws Exception {
    when(commonsService.get()).thenThrow(new RuntimeException("boom"));

    loader("abc123").loadCurrentMonth();

    verifyNoInteractions(menuService, itemsService);
  }

  // ---- rate limiting ----

  @Test
  void retries_after_429_with_exponential_backoff_then_succeeds() throws Exception {
    when(commonsService.get())
        .thenThrow(tooMany(null))
        .thenThrow(tooMany(null))
        .thenThrow(tooMany(null))
        .thenReturn(List.of());

    loader("abc123").loadCurrentMonth();

    verify(commonsService, times(4)).get();
    assertEquals(List.of(1000L, 2000L, 4000L), sleeps);
  }

  @Test
  void gives_up_after_max_attempts_and_stops_the_whole_load() throws Exception {
    when(commonsService.get()).thenReturn(List.of(commons("ortega")));
    when(menuService.getJSON(anyString(), anyString())).thenThrow(tooMany(null));

    loader("abc123").loadCurrentMonth();

    // only the first day was attempted: 5 attempts, 4 sleeps, then the load stops
    verify(menuService, times(MenuItemsLoader.MAX_ATTEMPTS)).getJSON("2025-02-01", "ortega");
    verify(menuService, never()).getJSON("2025-02-02", "ortega");
    assertEquals(List.of(1000L, 2000L, 4000L, 8000L), sleeps);
  }

  @Test
  void rate_limit_while_loading_items_stops_the_whole_load() throws Exception {
    when(commonsService.get()).thenReturn(List.of(commons("ortega")));
    when(menuService.getJSON(anyString(), anyString())).thenReturn("[{\"code\":\"lunch\"}]");
    when(itemsService.get(anyString(), anyString(), anyString())).thenThrow(tooMany(null));

    loader("abc123").loadCurrentMonth();

    verify(itemsService, times(MenuItemsLoader.MAX_ATTEMPTS)).get("2025-02-01", "ortega", "lunch");
    verify(menuService, times(1)).getJSON(anyString(), anyString());
  }

  @Test
  void interrupt_while_backing_off_stops_the_load_and_keeps_interrupt_flag() throws Exception {
    when(commonsService.get()).thenThrow(tooMany(null));
    MenuItemsLoader loader = loader("abc123");
    loader.sleeper =
        millis -> {
          throw new InterruptedException();
        };

    try {
      loader.loadCurrentMonth();
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted(); // clear the flag so it doesn't leak into other tests
    }
    verify(commonsService, times(1)).get();
  }

  @Test
  void interrupt_while_backing_off_during_day_load_is_propagated() throws Exception {
    when(commonsService.get()).thenReturn(List.of(commons("ortega")));
    when(menuService.getJSON(anyString(), anyString())).thenThrow(tooMany(null));
    MenuItemsLoader loader = loader("abc123");
    loader.sleeper =
        millis -> {
          throw new InterruptedException();
        };

    try {
      loader.loadCurrentMonth();
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
    verify(menuService, times(1)).getJSON(anyString(), anyString());
  }

  @Test
  void interrupt_while_backing_off_during_item_load_is_propagated() throws Exception {
    when(commonsService.get()).thenReturn(List.of(commons("ortega")));
    when(menuService.getJSON(anyString(), anyString())).thenReturn("[{\"code\":\"lunch\"}]");
    when(itemsService.get(anyString(), anyString(), anyString())).thenThrow(tooMany(null));
    MenuItemsLoader loader = loader("abc123");
    loader.sleeper =
        millis -> {
          throw new InterruptedException();
        };

    try {
      loader.loadCurrentMonth();
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
    verify(itemsService, times(1)).get(anyString(), anyString(), anyString());
  }

  @Test
  void withRetry_rethrows_the_original_exception_after_max_attempts() {
    MenuItemsLoader loader = loader("abc123");
    HttpClientErrorException ex = tooMany(null);
    int[] calls = {0};

    HttpClientErrorException thrown =
        assertThrows(
            HttpClientErrorException.class,
            () ->
                loader.withRetry(
                    () -> {
                      calls[0]++;
                      throw ex;
                    }));

    assertSame(ex, thrown);
    assertEquals(MenuItemsLoader.MAX_ATTEMPTS, calls[0]);
  }

  @Test
  void withRetry_does_not_retry_other_errors() {
    MenuItemsLoader loader = loader("abc123");
    int[] calls = {0};

    assertThrows(
        IllegalStateException.class,
        () ->
            loader.withRetry(
                () -> {
                  calls[0]++;
                  throw new IllegalStateException("nope");
                }));

    assertEquals(1, calls[0]);
    assertTrue(sleeps.isEmpty());
  }

  // ---- backoff computation ----

  @Test
  void backoff_is_exponential_without_retry_after() {
    assertEquals(1000, MenuItemsLoader.backoffMillis(1, tooMany(null)));
    assertEquals(2000, MenuItemsLoader.backoffMillis(2, tooMany(null)));
    assertEquals(8000, MenuItemsLoader.backoffMillis(4, tooMany(null)));
  }

  @Test
  void backoff_is_capped() {
    assertEquals(MenuItemsLoader.MAX_BACKOFF_MS, MenuItemsLoader.backoffMillis(10, tooMany(null)));
  }

  @Test
  void backoff_honors_retry_after_seconds() {
    assertEquals(7000, MenuItemsLoader.backoffMillis(1, tooMany(" 7 ")));
  }

  @Test
  void backoff_caps_a_huge_retry_after() {
    assertEquals(MenuItemsLoader.MAX_BACKOFF_MS, MenuItemsLoader.backoffMillis(1, tooMany("9999")));
  }

  @Test
  void backoff_ignores_a_non_numeric_retry_after() {
    assertEquals(2000, MenuItemsLoader.backoffMillis(2, tooMany("Wed, 21 Oct 2026 07:28:00 GMT")));
  }

  @Test
  void backoff_handles_missing_headers() {
    HttpClientErrorException noHeaders =
        HttpClientErrorException.create(
            HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", null, new byte[0], null);
    assertEquals(1000, MenuItemsLoader.backoffMillis(1, noHeaders));
  }
}
