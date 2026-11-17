# Design

## Context

Ticket Service guarda el hash SHA-256 del payload `v1.<ticketId>.<HMAC>` y el estado (`ISSUED`, `REVOKED`, `USED`). La revocación de una entrada `USED` ya responde `409`.

## Decisions

### 1. Verificación del QR

`TicketQrSigner.verify` recompone el payload esperado para el `ticketId` y lo compara en tiempo constante (`MessageDigest.isEqual`). Además se exige que el hash coincida con el almacenado. Un QR falsificado se rechaza sin consultar estados.

### 2. Marca atómica de uso

`UPDATE tickets SET status='USED', used_at=?, used_by=?, used_gate=? WHERE id=? AND event_id=? AND qr_hash=? AND status='ISSUED'`. Bajo READ COMMITTED, PostgreSQL bloquea la fila: la segunda escritura espera, reevalúa el `WHERE` y no afecta filas. El ingreso y su registro en `access_scans` van en la misma transacción. El índice único parcial `(ticket_id) WHERE result='ADMITTED'` es la segunda barrera. Se descartó un lock distribuido (Redis) porque la base de Ticket ya es el árbitro único de la fila.

### 3. Diagnóstico del rechazo

Si el `UPDATE` no afecta filas, se lee el ticket para distinguir `UNKNOWN_TICKET` (`404`), `WRONG_EVENT` (`422`, la entrada no se consume), `REVOKED` (`409`) y `ALREADY_USED` (`409`, con fecha y puerta del ingreso). Los rechazos usan problem+json con un campo `reason`, y la respuesta no expone datos del comprador.

### 4. Registro de intentos

`access_scans` guarda todos los intentos, incluidos los QR desconocidos (sin FK a `tickets`). Cada intento se publica además como `ACCESS_VALIDATION` en la bitácora centralizada.

## Risks / Trade-offs

- Una entrada `USED` no puede revocarse. Si se cancela un evento ya iniciado, su reintegro queda pausado (`202`) y requiere intervención; es coherente con la regla existente de E1.
- La copia `order_tickets.status` de Purchase sigue `ISSUED` tras el ingreso: el estado de uso autoritativo es el de Ticket Service.
