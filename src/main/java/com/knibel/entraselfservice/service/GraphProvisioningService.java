package com.knibel.entraselfservice.service;

import com.knibel.entraselfservice.config.EntraProperties;
import com.knibel.entraselfservice.model.CreateUserRequest;
import com.knibel.entraselfservice.model.UpdateEmailRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class GraphProvisioningService {

    private final RestTemplate restTemplate;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final EntraProperties properties;

    public GraphProvisioningService(
        RestTemplate restTemplate,
        OAuth2AuthorizedClientService authorizedClientService,
        EntraProperties properties
    ) {
        this.restTemplate = restTemplate;
        this.authorizedClientService = authorizedClientService;
        this.properties = properties;
    }

    public void inviteUser(CreateUserRequest request, OAuth2AuthenticationToken principal) {
        String token = accessToken(principal);
        Map<String, Object> inviteRequest = Map.of(
            "invitedUserEmailAddress", request.email(),
            "inviteRedirectUrl", properties.getInviteRedirectUrl(),
            "sendInvitationMessage", true,
            "invitedUserDisplayName", request.firstName() + " " + request.lastName()
        );

        ResponseEntity<Map> response = restTemplate.exchange(
            graphUrl("/invitations"),
            HttpMethod.POST,
            entity(inviteRequest, token),
            Map.class
        );

        String userId = extractUserId(response.getBody());
        if (userId == null) {
            throw new IllegalStateException("Invitation succeeded but no invited user id was returned by Microsoft Graph");
        }

        Map<String, Object> userPatch = Map.of(
            "givenName", request.firstName(),
            "surname", request.lastName(),
            "displayName", request.firstName() + " " + request.lastName(),
            "companyName", request.companyName(),
            "department", request.department()
        );

        restTemplate.exchange(
            graphUrl("/users/" + userId),
            HttpMethod.PATCH,
            entity(userPatch, token),
            Void.class
        );
    }

    public void updatePrimaryEmail(UpdateEmailRequest request, OAuth2AuthenticationToken principal) {
        String token = accessToken(principal);
        String userId = findUserIdByEmail(request.currentEmail(), token);

        if (userId == null) {
            throw new IllegalArgumentException("No user found for email: " + request.currentEmail());
        }

        Map<String, Object> userPatch = Map.of(
            "identities", List.of(
                Map.of(
                    "signInType", "emailAddress",
                    "issuer", properties.getTenantDomain(),
                    "issuerAssignedId", request.newEmail()
                )
            ),
            "mail", request.newEmail(),
            "otherMails", List.of(request.newEmail())
        );

        restTemplate.exchange(
            graphUrl("/users/" + userId),
            HttpMethod.PATCH,
            entity(userPatch, token),
            Void.class
        );

        Map<String, Object> reinviteRequest = Map.of(
            "invitedUserEmailAddress", request.newEmail(),
            "inviteRedirectUrl", properties.getInviteRedirectUrl(),
            "sendInvitationMessage", true,
            "resetRedemption", true,
            "invitedUser", Map.of("id", userId)
        );

        restTemplate.exchange(
            graphUrl("/invitations"),
            HttpMethod.POST,
            entity(reinviteRequest, token),
            Map.class
        );
    }

    private String findUserIdByEmail(String email, String token) {
        validateFilterSafeEmail(email);
        String filter = "identities/any(c:c/issuerAssignedId eq '" + email + "' and c/signInType eq 'emailAddress')";
        String uri = UriComponentsBuilder
            .fromUriString(graphUrl("/users"))
            .queryParam("$top", 1)
            .queryParam("$select", "id")
            .queryParam("$filter", filter)
            .build()
            .toUriString();

        ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, entity(null, token), Map.class);
        Object rawValue = response.getBody() == null ? null : response.getBody().get("value");
        if (!(rawValue instanceof List<?> values) || values.isEmpty()) {
            return null;
        }

        Object first = values.get(0);
        if (!(first instanceof Map<?, ?> firstMap)) {
            return null;
        }

        Object id = firstMap.get("id");
        return Objects.toString(id, null);
    }

    private void validateFilterSafeEmail(String email) {
        if (email.contains("'") || email.contains("\n") || email.contains("\r")) {
            throw new IllegalArgumentException("Email contains unsupported characters");
        }
    }

    private String extractUserId(Map body) {
        if (body == null) {
            return null;
        }
        Object invitedUser = body.get("invitedUser");
        if (!(invitedUser instanceof Map<?, ?> user)) {
            return null;
        }
        return Objects.toString(user.get("id"), null);
    }

    private HttpEntity<?> entity(Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private String accessToken(OAuth2AuthenticationToken principal) {
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
            properties.getClientRegistrationId(),
            principal.getName()
        );
        if (client == null || client.getAccessToken() == null) {
            throw new IllegalStateException("No OAuth2 access token found for current user session");
        }
        return client.getAccessToken().getTokenValue();
    }

    private String graphUrl(String path) {
        return properties.getGraphBaseUrl() + path;
    }
}
