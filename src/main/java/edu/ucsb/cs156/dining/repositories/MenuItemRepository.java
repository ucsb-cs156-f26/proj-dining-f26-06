package edu.ucsb.cs156.dining.repositories;

import edu.ucsb.cs156.dining.entities.MenuItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MenuItemRepository
    extends CrudRepository<MenuItem, Long>, CustomMenuItemRepository {
  /**
   * This method returns a MenuItem entity with a given id.
   *
   * @param diningCommonsCode of menu item
   * @param mealCode of menu item
   * @param name of menu item
   * @param station of menu item
   * @return Optional of Menu item based on the parameters (empty if not found)
   */
  Optional<MenuItem> findByDiningCommonsCodeAndMealCodeAndNameAndStation(
      String diningCommonsCode, String mealCode, String name, String station);

  boolean existsById(Long id);

  /**
   * This method returns up to 20 menu items whose name contains the given text, ignoring case,
   * ordered by name (then id, so that the order is stable).
   *
   * @param name text to look for anywhere in the name of the menu item
   * @return up to 20 matching menu items (empty if none match)
   */
  List<MenuItem> findTop20ByNameContainingIgnoreCaseOrderByNameAscIdAsc(String name);
}
