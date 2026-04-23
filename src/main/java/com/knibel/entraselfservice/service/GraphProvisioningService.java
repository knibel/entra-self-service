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

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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

    public void createUser(CreateUserRequest request, OAuth2AuthenticationToken principal) {
        String token = accessToken(principal);
        String password = generateTemporaryPassword();

        Map<String, Object> userBody = Map.of(
            "accountEnabled", true,
            "displayName", request.firstName() + " " + request.lastName(),
            "givenName", request.firstName(),
            "surname", request.lastName(),
            "companyName", request.companyName(),
            "department", request.department(),
            "mail", request.email(),
            "identities", List.of(Map.of(
                "signInType", "emailAddress",
                "issuer", properties.getTenantDomain(),
                "issuerAssignedId", request.email()
            )),
            "passwordProfile", Map.of(
                "forceChangePasswordNextSignIn", true,
                "password", password
            ),
            "passwordPolicies", "DisablePasswordExpiration"
        );

        ResponseEntity<Map> response = restTemplate.exchange(
            graphUrl("/users"),
            HttpMethod.POST,
            entity(userBody, token),
            Map.class
        );

        Map body = response.getBody();
        if (body == null || body.get("id") == null) {
            throw new IllegalStateException("User creation succeeded but no user id was returned by Microsoft Graph");
        }

        sendPasswordResetEmail(request.email(), request.firstName(), token);
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

        sendEmailUpdateNotification(request.newEmail(), token);
    }

    private void sendPasswordResetEmail(String email, String firstName, String token) {
        String text = "Hello " + firstName + ",\n\n"
            + "Your account has been created. Please use the link below to set your password:\n"
            + properties.getPasswordResetUrl() + "\n\n"
            + "Use your email address (" + email + ") to identify your account.";
        sendMail(email, "Welcome – Set your password", text, token);
    }

    private void sendEmailUpdateNotification(String newEmail, String token) {
        String text = "Your sign-in email address has been updated to: " + newEmail + "\n\n"
            + "If you did not request this change, please contact your administrator immediately.";
        sendMail(newEmail, "Your sign-in email has been updated", text, token);
    }

    private void sendMail(String toAddress, String subject, String text, String token) {
        Map<String, Object> message = Map.of(
            "message", Map.of(
                "subject", subject,
                "body", Map.of(
                    "contentType", "Text",
                    "content", text
                ),
                "toRecipients", List.of(
                    Map.of("emailAddress", Map.of("address", toAddress))
                )
            )
        );

        restTemplate.exchange(
            graphUrl("/me/sendMail"),
            HttpMethod.POST,
            entity(message, token),
            Void.class
        );
    }

    private String findUserIdByEmail(String email, String token) {
        String safeEmail = email.replace("'", "''");
        String safeIssuer = properties.getTenantDomain().replace("'", "''");
        String filter = "identities/any(c:c/signInType eq 'emailAddress'"
            + " and c/issuer eq '" + safeIssuer + "'"
            + " and c/issuerAssignedId eq '" + safeEmail + "')";
        URI uri = UriComponentsBuilder
            .fromUriString(graphUrl("/users"))
            .queryParam("$top", 1)
            .queryParam("$select", "id")
            .queryParam("$filter", filter)
            .queryParam("$count", "true")
            .encode()
            .build()
            .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("ConsistencyLevel", "eventual");
        ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
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

    String generateTemporaryPassword() {
        // Use a random UUID as the base (cryptographically secure). Append a fixed suffix to
        // satisfy Entra's password complexity requirements (uppercase, digit, special character).
        // This password is never sent to the user; they reset it via the password reset link.
        return UUID.randomUUID().toString() + "A1!";
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
