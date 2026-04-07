# Transaction API Snippets

## Transfer Funds

### curl
```bash
curl -X POST "{{baseUrl}}/api/v1/transactions/transfer" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccountCode": "{{fromAccountCode}}",
    "toAccountCode": "{{toAccountCode}}",
    "amount": 250.50,
    "description": "Monthly transfer",
    "confirmTransfer": true
  }'
```

### JavaScript fetch
```javascript
const response = await fetch(`${BASE_URL}/api/v1/transactions/transfer`, {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${TOKEN}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    fromAccountCode,
    toAccountCode,
    amount: 250.5,
    description: 'Monthly transfer',
    confirmTransfer: true
  })
});
const tx = await response.json();
```

## Card Payment

### curl
```bash
curl -X POST "{{baseUrl}}/api/v1/transactions/payment" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "cardCode": "{{cardCode}}",
    "amount": 100.00,
    "merchantName": "Supermarket Demo",
    "merchantMccCode": "5411",
    "description": "Weekly grocery"
  }'
```

### JavaScript fetch
```javascript
const response = await fetch(`${BASE_URL}/api/v1/transactions/payment`, {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${TOKEN}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    cardCode,
    amount: 100,
    merchantName: 'Supermarket Demo',
    merchantMccCode: '5411',
    description: 'Weekly grocery'
  })
});
const tx = await response.json();
```

## Service Payment

### curl
```bash
curl -X POST "{{baseUrl}}/api/v1/transactions/pay-service" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "accountCode": "{{accountCode}}",
    "amount": 80.00,
    "serviceProvider": "UtilityCorp",
    "referenceNumber": "INV-10001",
    "description": "Electricity bill"
  }'
```

### JavaScript fetch
```javascript
const response = await fetch(`${BASE_URL}/api/v1/transactions/pay-service`, {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${TOKEN}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    accountCode,
    amount: 80,
    serviceProvider: 'UtilityCorp',
    referenceNumber: 'INV-10001',
    description: 'Electricity bill'
  })
});
const tx = await response.json();
```

## Schedule Transfer

### curl
```bash
curl -X POST "{{baseUrl}}/api/v1/transactions/schedule" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccountCode": "{{fromAccountCode}}",
    "toAccountCode": "{{toAccountCode}}",
    "amount": 50.00,
    "scheduledFor": "2026-05-01T10:00:00",
    "description": "Scheduled transfer"
  }'
```

### JavaScript fetch
```javascript
const response = await fetch(`${BASE_URL}/api/v1/transactions/schedule`, {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${TOKEN}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    fromAccountCode,
    toAccountCode,
    amount: 50,
    scheduledFor: '2026-05-01T10:00:00',
    description: 'Scheduled transfer'
  })
});
const tx = await response.json();
```

## Cancel Scheduled Transaction

### curl
```bash
curl -X DELETE "{{baseUrl}}/api/v1/transactions/{{transactionId}}/schedule" \
  -H "Authorization: Bearer $TOKEN"
```

### JavaScript fetch
```javascript
await fetch(`${BASE_URL}/api/v1/transactions/${transactionId}/schedule`, {
  method: 'DELETE',
  headers: { Authorization: `Bearer ${TOKEN}` }
});
```

## Request Reversal

### curl
```bash
curl -X POST "{{baseUrl}}/api/v1/transactions/{{transactionId}}/reversal?reason=Customer+request" \
  -H "Authorization: Bearer $TOKEN"
```

### JavaScript fetch
```javascript
await fetch(`${BASE_URL}/api/v1/transactions/${transactionId}/reversal?reason=Customer%20request`, {
  method: 'POST',
  headers: { Authorization: `Bearer ${TOKEN}` }
});
```

## Get My Transactions (Paginated)

### curl
```bash
curl -X GET "{{baseUrl}}/api/v1/transactions/me?page=0&size=20&sort=createdAt,desc" \
  -H "Authorization: Bearer $TOKEN"
```

### JavaScript fetch
```javascript
const response = await fetch(`${BASE_URL}/api/v1/transactions/me?page=0&size=20&sort=createdAt,desc`, {
  headers: { Authorization: `Bearer ${TOKEN}` }
});
const page = await response.json();
```

## Get Transactions Summary

### curl
```bash
curl -X GET "{{baseUrl}}/api/v1/transactions/summary?startDate=2026-01-01T00:00:00&endDate=2026-12-31T23:59:59" \
  -H "Authorization: Bearer $TOKEN"
```

### JavaScript fetch
```javascript
const response = await fetch(`${BASE_URL}/api/v1/transactions/summary?startDate=2026-01-01T00:00:00&endDate=2026-12-31T23:59:59`, {
  headers: { Authorization: `Bearer ${TOKEN}` }
});
const summary = await response.json();
```
