# Authentication Flow

```mermaid
sequenceDiagram
    participant Client
    participant JwtFilter as JwtTokenValidator
    participant Security as SpringSecurity
    participant Handler as ExceptionHandler
    participant Controller

    Note over Client,Controller: Case 1 — Missing Token
    Client->>+JwtFilter: Request (no Authorization header)
    JwtFilter->>Handler: RestAuthenticationEntryPoint
    Handler-->>-Client: 401 Unauthorized

    Note over Client,Controller: Case 2 — Invalid/Expired Token
    Client->>+JwtFilter: Request (Authorization: Bearer <invalid>)
    JwtFilter->>JwtFilter: validate token (fail)
    JwtFilter->>Handler: RestAuthenticationEntryPoint
    Handler-->>-Client: 401 Unauthorized

    Note over Client,Controller: Case 3 — Valid Token, Insufficient Authority
    Client->>+JwtFilter: Request (Authorization: Bearer <valid>)
    JwtFilter->>+Security: Set authentication context
    Security->>+Controller: invoke method
    Controller->>Security: @PreAuthorize check (fail)
    Security->>Handler: RestAccessDeniedHandler
    Handler-->>-Client: 403 Forbidden

    Note over Client,Controller: Case 4 — Valid Token, Correct Authority
    Client->>+JwtFilter: Request (Authorization: Bearer <valid>)
    JwtFilter->>+Security: Set authentication context
    Security->>+Controller: @PreAuthorize check (pass)
    Controller-->>-Client: 200/201/204
```
