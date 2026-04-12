# user-auth Specification

## Purpose

Defines the requirements and testable scenarios for user authentication, including login, stateful refresh token rotation with reuse detection, logout, and user details resolution.

## Requirements

### Requirement: User Login

The system MUST authenticate users using their credentials, returning an Access Token and a Stateful Refresh Token upon success.

#### Scenario: Successful Login

- GIVEN valid user credentials
- AND the user account is active and not locked
- WHEN the user requests to login
- THEN the system MUST return a 200 OK
- AND the response MUST contain a valid JWT Access Token and a Refresh Token
- AND the new Refresh Token MUST be saved in the database

#### Scenario: Invalid Credentials

- GIVEN invalid user credentials
- WHEN the user requests to login
- THEN the system MUST return a 400 Bad Request or 401 Unauthorized
- AND the response body MUST contain error details

#### Scenario: Locked Account

- GIVEN valid user credentials
- AND the user account is locked
- WHEN the user requests to login
- THEN the system MUST return a 401 Unauthorized or 403 Forbidden
- AND the response MUST indicate the account is locked

### Requirement: Stateful Refresh Token Rotation

The system MUST allow issuing a new Access Token and Refresh Token pair when provided with a valid, active Refresh Token, while revoking the used one.

#### Scenario: Successful Token Refresh

- GIVEN a valid, active, and non-expired Refresh Token
- WHEN the user requests to refresh the token
- THEN the system MUST revoke the provided Refresh Token in the database
- AND the system MUST return a 200 OK with a new Access Token and a new Refresh Token
- AND the new Refresh Token MUST be saved in the database

#### Scenario: Expired or Invalid Refresh Token

- GIVEN an expired or cryptographically invalid Refresh Token
- WHEN the user requests to refresh the token
- THEN the system MUST return a 401 Unauthorized
- AND the token MUST NOT be refreshed

### Requirement: Refresh Token Reuse Detection

The system MUST detect attempts to use a revoked Refresh Token and mitigate potential token theft by revoking all tokens for the compromised user.

#### Scenario: Refresh Token Reuse Attempt

- GIVEN a revoked Refresh Token
- WHEN the user requests to refresh the token using this revoked token
- THEN the system MUST revoke all active Refresh Tokens belonging to that user
- AND the system MUST return a 401 Unauthorized

### Requirement: User Logout

The system MUST allow users to explicitly revoke their active Refresh Token.

#### Scenario: Successful Logout

- GIVEN a valid, active Refresh Token
- WHEN the user requests to logout with this token
- THEN the system MUST revoke the provided Refresh Token in the database
- AND the system MUST return a 200 OK or 204 No Content

### Requirement: User Details Resolution

The system MUST load user data and authorities efficiently for the AuthenticationManager, preventing N+1 queries.

#### Scenario: Loading User Authorities

- GIVEN an authentication attempt
- WHEN the UserDetailsService loads the user by username
- THEN the system MUST execute a single query using `JOIN FETCH` to retrieve the user, their credentials, and their roles/authorities
- AND the loaded `UserDetails` MUST correctly reflect the account lock status