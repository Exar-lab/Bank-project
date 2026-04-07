# Account Lifecycle

```mermaid
stateDiagram-v2
    [*] --> ACTIVE : createAccount
    ACTIVE --> ACTIVE : deposit / withdraw / transfer

    ACTIVE --> SUSPENDED : adminSuspend
    SUSPENDED --> ACTIVE : adminActivate

    ACTIVE --> BLOCKED : adminBlock
    BLOCKED --> ACTIVE : adminChangeStatus (ACTIVE)

    ACTIVE --> CLOSED : closeAccount

    CLOSED --> [*]
```
