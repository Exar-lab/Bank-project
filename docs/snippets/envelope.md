# Envelope API Snippets

## Create Envelope

### curl
```bash
curl -X POST "{{baseUrl}}/api/v1/envelopes" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Vacation 2026",
    "description": "Trip savings",
    "accountCode": "{{accountCode}}",
    "envelopeType": "VACATION",
    "targetAmount": 1200.00,
    "targetDate": "2026-12-01",
    "autoContribute": true,
    "autoContributeAmount": 100.00,
    "autoContributeFrequency": "MONTHLY",
    "roundUpEnabled": false,
    "icon": "✈️",
    "color": "#00AEEF",
    "priority": 5
  }'
```

### JavaScript fetch
```javascript
const response = await fetch(`${BASE_URL}/api/v1/envelopes`, {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${TOKEN}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    name: 'Vacation 2026',
    description: 'Trip savings',
    accountCode,
    envelopeType: 'VACATION',
    targetAmount: 1200,
    targetDate: '2026-12-01',
    autoContribute: true,
    autoContributeAmount: 100,
    autoContributeFrequency: 'MONTHLY',
    roundUpEnabled: false,
    icon: '✈️',
    color: '#00AEEF',
    priority: 5
  })
});
const created = await response.json();
```

## List My Envelopes

### curl
```bash
curl -X GET "{{baseUrl}}/api/v1/envelopes" \
  -H "Authorization: Bearer $TOKEN"
```

### JavaScript fetch
```javascript
const response = await fetch(`${BASE_URL}/api/v1/envelopes`, {
  headers: { Authorization: `Bearer ${TOKEN}` }
});
const items = await response.json();
```

## Get Envelope by Code

### curl
```bash
curl -X GET "{{baseUrl}}/api/v1/envelopes/{{envelopeCode}}" \
  -H "Authorization: Bearer $TOKEN"
```

### JavaScript fetch
```javascript
const response = await fetch(`${BASE_URL}/api/v1/envelopes/${envelopeCode}`, {
  headers: { Authorization: `Bearer ${TOKEN}` }
});
const item = await response.json();
```

## Deposit to Envelope

### curl
```bash
curl -X POST "{{baseUrl}}/api/v1/envelopes/deposit" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "envelopeCode": "{{envelopeCode}}",
    "amount": 40.00,
    "description": "Monthly contribution"
  }'
```

### JavaScript fetch
```javascript
const response = await fetch(`${BASE_URL}/api/v1/envelopes/deposit`, {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${TOKEN}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    envelopeCode,
    amount: 40,
    description: 'Monthly contribution'
  })
});
const updated = await response.json();
```

## Withdraw from Envelope

### curl
```bash
curl -X POST "{{baseUrl}}/api/v1/envelopes/withdraw" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "envelopeCode": "{{envelopeCode}}",
    "amount": 15.00,
    "description": "Expense",
    "confirmWithdrawal": true
  }'
```

### JavaScript fetch
```javascript
const response = await fetch(`${BASE_URL}/api/v1/envelopes/withdraw`, {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${TOKEN}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    envelopeCode,
    amount: 15,
    description: 'Expense',
    confirmWithdrawal: true
  })
});
const updated = await response.json();
```

## Update Envelope

### curl
```bash
curl -X PUT "{{baseUrl}}/api/v1/envelopes/{{envelopeCode}}" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Vacation 2026 Updated",
    "description": "Updated goal",
    "targetAmount": 1500.00,
    "priority": 6
  }'
```

### JavaScript fetch
```javascript
const response = await fetch(`${BASE_URL}/api/v1/envelopes/${envelopeCode}`, {
  method: 'PUT',
  headers: {
    Authorization: `Bearer ${TOKEN}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    name: 'Vacation 2026 Updated',
    description: 'Updated goal',
    targetAmount: 1500,
    priority: 6
  })
});
const updated = await response.json();
```

## Delete Envelope

### curl
```bash
curl -X DELETE "{{baseUrl}}/api/v1/envelopes/{{envelopeCode}}" \
  -H "Authorization: Bearer $TOKEN"
```

### JavaScript fetch
```javascript
await fetch(`${BASE_URL}/api/v1/envelopes/${envelopeCode}`, {
  method: 'DELETE',
  headers: { Authorization: `Bearer ${TOKEN}` }
});
```
