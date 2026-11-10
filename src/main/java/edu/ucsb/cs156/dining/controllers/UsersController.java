package edu.ucsb.cs156.dining.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ucsb.cs156.dining.entities.User;
import edu.ucsb.cs156.dining.errors.EntityNotFoundException;
import edu.ucsb.cs156.dining.models.CurrentUser;
import edu.ucsb.cs156.dining.models.UserDTO;
import edu.ucsb.cs156.dining.repositories.AdminRepository;
import edu.ucsb.cs156.dining.repositories.ModeratorRepository;
import edu.ucsb.cs156.dining.repositories.UserRepository;
import edu.ucsb.cs156.dining.services.UserDataDTOService;
import edu.ucsb.cs156.dining.statuses.ModerationStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * This is a REST controller for getting information about the users.
 *
 * <p>These endpoints are only accessible to users with the role "ROLE_ADMIN".
 */
@Tag(name = "User information (admin only)")
@RequestMapping("/api")
@RestController
public class UsersController extends ApiController {

  @Value("#{'${app.admin.emails}'.split(',')}")
  private final List<String> adminEmails = new ArrayList<>();

  @Autowired UserRepository userRepository;

  @Autowired AdminRepository adminRepository;

  @Autowired ModeratorRepository moderatorRepository;

  @Autowired ObjectMapper mapper;

  @Autowired UserDataDTOService userDataDTOService;

  /**
   * Original endpoint: returns ALL users as a plain array (with admin/moderator flags). Kept so the
   * existing frontend keeps working until it is migrated to /paged.
   *
   * @return a list of all users
   * @throws JsonProcessingException if there is an error processing the JSON
   */
  @Operation(summary = "Get a list of all users")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  @GetMapping("/admin/users")
  public ResponseEntity<String> users() throws JsonProcessingException {
    Iterable<User> users = userRepository.findAll();
    List<UserDTO> userDTOs = new ArrayList<>();
    for (User user : users) {
      userDTOs.add(userDTO(user));
    }
    String body = mapper.writeValueAsString(userDTOs);
    return ResponseEntity.ok().body(body);
  }

  /**
   * Returns a page of users, always sorted by user id so rows never jump around.
   *
   * @param pageable page number and size (any sort in the request is ignored)
   * @return a page of users
   */
  @Operation(summary = "Get a paged list of all users")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  @GetMapping("/admin/users/paged")
  public Page<UserDTO> usersPaged(Pageable pageable) {
    Pageable sortedById =
        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("id"));
    return userDataDTOService.getUserDataDTOs(sortedById);
  }

  /**
   * This method returns list of all users with a proposed alias.
   *
   * @return a list of users with a proposed alias
   * @throws JsonProcessingException if there is an error processing the JSON
   */
  @Operation(summary = "Get a list of all users with a proposed alias")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  @GetMapping("/admin/usersWithProposedAlias")
  public ResponseEntity<String> getUsersWithProposedAlias() throws JsonProcessingException {
    Iterable<User> users = userRepository.findByProposedAliasNotNull();
    String body = mapper.writeValueAsString(users);
    return ResponseEntity.ok().body(body);
  }

  /** Get all users whose proposed alias is awaiting moderation. */
  @Operation(summary = "Get all aliases needing moderation")
  @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_MODERATOR')")
  @GetMapping("/admin/users/needsmoderation")
  public ResponseEntity<String> getAliasesNeedingModeration() throws JsonProcessingException {

    Iterable<User> users =
        userRepository.findByStatusAndProposedAliasNotNull(ModerationStatus.AWAITING_REVIEW);

    String body = mapper.writeValueAsString(users);

    return ResponseEntity.ok().body(body);
  }

  /**
   * This method allows the user to update their alias.
   *
   * @param proposedAlias the new alias
   * @return the updated user
   */
  @Operation(summary = "Update proposed alias of the current user")
  @PreAuthorize("hasRole('ROLE_USER')")
  @PostMapping("/currentUser/updateAlias")
  public ResponseEntity<User> updateProposedAlias(@RequestParam String proposedAlias) {
    CurrentUser currentUser = super.getCurrentUser();
    User user = currentUser.getUser();

    if (userRepository.findByAlias(proposedAlias).isPresent()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Alias already in use.");
    }

    user.setProposedAlias(proposedAlias);
    user.setStatus(ModerationStatus.AWAITING_REVIEW);
    User savedUser = userRepository.save(user);

    return ResponseEntity.ok(savedUser);
  }

  /**
   * This method allows an admin to update the moderation status of a user's alias.
   *
   * @param id the id of the user to update
   * @param approved the new moderation status
   * @return the updated user
   */
  @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_MODERATOR')")
  @PutMapping("/currentUser/updateAliasModeration")
  public User updateAliasModeration(@RequestParam long id, @RequestParam Boolean approved) {

    User user =
        userRepository.findById(id).orElseThrow(() -> new EntityNotFoundException(User.class, id));

    if (approved) {
      user.setAlias(user.getProposedAlias());
      user.setStatus(ModerationStatus.APPROVED);
      user.setDateApproved(LocalDate.now());
      user.setProposedAlias(null);
    } else {
      user.setStatus(ModerationStatus.REJECTED);
    }

    userRepository.save(user);

    return user;
  }

  private UserDTO userDTO(User user) {
    String email = user.getEmail();
    boolean isAdmin = adminEmails.contains(email) || adminRepository.existsByEmail(email);
    boolean isModerator = moderatorRepository.existsByEmail(email);
    return new UserDTO(user, isAdmin, isModerator);
  }
}
