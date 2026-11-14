package edu.ucsb.cs156.dining.startup;

import edu.ucsb.cs156.dining.entities.Admin;
import edu.ucsb.cs156.dining.repositories.AdminRepository;
import edu.ucsb.cs156.dining.services.MenuItemsLoader;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** This class contains a method that is called once at application startup time. */
@Slf4j
@Component
public class DiningStartup {

  @Value("#{'${app.admin.emails}'.split(',')}")
  List<String> adminEmails;

  @Autowired AdminRepository adminRepository;

  @Autowired MenuItemsLoader menuItemsLoader;

  /**
   * Called once at application startup time. Loads all emails in ADMIN_EMAILS into the Admin table,
   * then starts loading the current month's menu items in the background.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void alwaysRunOnStartup() {
    log.info("alwaysRunOnStartup called");

    adminEmails.forEach(
        (email) -> {
          try {
            if (!adminRepository.existsByEmail(email)) {
              Admin admin = new Admin(email);
              adminRepository.save(admin);
            }
          } catch (Exception e) {
            log.error("Error saving admin email {}: {}", email, e.getMessage());
          }
        });

    // Runs in the background so a slow or rate-limited API never delays startup.
    CompletableFuture.runAsync(menuItemsLoader::loadCurrentMonth);
  }
}
