package com.knibel.entraselfservice.service;

import com.knibel.entraselfservice.config.EntraProperties;
import com.knibel.entraselfservice.model.CreateUserRequest;
import com.knibel.entraselfservice.model.UpdateEmailRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GraphProvisioningServiceTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private OAuth2AuthorizedClientService clientService;
    private OAuth2AuthenticationToken principal;
    private GraphProvisioningService service;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        clientService = mock(OAuth2AuthorizedClientService.class);
        principal = mock(OAuth2AuthenticationToken.class);
        when(principal.getName()).thenReturn("alice");

        ClientRegistration registration = ClientRegistration.withRegistrationId("azure")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .tokenUri("https://login.microsoftonline.com/token")
            .authorizationUri("https://login.microsoftonline.com/authorize")
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .clientId("id")
            .build();

        OAuth2AccessToken token = new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "token-value",
            Instant.now(),
            Instant.now().plusSeconds(300)
        );

        OAuth2AuthorizedClient client = new OAuth2AuthorizedClient(registration, "alice", token);
        when(clientService.loadAuthorizedClient("azure", "alice")).thenReturn(client);

        EntraProperties props = new EntraProperties();
        props.setClientRegistrationId("azure");
        props.setGraphBaseUrl("https://graph.microsoft.com/v1.0");
        props.setTenantDomain("contoso.onmicrosoft.com");
        props.setTenantId("tenant");
        props.setPasswordResetUrl("https://passwordreset.microsoftonline.com/");

        service = new GraphProvisioningService(restTemplate, clientService, props);
    }

    @Test
    void updatePrimaryEmailPatchesIdentityAndSendsNotification() {
        server.expect(requestTo(org.hamcrest.Matchers.allOf(
                    org.hamcrest.Matchers.containsString("/users?"),
                    org.hamcrest.Matchers.containsString("identities"),
                    org.hamcrest.Matchers.containsString("emailAddress"),
                    org.hamcrest.Matchers.containsString("contoso.onmicrosoft.com"))))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"value\":[{\"id\":\"user-1\"}]}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://graph.microsoft.com/v1.0/users/user-1"))
            .andExpect(method(HttpMethod.PATCH))
            .andRespond(withSuccess());

        server.expect(requestTo("https://graph.microsoft.com/v1.0/me/sendMail"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess());

        service.updatePrimaryEmail(new UpdateEmailRequest("old@example.com", "new@example.com"), principal);

        server.verify();
    }

    @Test
    void createUserPostsUserAndSendsPasswordResetEmail() {
        server.expect(requestTo("https://graph.microsoft.com/v1.0/users"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"id\":\"user-2\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://graph.microsoft.com/v1.0/me/sendMail"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess());

        service.createUser(
            new CreateUserRequest("new@example.com", "Contoso", "Engineering", "Ada", "Lovelace"),
            principal
        );

        server.verify();
    }

    @Test
    void generateTemporaryPasswordIsSecureAndMeetsComplexity() {
        for (int i = 0; i < 20; i++) {
            String pw = service.generateTemporaryPassword();
            assertThat(pw).hasSizeGreaterThanOrEqualTo(16);
            assertThat(pw).matches(".*[A-Z].*");
            assertThat(pw).matches(".*[a-z].*");
            assertThat(pw).matches(".*[0-9].*");
            assertThat(pw).matches(".*[!@#$%^&*].*");
        }
    }
}
