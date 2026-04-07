# API Documentation Coverage Report

## Endpoint Coverage

| # | Controller | HTTP Method | Path | OpenAPI ✓ | Postman ✓ |
|---|---|---|---|---|---|
| 1 | AccountController | GET | /api/v1/accounts | ✓ | ✓ |
| 2 | AccountController | GET | /api/v1/accounts/{id} | ✓ | ✓ |
| 3 | AccountController | GET | /api/v1/accounts/code/{code} | ✓ | ✓ |
| 4 | AccountController | GET | /api/v1/accounts/{id}/balance | ✓ | ✓ |
| 5 | AccountController | POST | /api/v1/accounts | ✓ | ✓ |
| 6 | AccountController | PUT | /api/v1/accounts/{code} | ✓ | ✓ |
| 7 | AccountController | DELETE | /api/v1/accounts/{id} | ✓ | ✓ |
| 8 | AccountAdminController | PUT | /api/v1/admin/accounts/{id}/status | ✓ | ✓ |
| 9 | AccountAdminController | POST | /api/v1/admin/accounts/{id}/close | ✓ | ✓ |
| 10 | CardController | POST | /api/v1/cards | ✓ | ✓ |
| 11 | CardController | GET | /api/v1/cards | ✓ | ✓ |
| 12 | CardController | GET | /api/v1/cards/account/{accountCode} | ✓ | ✓ |
| 13 | CardController | GET | /api/v1/cards/{cardCode} | ✓ | ✓ |
| 14 | CardController | POST | /api/v1/cards/{cardCode}/activate | ✓ | ✓ |
| 15 | CardController | POST | /api/v1/cards/{cardCode}/block | ✓ | ✓ |
| 16 | CardController | POST | /api/v1/cards/{cardCode}/report-stolen | ✓ | ✓ |
| 17 | CardController | POST | /api/v1/cards/{cardCode}/report-lost | ✓ | ✓ |
| 18 | CardController | POST | /api/v1/cards/{cardCode}/close | ✓ | ✓ |
| 19 | CardController | PUT | /api/v1/cards/{cardCode}/limits | ✓ | ✓ |
| 20 | CardController | PUT | /api/v1/cards/{cardCode}/features | ✓ | ✓ |
| 21 | CardAdminController | GET | /api/v1/admin/cards | ✓ | ✓ |
| 22 | CardAdminController | PUT | /api/v1/admin/cards/{cardCode}/status | ✓ | ✓ |
| 23 | CardAdminController | POST | /api/v1/admin/cards/{cardCode}/reset-pin | ✓ | ✓ |
| 24 | TransactionController | POST | /api/v1/transactions/transfer | ✓ | ✓ |
| 25 | TransactionController | POST | /api/v1/transactions/payment | ✓ | ✓ |
| 26 | TransactionController | POST | /api/v1/transactions/pay-service | ✓ | ✓ |
| 27 | TransactionController | POST | /api/v1/transactions/schedule | ✓ | ✓ |
| 28 | TransactionController | DELETE | /api/v1/transactions/{id}/schedule | ✓ | ✓ |
| 29 | TransactionController | POST | /api/v1/transactions/{id}/reversal | ✓ | ✓ |
| 30 | TransactionController | GET | /api/v1/transactions/me | ✓ | ✓ |
| 31 | TransactionController | GET | /api/v1/transactions/{id} | ✓ | ✓ |
| 32 | TransactionController | GET | /api/v1/transactions/account/{code} | ✓ | ✓ |
| 33 | TransactionController | GET | /api/v1/transactions/categories | ✓ | ✓ |
| 34 | TransactionController | GET | /api/v1/transactions/summary | ✓ | ✓ |
| 35 | TransactionAdminController | GET | /api/v1/admin/transactions | ✓ | ✓ |
| 36 | TransactionAdminController | GET | /api/v1/admin/transactions/suspicious | ✓ | ✓ |
| 37 | TransactionAdminController | PUT | /api/v1/admin/transactions/{id}/approve | ✓ | ✓ |
| 38 | TransactionAdminController | PUT | /api/v1/admin/transactions/{id}/reject | ✓ | ✓ |
| 39 | TransactionAdminController | PUT | /api/v1/admin/transactions/{id}/reverse | ✓ | ✓ |
| 40 | TransactionAdminController | POST | /api/v1/admin/transactions/{id}/fraud | ✓ | ✓ |
| 41 | TransactionEmployeeController | POST | /api/v1/teller/transactions/cash-deposit | ✓ | ✓ |
| 42 | TransactionEmployeeController | POST | /api/v1/teller/transactions/cash-withdrawal | ✓ | ✓ |
| 43 | TransactionEmployeeController | POST | /api/v1/teller/transactions/check-deposit | ✓ | ✓ |
| 44 | EnvelopeController | POST | /api/v1/envelopes | ✓ | ✓ |
| 45 | EnvelopeController | GET | /api/v1/envelopes | ✓ | ✓ |
| 46 | EnvelopeController | GET | /api/v1/envelopes/{code} | ✓ | ✓ |
| 47 | EnvelopeController | GET | /api/v1/envelopes/account/{accountCode} | ✓ | ✓ |
| 48 | EnvelopeController | GET | /api/v1/envelopes/status/{status} | ✓ | ✓ |
| 49 | EnvelopeController | GET | /api/v1/envelopes/type/{type} | ✓ | ✓ |
| 50 | EnvelopeController | GET | /api/v1/envelopes/search | ✓ | ✓ |
| 51 | EnvelopeController | GET | /api/v1/envelopes/created-after | ✓ | ✓ |
| 52 | EnvelopeController | PUT | /api/v1/envelopes/{code} | ✓ | ✓ |
| 53 | EnvelopeController | POST | /api/v1/envelopes/deposit | ✓ | ✓ |
| 54 | EnvelopeController | POST | /api/v1/envelopes/withdraw | ✓ | ✓ |
| 55 | EnvelopeController | DELETE | /api/v1/envelopes/{code} | ✓ | ✓ |
| 56 | UserController | GET | /api/v1/users/me | ✓ | ✓ |
| 57 | UserController | PUT | /api/v1/users/me | ✓ | ✓ |
| 58 | UserController | PUT | /api/v1/users/me/password | ✓ | ✓ |
| 59 | UserController | DELETE | /api/v1/users/me | ✓ | ✓ |
| 60 | UserAdminController | GET | /api/v1/admin/users/{id} | ✓ | ✓ |
| 61 | UserAdminController | PUT | /api/v1/admin/users/{id} | ✓ | ✓ |
| 62 | UserAdminController | PUT | /api/v1/admin/users/{id}/suspend | ✓ | ✓ |
| 63 | UserAdminController | PUT | /api/v1/admin/users/{id}/activate | ✓ | ✓ |
| 64 | UserAdminController | PUT | /api/v1/admin/users/{id}/status | ✓ | ✓ |
| 65 | UserAdminController | POST | /api/v1/admin/users/employees | ✓ | ✓ |
| 66 | PublicUserController | POST | /api/v1/public/users/register | ✓ | ✓ |

## Summary
- Total endpoints: 66
- OpenAPI coverage: 66/66
- Postman coverage: 66/66
- Public endpoints (no auth): 1
- Admin endpoints: 22
- Customer endpoints: 43
