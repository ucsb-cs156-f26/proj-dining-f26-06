package edu.ucsb.cs156.dining.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.ucsb.cs156.dining.entities.MenuItem;
import edu.ucsb.cs156.dining.services.wiremock.WiremockService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
public class MenuItemRepositoryTests {

  @Autowired private MenuItemRepository menuItemRepository;

  @MockitoBean private WiremockService wmService;

  private MenuItem save(String commons, String meal, String name, String station) {
    return menuItemRepository.save(
        MenuItem.builder()
            .diningCommonsCode(commons)
            .mealCode(meal)
            .name(name)
            .station(station)
            .build());
  }

  @Test
  public void search_matches_ignoring_case() {
    MenuItem pizza = save("portola", "dinner", "Pizza", "Pizza Station");
    save("portola", "dinner", "Waffles", "Grill");

    List<MenuItem> upper =
        menuItemRepository.findTop20ByNameContainingIgnoreCaseOrderByNameAscIdAsc("PIZZA");
    List<MenuItem> lower =
        menuItemRepository.findTop20ByNameContainingIgnoreCaseOrderByNameAscIdAsc("pizza");

    assertEquals(List.of(pizza), upper);
    assertEquals(List.of(pizza), lower);
  }

  @Test
  public void search_matches_anywhere_in_the_name() {
    MenuItem pepperoni = save("portola", "dinner", "Pepperoni Pizza", "Pizza Station");
    save("portola", "dinner", "Waffles", "Grill");

    List<MenuItem> result =
        menuItemRepository.findTop20ByNameContainingIgnoreCaseOrderByNameAscIdAsc("pper");

    assertEquals(List.of(pepperoni), result);
  }

  @Test
  public void search_returns_empty_list_when_nothing_matches() {
    save("portola", "dinner", "Pizza", "Pizza Station");

    List<MenuItem> result =
        menuItemRepository.findTop20ByNameContainingIgnoreCaseOrderByNameAscIdAsc("sushi");

    assertTrue(result.isEmpty());
  }

  @Test
  public void search_does_not_dedupe_items_served_at_several_meals_or_commons() {
    MenuItem a = save("portola", "lunch", "Pizza", "Pizza Station");
    MenuItem b = save("portola", "dinner", "Pizza", "Pizza Station");
    MenuItem c = save("carrillo", "lunch", "Pizza", "Grill");

    List<MenuItem> result =
        menuItemRepository.findTop20ByNameContainingIgnoreCaseOrderByNameAscIdAsc("pizza");

    assertEquals(List.of(a, b, c), result);
  }

  @Test
  public void search_orders_by_name_then_id() {
    MenuItem zebra = save("portola", "dinner", "Zebra Pizza", "Grill");
    MenuItem pizzaLowerId = save("carrillo", "lunch", "Pizza", "Grill");
    MenuItem pizzaHigherId = save("portola", "lunch", "Pizza", "Grill");
    assertTrue(pizzaLowerId.getId() < pizzaHigherId.getId());

    List<MenuItem> result =
        menuItemRepository.findTop20ByNameContainingIgnoreCaseOrderByNameAscIdAsc("pizza");

    assertEquals(List.of(pizzaLowerId, pizzaHigherId, zebra), result);
  }

  @Test
  public void search_returns_at_most_20_results() {
    for (int i = 1; i <= 21; i++) {
      save("portola", "dinner", String.format("Pizza %02d", i), "Pizza Station");
    }

    List<MenuItem> result =
        menuItemRepository.findTop20ByNameContainingIgnoreCaseOrderByNameAscIdAsc("pizza");

    assertEquals(20, result.size());
    assertEquals("Pizza 01", result.get(0).getName());
    assertEquals("Pizza 20", result.get(19).getName());
  }
}
