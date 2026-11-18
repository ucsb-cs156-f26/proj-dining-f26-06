package edu.ucsb.cs156.dining.web;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.options.AriaRole;
import edu.ucsb.cs156.dining.WebTestCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * End-to-end test that creates a review while the UCSB Dining API is stubbed by {@code
 * DiningApiWireMock}.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("integration")
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
public class ReviewWebIT extends WebTestCase {

  private static final LocatorAssertions.IsVisibleOptions VISIBLE =
      new LocatorAssertions.IsVisibleOptions().setTimeout(20_000);

  private static final LocatorAssertions.HasValueOptions HAS_VALUE =
      new LocatorAssertions.HasValueOptions().setTimeout(20_000);

  private static final LocatorAssertions.HasTextOptions HAS_TEXT =
      new LocatorAssertions.HasTextOptions().setTimeout(20_000);

  @Test
  public void logged_in_user_can_create_a_review() {
    setupUser(false);

    assertThat(page.getByText("Log Out")).isVisible(VISIBLE);
    assertThat(page.getByTestId("DiningCommonsTable-cell-row-0-col-meal-lunch")).isVisible(VISIBLE);

    page.getByTestId("DiningCommonsTable-cell-row-0-col-meal-lunch").click();

    assertThat(page.getByTestId("MenuItemTable-cell-row-0-col-Review Item-button"))
        .isVisible(VISIBLE);
    page.getByTestId("MenuItemTable-cell-row-0-col-Review Item-button").click();

    assertThat(page.locator("#review-item-name")).hasValue("Chicken Caesar Salad", HAS_VALUE);

    page.locator("#review-comments").fill("Crisp and fresh");
    page.locator("#review-stars").selectOption("4");
    page.locator("#review-date").fill("2026-11-04T12:00");
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Submit Review")).click();

    assertThat(page.getByText("Review submitted for Chicken Caesar Salad")).isVisible(VISIBLE);

    page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("My Reviews")).click();

    assertThat(page.getByTestId("Reviewstable-cell-row-0-col-item.name"))
        .hasText("Chicken Caesar Salad", HAS_TEXT);
    assertThat(page.getByTestId("Reviewstable-cell-row-0-col-reviewerComments"))
        .hasText("Crisp and fresh");
    assertThat(page.getByTestId("Reviewstable-cell-row-0-col-itemsStars")).hasText("⭐⭐⭐⭐");
    assertThat(page.getByTestId("Reviewstable-cell-row-0-col-status")).hasText("AWAITING_REVIEW");
    assertThat(page.getByTestId("Reviewstable-cell-row-0-col-item.diningCommonsCode"))
        .hasText("ortega");
  }
}
