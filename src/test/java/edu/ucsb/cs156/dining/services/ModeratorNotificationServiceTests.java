package edu.ucsb.cs156.dining.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import edu.ucsb.cs156.dining.entities.Moderator;
import edu.ucsb.cs156.dining.entities.Review;
import edu.ucsb.cs156.dining.repositories.ModeratorRepository;
import edu.ucsb.cs156.dining.services.wiremock.WiremockService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureDataJpa;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;

@RestClientTest(ModeratorNotificationService.class)
@AutoConfigureDataJpa
public class ModeratorNotificationServiceTests {

  private static final String PRINCIPAL_NAME = "123";
  private static final String TOKEN_VALUE = "fake-token-value";

  @Value("${app.name}")
  private String appName;

  @Value("${app.hostname}")
  private String appHostname;

  @Autowired private MockRestServiceServer mockRestServiceServer;

  @Autowired private ModeratorNotificationService moderatorNotificationService;

  @MockitoBean private OAuth2AuthorizedClientService authorizedClientService;

  @MockitoBean private ModeratorRepository moderatorRepository;

  @MockitoBean private WiremockService wiremockService;

  private final Review review = Review.builder().id(17L).build();

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  // ---------- helpers ----------

  private void logInWith(String registrationId) {
    OAuth2User principal =
        new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_USER")),
            Map.of("sub", PRINCIPAL_NAME, "email", "student@ucsb.edu"),
            "sub");
    OAuth2AuthenticationToken auth =
        new OAuth2AuthenticationToken(principal, principal.getAuthorities(), registrationId);
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  private OAuth2AuthorizedClient clientWithScopes(Set<String> scopes) {
    ClientRegistration registration =
        ClientRegistration.withRegistrationId("google")
            .clientId("client-id")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
            .tokenUri("https://oauth2.googleapis.com/token")
            .build();
    OAuth2AccessToken token =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            TOKEN_VALUE,
            Instant.now(),
            Instant.now().plusSeconds(3600),
            scopes);
    return new OAuth2AuthorizedClient(registration, PRINCIPAL_NAME, token);
  }

  private void logInWithGmailScope() {
    logInWith("google");
    doReturn(
            clientWithScopes(
                Set.of("email", "profile", ModeratorNotificationService.GMAIL_SEND_SCOPE)))
        .when(authorizedClientService)
        .loadAuthorizedClient("google", PRINCIPAL_NAME);
  }

  private String expectedRaw(String bcc) {
    String message =
        "Bcc: "
            + bcc
            + "\r\n"
            + "Subject: A new dining review needs moderation\r\n"
            + "Content-Type: text/plain; charset=UTF-8\r\n"
            + "\r\n"
            + "A new review (id 17) was submitted and is waiting for moderation.\r\n\r\n"
            + "Review it here: https://"
            + appName
            + "."
            + appHostname
            + "/moderate\r\n";
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(message.getBytes(StandardCharsets.UTF_8));
  }

  private void expectGmailSend(String bcc) {
    mockRestServiceServer
        .expect(requestTo(ModeratorNotificationService.GMAIL_SEND_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer " + TOKEN_VALUE))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.raw").value(expectedRaw(bcc)))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
  }

  // ---------- happy path ----------

  @Test
  void emails_all_moderators_with_bcc_and_moderate_link() {
    logInWithGmailScope();
    when(moderatorRepository.findAll())
        .thenReturn(List.of(new Moderator("mod1@ucsb.edu"), new Moderator("mod2@ucsb.edu")));
    expectGmailSend("mod1@ucsb.edu, mod2@ucsb.edu");

    moderatorNotificationService.notifyModerators(review);

    mockRestServiceServer.verify();
  }

  // ---------- no usable token: nothing is sent ----------

  @Test
  void does_nothing_when_no_one_is_logged_in() {
    moderatorNotificationService.notifyModerators(review);

    verify(authorizedClientService, never()).loadAuthorizedClient(anyString(), anyString());
    verify(moderatorRepository, never()).findAll();
    mockRestServiceServer.verify();
  }

  @Test
  void does_nothing_for_a_non_google_login() {
    logInWith("my-oauth-provider");

    moderatorNotificationService.notifyModerators(review);

    verify(authorizedClientService, never()).loadAuthorizedClient(anyString(), anyString());
    verify(moderatorRepository, never()).findAll();
    mockRestServiceServer.verify();
  }

  @Test
  void does_nothing_when_there_is_no_authorized_client() {
    logInWith("google");
    doReturn(null).when(authorizedClientService).loadAuthorizedClient("google", PRINCIPAL_NAME);

    moderatorNotificationService.notifyModerators(review);

    verify(moderatorRepository, never()).findAll();
    mockRestServiceServer.verify();
  }

  @Test
  void does_nothing_when_token_lacks_gmail_scope() {
    logInWith("google");
    doReturn(clientWithScopes(Set.of("email", "profile")))
        .when(authorizedClientService)
        .loadAuthorizedClient("google", PRINCIPAL_NAME);

    moderatorNotificationService.notifyModerators(review);

    verify(moderatorRepository, never()).findAll();
    mockRestServiceServer.verify();
  }

  // ---------- recipients ----------

  @Test
  void does_nothing_when_there_are_no_moderators() {
    logInWithGmailScope();
    when(moderatorRepository.findAll()).thenReturn(List.of());

    moderatorNotificationService.notifyModerators(review);

    mockRestServiceServer.verify();
  }

  @Test
  void skips_unsafe_moderator_emails() {
    logInWithGmailScope();
    when(moderatorRepository.findAll())
        .thenReturn(
            List.of(
                new Moderator((String) null),
                new Moderator(""),
                new Moderator("   "),
                new Moderator("\rinjected@ucsb.edu"),
                new Moderator("\ninjected@ucsb.edu"),
                new Moderator("good@ucsb.edu")));
    expectGmailSend("good@ucsb.edu");

    moderatorNotificationService.notifyModerators(review);

    mockRestServiceServer.verify();
  }

  @Test
  void does_nothing_when_all_moderator_emails_are_unsafe() {
    logInWithGmailScope();
    when(moderatorRepository.findAll())
        .thenReturn(List.of(new Moderator("bad\r\nBcc: attacker@example.com")));

    moderatorNotificationService.notifyModerators(review);

    mockRestServiceServer.verify();
  }

  // ---------- failures never throw ----------

  @Test
  void gmail_error_is_caught_and_does_not_throw() {
    logInWithGmailScope();
    when(moderatorRepository.findAll()).thenReturn(List.of(new Moderator("mod1@ucsb.edu")));
    mockRestServiceServer
        .expect(requestTo(ModeratorNotificationService.GMAIL_SEND_URL))
        .andRespond(withServerError());

    assertDoesNotThrow(() -> moderatorNotificationService.notifyModerators(review));

    mockRestServiceServer.verify();
  }

  @Test
  void repository_error_is_caught_and_does_not_throw() {
    logInWithGmailScope();
    when(moderatorRepository.findAll()).thenThrow(new RuntimeException("database down"));

    assertDoesNotThrow(() -> moderatorNotificationService.notifyModerators(review));

    mockRestServiceServer.verify();
  }
}
