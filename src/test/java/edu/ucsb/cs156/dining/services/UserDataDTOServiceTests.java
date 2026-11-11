package edu.ucsb.cs156.dining.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ucsb.cs156.dining.entities.User;
import edu.ucsb.cs156.dining.models.UserDTO;
import edu.ucsb.cs156.dining.repositories.AdminRepository;
import edu.ucsb.cs156.dining.repositories.ModeratorRepository;
import edu.ucsb.cs156.dining.repositories.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class UserDataDTOServiceTests {
  @Mock UserRepository userRepository;
  @Mock AdminRepository adminRepository;
  @Mock ModeratorRepository moderatorRepository;

  // reads a boolean flag from the DTO's JSON, so it doesn't depend on accessor names
  private static boolean flag(UserDTO dto, String name) {
    return new ObjectMapper().findAndRegisterModules().valueToTree(dto).get(name).asBoolean();
  }

  @Test
  void returns_page_of_user_dtos() {
    User u1 = User.builder().id(1L).email("a@ucsb.edu").build();
    User u2 = User.builder().id(2L).email("b@ucsb.edu").build();
    Pageable pageable = PageRequest.of(0, 2, Sort.by("id"));

    when(userRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(u1, u2), pageable, 5));
    when(adminRepository.existsByEmail("a@ucsb.edu")).thenReturn(true);
    when(adminRepository.existsByEmail("b@ucsb.edu")).thenReturn(false);
    when(moderatorRepository.existsByEmail("a@ucsb.edu")).thenReturn(false);
    when(moderatorRepository.existsByEmail("b@ucsb.edu")).thenReturn(true);

    UserDataDTOService service =
        new UserDataDTOService(
            userRepository, adminRepository, moderatorRepository, List.of("root@ucsb.edu"));

    Page<UserDTO> result = service.getUserDataDTOs(pageable);

    assertEquals(2, result.getContent().size());
    assertEquals(5, result.getTotalElements());

    UserDTO first = result.getContent().get(0);
    UserDTO second = result.getContent().get(1);
    assertEquals(true, flag(first, "admin"));
    assertEquals(false, flag(first, "moderator"));
    assertEquals(false, flag(second, "admin"));
    assertEquals(true, flag(second, "moderator"));
  }

  @Test
  void user_in_admin_emails_is_admin_without_checking_the_admin_repository() {
    User u1 = User.builder().id(1L).email("root@ucsb.edu").build();
    Pageable pageable = PageRequest.of(0, 1, Sort.by("id"));

    when(userRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(u1), pageable, 1));
    when(moderatorRepository.existsByEmail("root@ucsb.edu")).thenReturn(false);

    UserDataDTOService service =
        new UserDataDTOService(
            userRepository, adminRepository, moderatorRepository, List.of("root@ucsb.edu"));

    Page<UserDTO> result = service.getUserDataDTOs(pageable);

    assertEquals(1, result.getContent().size());
    assertEquals(true, flag(result.getContent().get(0), "admin"));
    assertEquals(false, flag(result.getContent().get(0), "moderator"));
    // the email is in app.admin.emails, so the database check is skipped
    verify(adminRepository, never()).existsByEmail("root@ucsb.edu");
  }

  @Test
  void plain_user_is_neither_admin_nor_moderator() {
    User u1 = User.builder().id(1L).email("plain@ucsb.edu").build();
    Pageable pageable = PageRequest.of(0, 1, Sort.by("id"));

    when(userRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(u1), pageable, 1));
    when(adminRepository.existsByEmail("plain@ucsb.edu")).thenReturn(false);
    when(moderatorRepository.existsByEmail("plain@ucsb.edu")).thenReturn(false);

    UserDataDTOService service =
        new UserDataDTOService(
            userRepository, adminRepository, moderatorRepository, List.of("root@ucsb.edu"));

    UserDTO dto = service.getUserDataDTOs(pageable).getContent().get(0);

    assertEquals(false, flag(dto, "admin"));
    assertEquals(false, flag(dto, "moderator"));
  }
}
