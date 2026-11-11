package edu.ucsb.cs156.dining.startup;

import static org.mockito.Mockito.*;

import edu.ucsb.cs156.dining.entities.Admin;
import edu.ucsb.cs156.dining.repositories.AdminRepository;
import edu.ucsb.cs156.dining.services.MenuItemsLoader;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

class DiningStartupTests {

  @Mock private AdminRepository adminRepository;
  @Mock private MenuItemsLoader menuItemsLoader;

  private DiningStartup diningStartup;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    diningStartup = new DiningStartup();
    diningStartup.adminRepository = adminRepository;
    diningStartup.menuItemsLoader = menuItemsLoader;
    diningStartup.adminEmails = List.of("admin1@ucsb.edu", "admin2@ucsb.edu");
  }

  @Test
  void test_AlwaysRunOnStartup_saves_admins() {
    when(adminRepository.existsByEmail(anyString())).thenReturn(false);

    diningStartup.alwaysRunOnStartup();

    verify(adminRepository).save(new Admin("admin1@ucsb.edu"));
    verify(adminRepository).save(new Admin("admin2@ucsb.edu"));
  }

  @Test
  void test_AlwaysRunOnStartup_skips_existing_admins() {
    when(adminRepository.existsByEmail("admin1@ucsb.edu")).thenReturn(true);
    when(adminRepository.existsByEmail("admin2@ucsb.edu")).thenReturn(false);

    diningStartup.alwaysRunOnStartup();

    verify(adminRepository, never()).save(new Admin("admin1@ucsb.edu"));
    verify(adminRepository).save(new Admin("admin2@ucsb.edu"));
  }

  @Test
  void test_AlwaysRunOnStartup_handles_exception() {
    when(adminRepository.existsByEmail(anyString())).thenReturn(false);
    doThrow(new RuntimeException("Simulated error")).when(adminRepository).save(any(Admin.class));

    diningStartup.alwaysRunOnStartup();

    // Both emails are attempted despite the exception on the first one
    verify(adminRepository, times(2)).save(any(Admin.class));
  }

  @Test
  void test_AlwaysRunOnStartup_starts_menu_item_load() {
    diningStartup.alwaysRunOnStartup();

    // the load runs on a background thread, so allow it a moment to be invoked
    verify(menuItemsLoader, timeout(5000)).loadCurrentMonth();
  }
}
