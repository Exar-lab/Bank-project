# Account API Snippets

## List My Accounts

### curl
```bash
curl -X GET "{{baseUrl}}/api/v1/accounts" \
  -H "Authorization: Bearer $TOKEN"
```

### JavaScript fetch
```javascript
const response = await fetch(`${BASE_URL}/api/v1/accounts`, {
  headers: { Authorization: `Bearer ${TOKEN}` }
});
const data = await response.json();
```

## Get Account by Code

### curl
```bash
curl -X GET "{{baseUrl}}/api/v1/accounts/code/{{accountCode}}" \
  -H "Authorization: Bearer $TOKEN"
```

### JavaScript fetch
```javascript
const response = await fetch(`${BASE_URL}/api/v1/accounts/code/${accountCode}`, {
  headers: { Authorization: `Bearer ${TOKEN}` }
});
const data = await response.json();
```

## Get Account Balance

### curl
```bash
curl -X GET "{{baseUrl}}/api/v1/accounts/{{accountId}}/balance" \
  -H "Authorization: Bearer $TOKEN"
```

### JavaScript fetch
```javascript
const response = await fetch(`${BASE_URL}/api/v1/accounts/${accountId}/balance`, {
  headers: { Authorization: `Bearer ${TOKEN}` }
});
const balance = await response.json();
```

## Create Account

### curl
```bash
curl -X POST "{{baseUrl}}/api/v1/accounts" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "accountType": "SAVINGS",
    "currency": "USD",
    "overdraftLimit": 100.00,
    "interestRate": 2.50,
    "documentNumber": "123456789012"
  }'
```

### JavaScript fetch
```javascript
const response = await fetch(`${BASE_URL}/api/v1/accounts`, {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${TOKEN}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    accountType: 'SAVINGS',
    currency: 'USD',
    overdraftLimit: 100.0,
    interestRate: 2.5,
    documentNumber: '123456789012'
  })
});
const created = await response.json();
```

## Update Account

### curl
```bash
curl -X PUT "{{baseUrl}}/api/v1/accounts/{{accountCode}}" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "overdraftLimit": 150.00,
    "interestRate": 2.80,
    "currency": "USD"
  }'
```

### JavaScript fetch
```javascript
const response = await fetch(`${BASE_URL}/api/v1/accounts/${accountCode}`, {
  method: 'PUT',
  headers: {
    Authorization: `Bearer ${TOKEN}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    overdraftLimit: 150.0,
    interestRate: 2.8,
    currency: 'USD'
  })
});
const updated = await response.json();
```

## Close Account

### curl
```bash
curl -X DELETE "{{baseUrl}}/api/v1/accounts/{{accountId}}" \
  -H "Authorization: Bearer $TOKEN"
```

### JavaScript fetch
```javascript
await fetch(`${BASE_URL}/api/v1/accounts/${accountId}`, {
  method: 'DELETE',
  headers: { Authorization: `Bearer ${TOKEN}` }
});
```
