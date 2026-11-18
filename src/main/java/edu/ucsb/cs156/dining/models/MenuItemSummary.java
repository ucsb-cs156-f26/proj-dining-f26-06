package edu.ucsb.cs156.dining.models;

import edu.ucsb.cs156.dining.entities.MenuItem;

/**
 * Summary of a single menu item, used by the get-menu-item-by-id endpoint.
 *
 * <p>Deliberately excludes the reviews of the menu item, so that unapproved reviews and reviewer
 * details are never exposed through this endpoint.
 */
public record MenuItemSummary(
    long id, String name, String diningCommonsCode, String mealCode, String station) {

  public MenuItemSummary(MenuItem menuItem) {
    this(
        menuItem.getId(),
        menuItem.getName(),
        menuItem.getDiningCommonsCode(),
        menuItem.getMealCode(),
        menuItem.getStation());
  }
}
