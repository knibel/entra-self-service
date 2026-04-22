# Entra Self Service

Spring Boot + Thymeleaf self-service UI for Entra ID user provisioning.

## Features

- OIDC-secured UI (OAuth2 Authorization Code Flow)
- Create/invite user by email (email OTP sign-in), company, department, first/last name
- Update user's **primary** email identity and resend invitation

## Run locally

Prerequisites:

- Java 17+
- Maven 3.9+

Set environment variables:

- `AZURE_CLIENT_ID`
- `AZURE_CLIENT_SECRET`
- `ENTRA_TENANT_ID` (GUID)
- `ENTRA_TENANT_DOMAIN` (for example `contoso.onmicrosoft.com`)
- `ENTRA_INVITE_REDIRECT_URL` (optional, defaults to `http://localhost:8080/`)

Start:

```bash
mvn spring-boot:run
```

Open: `http://localhost:8080`.

## Required Entra app registrations

You need one confidential client app registration used by this Spring Boot application.

### 1) App registration for this web app

- Platform: **Web**
- Redirect URI: `http://localhost:8080/login/oauth2/code/azure` (and production equivalent)
- Authentication: enable **ID tokens** and **Access tokens**
- Create a client secret and configure it as `AZURE_CLIENT_SECRET`

### 2) Microsoft Graph API permissions (delegated)

Add delegated Graph permissions to the app registration and grant admin consent:

- `User.Invite.All` (send invitations)
- `User.ReadWrite.All` (update user identity/mail)
- `Directory.Read.All` (resolve user by identity)

Because the app uses the authorization code flow and calls Graph on behalf of the signed-in admin/operator, the signed-in user also needs sufficient Entra role permissions.

### 3) Who can sign in

Use single-tenant or multi-tenant according to your environment. For a fixed tenant setup, single-tenant is typical.

## Configuration

`src/main/resources/application.yml` defines:

- OIDC client registration `azure`
- issuer URI: `https://login.microsoftonline.com/${ENTRA_TENANT_ID}/v2.0`
- required Graph scopes
- static Entra provisioning properties under `entra.*`

## Notes

- The update flow replaces the user `identities` entry with `signInType=emailAddress` for the configured tenant domain, updates `mail`, and resends invitation with `resetRedemption=true`.
- Handle production hardening as needed (auditing, fine-grained authorization, error mapping, rate limits, etc.).
