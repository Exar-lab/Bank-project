# Card API Snippets

## Create Card

### curl
```bash
curl -X POST "{{baseUrl}}/api/v1/cards" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "cardType": "DEBITO",
    "brand": "VISA",
    "tier": "CLASSIC",
    "accountCode": "{{accountCode}}"
  }'
```

### JavaScript fetch
```javascript
const response = await fetch(`${BASE_URL}/api/v1/cards`, {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${TOKEN}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    cardType: 'DEBITO',
    brand: 'VISA',
    tier: 'CLASSIC',
    accountCode: accountCode
  })
});
const created = await response.json();
```

## List My Cards

### curl
```bash
curl -X GET "{{baseUrl}}/api/v1/cards" \
  -H "Authorization: Bearer $TOKEN"
```

### JavaScript fetch
```javascript
const response = await fetch(`${BASE_URL}/api/v1/cards`, {
  headers: { Authorization: `Bearer ${TOKEN}` }
});
const cards = await response.json();
```

## Activate Card

### curl
```bash
curl -X POST "{{baseUrl}}/api/v1/cards/{{cardCode}}/activate" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "pin": "**redacted**"
  }'
```

### JavaScript fetch
```javascript
const response = await fetch(`${BASE_URL}/api/v1/cards/${cardCode}/activate`, {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${TOKEN}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({ pin: '**redacted**' })
});
const activated = await response.json();
```

## Block Card

### curl
```bash
curl -X POST "{{baseUrl}}/api/v1/cards/{{cardCode}}/block" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "reason": "Suspicious activity"
  }'
```

### JavaScript fetch
```javascript
const response = await fetch(`${BASE_URL}/api/v1/cards/${cardCode}/block`, {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${TOKEN}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({ reason: 'Suspicious activity' })
});
const blocked = await response.json();
```

## Update Card Limits

### curl
```bash
curl -X PUT "{{baseUrl}}/api/v1/cards/{{cardCode}}/limits" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "dailyLimit": 500.00,
    "monthlyLimit": 5000.00
  }'
```

### JavaScript fetch
```javascript
const response = await fetch(`${BASE_URL}/api/v1/cards/${cardCode}/limits`, {
  method: 'PUT',
  headers: {
    Authorization: `Bearer ${TOKEN}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({ dailyLimit: 500.0, monthlyLimit: 5000.0 })
});
const updated = await response.json();
```

## Update Card Features

### curl
```bash
curl -X PUT "{{baseUrl}}/api/v1/cards/{{cardCode}}/features" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "contactlessEnabled": true,
    "onlinePaymentsEnabled": true,
    "internationalEnabled": false
  }'
```

### JavaScript fetch
```javascript
const response = await fetch(`${BASE_URL}/api/v1/cards/${cardCode}/features`, {
  method: 'PUT',
  headers: {
    Authorization: `Bearer ${TOKEN}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    contactlessEnabled: true,
    onlinePaymentsEnabled: true,
    internationalEnabled: false
  })
});
const updated = await response.json();
```

## Report Stolen or Lost

### curl
```bash
curl -X POST "{{baseUrl}}/api/v1/cards/{{cardCode}}/report-stolen" \
  -H "Authorization: Bearer $TOKEN"
```

### JavaScript fetch
```javascript
const response = await fetch(`${BASE_URL}/api/v1/cards/${cardCode}/report-stolen`, {
  method: 'POST',
  headers: { Authorization: `Bearer ${TOKEN}` }
});
const result = await response.json();
```

## Close Card

### curl
```bash
curl -X POST "{{baseUrl}}/api/v1/cards/{{cardCode}}/close" \
  -H "Authorization: Bearer $TOKEN"
```

### JavaScript fetch
```javascript
const response = await fetch(`${BASE_URL}/api/v1/cards/${cardCode}/close`, {
  method: 'POST',
  headers: { Authorization: `Bearer ${TOKEN}` }
});
const closed = await response.json();
```
