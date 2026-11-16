package edu.ucsb.cs156.dining.models;

import edu.ucsb.cs156.dining.entities.MenuItem;

/**
 * Lightweight search result for a menu item, used by the menu item search endpoint.
 *
 * <p>Deliberately excludes the reviews of the menu item, so that unapproved reviews are never
 * exposed through search.
 */
public record MenuItemSearchResult(long id, String name, String diningCommonsCode) {

  public MenuItemSearchResult(MenuItem menuItem) {
    this(menuItem.getId(), menuItem.getName(), menuItem.getDiningCommonsCode());
  }
}
