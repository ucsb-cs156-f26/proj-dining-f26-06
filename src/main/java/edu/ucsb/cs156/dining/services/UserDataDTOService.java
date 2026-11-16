package edu.ucsb.cs156.dining.services;

import edu.ucsb.cs156.dining.entities.User;
import edu.ucsb.cs156.dining.models.UserDTO;
import edu.ucsb.cs156.dining.repositories.AdminRepository;
import edu.ucsb.cs156.dining.repositories.ModeratorRepository;
import edu.ucsb.cs156.dining.repositories.UserRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/** Builds pages of {@link UserDTO} objects, including each user's admin/moderator status. */
@Service
public class UserDataDTOService {
  private final UserRepository userRepository;
  private final AdminRepository adminRepository;
  private final ModeratorRepository moderatorRepository;
  private final List<String> adminEmails;

  @Autowired
  public UserDataDTOService(
      UserRepository userRepository,
      AdminRepository adminRepository,
      ModeratorRepository moderatorRepository,
      @Value("#{'${app.admin.emails}'.split(',')}") List<String> adminEmails) {
    this.userRepository = userRepository;
    this.adminRepository = adminRepository;
    this.moderatorRepository = moderatorRepository;
    this.adminEmails = adminEmails;
  }

  /**
   * Returns one page of users with admin/moderator flags.
   *
   * @param pageable page number, page size, and sort
   * @return a page of UserDTO
   */
  public Page<UserDTO> getUserDataDTOs(Pageable pageable) {
    Page<User> users = userRepository.findAll(pageable);

    List<UserDTO> userDTOs = new ArrayList<>();
    for (User user : users) {
      String email = user.getEmail();
      boolean isAdmin = adminEmails.contains(email) || adminRepository.existsByEmail(email);
      boolean isModerator = moderatorRepository.existsByEmail(email);
      userDTOs.add(new UserDTO(user, isAdmin, isModerator));
    }
    return new PageImpl<>(userDTOs, pageable, users.getTotalElements());
  }
}
