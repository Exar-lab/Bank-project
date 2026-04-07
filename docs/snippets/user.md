# User API Snippets

## Register User (Public)

### curl
```bash
curl -X POST "{{baseUrl}}/api/v1/public/users/register" \
  -H "Content-Type: application/json" \
  -d '{
    "fistName": "Nuevo",
    "lastName": "Usuario",
    "email": "nuevo.usuario@example.com",
    "password": "**redacted**",
    "documentNumber": "998877665544",
    "documentType": "CEDULA",
    "birthDate": "1995-08-20",
    "phoneNumber": "+573001234000",
    "address": "Avenida Siempre Viva 123",
    "username": "nuevousuario"
  }'
```

### JavaScript fetch
```javascript
const response = await fetch(`${BASE_URL}/api/v1/public/users/register`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    fistName: 'Nuevo',
    lastName: 'Usuario',
    email: 'nuevo.usuario@example.com',
    password: '**redacted**',
    documentNumber: '998877665544',
    documentType: 'CEDULA',
    birthDate: '1995-08-20',
    phoneNumber: '+573001234000',
    address: 'Avenida Siempre Viva 123',
    username: 'nuevousuario'
  })
});
const created = await response.json();
```

## Get My Profile

### curl
```bash
curl -X GET "{{baseUrl}}/api/v1/users/me" \
  -H "Authorization: Bearer $TOKEN"
```

### JavaScript fetch
```javascript
const response = await fetch(`${BASE_URL}/api/v1/users/me`, {
  headers: { Authorization: `Bearer ${TOKEN}` }
});
const me = await response.json();
```

## Update My Profile

### curl
```bash
curl -X PUT "{{baseUrl}}/api/v1/users/me" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "fistName": "Juan",
    "lastName": "Pérez",
    "phoneNumber": "+573001234567",
    "address": "Calle Falsa 123",
    "username": "juanperez"
  }'
```

### JavaScript fetch
```javascript
const response = await fetch(`${BASE_URL}/api/v1/users/me`, {
  method: 'PUT',
  headers: {
    Authorization: `Bearer ${TOKEN}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    fistName: 'Juan',
    lastName: 'Pérez',
    phoneNumber: '+573001234567',
    address: 'Calle Falsa 123',
    username: 'juanperez'
  })
});
const updated = await response.json();
```

## Update Password

### curl
```bash
curl -X PUT "{{baseUrl}}/api/v1/users/me/password" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "currentPassword": "**redacted**",
    "password": "**redacted**",
    "confirmPassword": "**redacted**"
  }'
```

### JavaScript fetch
```javascript
await fetch(`${BASE_URL}/api/v1/users/me/password`, {
  method: 'PUT',
  headers: {
    Authorization: `Bearer ${TOKEN}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    currentPassword: '**redacted**',
    password: '**redacted**',
    confirmPassword: '**redacted**'
  })
});
```

## Delete My Account

### curl
```bash
curl -X DELETE "{{baseUrl}}/api/v1/users/me" \
  -H "Authorization: Bearer $TOKEN"
```

### JavaScript fetch
```javascript
await fetch(`${BASE_URL}/api/v1/users/me`, {
  method: 'DELETE',
  headers: { Authorization: `Bearer ${TOKEN}` }
});
```

## Create Employee (Admin)

### curl
```bash
curl -X POST "{{baseUrl}}/api/v1/admin/users/employees" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "fistName": "Carlos",
    "lastName": "Admin",
    "email": "carlos.admin@example.com",
    "password": "**redacted**",
    "documentNumber": "321654987012",
    "documentType": "CEDULA",
    "birthDate": "1992-02-10",
    "phoneNumber": "+573001111222",
    "address": "Centro 101",
    "role": "TELLER"
  }'
```

### JavaScript fetch
```javascript
const response = await fetch(`${BASE_URL}/api/v1/admin/users/employees`, {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${TOKEN}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    fistName: 'Carlos',
    lastName: 'Admin',
    email: 'carlos.admin@example.com',
    password: '**redacted**',
    documentNumber: '321654987012',
    documentType: 'CEDULA',
    birthDate: '1992-02-10',
    phoneNumber: '+573001111222',
    address: 'Centro 101',
    role: 'TELLER'
  })
});
const employee = await response.json();
```
