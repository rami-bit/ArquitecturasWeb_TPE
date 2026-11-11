package com.ticketya.purchase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/purchases")
public class PurchaseController {
    private final JdbcTemplate jdbc;
    private final RestClient http;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;
    private final String catalogUrl, inventoryUrl, paymentUrl, ticketUrl;
    private final int ticketLimit, holdTtl;

    public PurchaseController(JdbcTemplate jdbc, ObjectMapper mapper, TransactionTemplate transactions,
            @Value("${purchase.catalog-url}") String catalogUrl,
            @Value("${purchase.inventory-url}") String inventoryUrl,
            @Value("${purchase.payment-url}") String paymentUrl,
            @Value("${purchase.ticket-url}") String ticketUrl,
            @Value("${purchase.ticket-limit}") int ticketLimit,
            @Value("${purchase.hold-ttl-seconds}") int holdTtl) {
        this.jdbc=jdbc; this.mapper=mapper; this.transactions=transactions; this.catalogUrl=catalogUrl;
        this.inventoryUrl=inventoryUrl; this.paymentUrl=paymentUrl; this.ticketUrl=ticketUrl;
        this.ticketLimit=ticketLimit; this.holdTtl=holdTtl; this.http=RestClient.builder().build();
    }

    @PostMapping
    public ResponseEntity<PurchaseView> create(@RequestHeader("X-User-Id") UUID customerId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value="X-Correlation-Id", required=false) String correlationId,
            @Valid @RequestBody PurchaseRequest request) {
        String trace = correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId;
        if (idempotencyKey.isBlank() || idempotencyKey.length()>120) throw problem(HttpStatus.BAD_REQUEST,"Invalid idempotency key");
        String fingerprint = hash(customerId+"|"+request.toString());
        UUID existing = findByKey(idempotencyKey, fingerprint);
        if (existing != null) return response(run(existing, trace), HttpStatus.OK);

        JsonNode event = callGet(catalogUrl+"/api/events/"+request.eventId(), trace);
        if (!event.path("id").asText().equals(request.eventId().toString())) throw problem(HttpStatus.NOT_FOUND,"Published event not found");
        Map<UUID,JsonNode> sectors=new HashMap<>(); event.path("sectors").forEach(s -> sectors.put(UUID.fromString(s.path("id").asText()),s));
        boolean numbered=request.items().stream().anyMatch(item -> item.seatId()!=null);
        JsonNode availability=numbered?callGet(inventoryUrl+"/api/inventory/events/"+request.eventId()+"/availability",trace):null;
        List<PreparedItem> prepared=new ArrayList<>(); BigDecimal total=BigDecimal.ZERO; int quantity=0;
        for (ItemRequest item:request.items()) {
            JsonNode sector=sectors.get(item.sectorId());
            if (sector==null || (item.seatId()==null)==(item.quantity()==null)) throw problem(HttpStatus.BAD_REQUEST,"Invalid sector or item selection");
            int count=item.seatId()!=null?1:item.quantity(); quantity+=count;
            BigDecimal price=sector.path("price").decimalValue(); total=total.add(price.multiply(BigDecimal.valueOf(count)));
            String row=item.rowLabel(); Integer seatNumber=item.seatNumber();
            if(item.seatId()!=null) {
                JsonNode found=null;
                for(JsonNode s:availability.path("sectors")) if(s.path("sectorId").asText().equals(item.sectorId().toString()))
                    for(JsonNode seat:s.path("seats")) if(seat.path("seatId").asText().equals(item.seatId().toString())) found=seat;
                if(found==null) throw problem(HttpStatus.BAD_REQUEST,"Seat does not belong to the selected sector");
                row=found.path("row").asText(); seatNumber=found.path("number").asInt();
            }
            prepared.add(new PreparedItem(UUID.randomUUID(),item.sectorId(),sector.path("name").asText(),price,count,item.seatId(),row,seatNumber));
        }
        UUID orderId=UUID.randomUUID(); final BigDecimal orderTotal=total; final int orderQuantity=quantity;
        try { transactions.executeWithoutResult(status -> {
            lock("idempotency|"+idempotencyKey);
            List<Map.Entry<UUID,String>> concurrent=jdbc.query("SELECT id,request_fingerprint FROM purchase_orders WHERE idempotency_key=?",(r,n)->Map.entry(r.getObject(1,UUID.class),r.getString(2)),idempotencyKey);
            if(!concurrent.isEmpty()) {
                if(!concurrent.get(0).getValue().equals(fingerprint)) throw problem(HttpStatus.CONFLICT,"Idempotency key reused with different request");
                throw new ExistingOrder(concurrent.get(0).getKey());
            }
            lock(customerId+"|"+request.eventId());
            Integer used=jdbc.queryForObject("SELECT COALESCE(SUM(i.quantity),0) FROM purchase_order_items i JOIN purchase_orders o ON o.id=i.order_id WHERE o.customer_id=? AND o.event_id=? AND o.status IN ('PROCESSING','PAID','RECOVERY_REQUIRED')",Integer.class,customerId,request.eventId());
            if (used+orderQuantity>ticketLimit) throw problem(HttpStatus.CONFLICT,"Per-customer event ticket limit exceeded");
            jdbc.update("INSERT INTO purchase_orders(id,customer_id,event_id,event_name,event_starts_at,venue_name,venue_city,payment_method,payment_outcome,status,idempotency_key,request_fingerprint,total) VALUES (?,?,?,?,?,?,?,?,?,'PROCESSING',?,?,?)",
                    orderId,customerId,request.eventId(),event.path("name").asText(),OffsetDateTime.parse(event.path("startsAt").asText()),event.path("venueName").asText(),event.path("city").asText(),request.paymentMethod(),request.paymentOutcome(),idempotencyKey,fingerprint,orderTotal);
            for(PreparedItem p:prepared) jdbc.update("INSERT INTO purchase_order_items(id,order_id,sector_id,sector_name,unit_price,quantity,seat_id,row_label,seat_number) VALUES(?,?,?,?,?,?,?,?,?)",p.id,orderId,p.sectorId,p.sectorName,p.price,p.quantity,p.seatId,p.row,p.seat);
        }); } catch(ExistingOrder duplicate) { return response(run(duplicate.orderId,trace),HttpStatus.OK); }
        return response(run(orderId,trace,request.paymentOutcome()), HttpStatus.CREATED);
    }

    @GetMapping("/{orderId}")
    public PurchaseView get(@RequestHeader("X-User-Id") UUID customerId,@PathVariable UUID orderId) {
        try { jdbc.queryForObject("SELECT id FROM purchase_orders WHERE id=? AND customer_id=?",UUID.class,orderId,customerId); }
        catch (Exception ex) { throw problem(HttpStatus.NOT_FOUND,"Order not found"); }
        return view(orderId);
    }

    private UUID findByKey(String key,String fingerprint) {
        var rows=jdbc.query("SELECT id,request_fingerprint FROM purchase_orders WHERE idempotency_key=?",(r,n)->Map.entry(r.getObject(1,UUID.class),r.getString(2)),key);
        if(rows.isEmpty()) return null;
        if(!rows.get(0).getValue().equals(fingerprint)) throw problem(HttpStatus.CONFLICT,"Idempotency key reused with different request");
        return rows.get(0).getKey();
    }

    private PurchaseView run(UUID orderId,String trace) { return run(orderId,trace,"APPROVED"); }
    private PurchaseView run(UUID orderId,String trace,String outcome) {
        JsonNode o=jdbc.queryForObject("SELECT to_jsonb(purchase_orders) FROM purchase_orders WHERE id=?",(r,n)->read(r.getString(1)),orderId);
        String state=o.path("status").asText(); if(state.equals("PAID")||state.equals("REJECTED")) return view(orderId);
        try {
            JsonNode items=mapper.valueToTree(jdbc.query("SELECT * FROM purchase_order_items WHERE order_id=? ORDER BY id",(r,n)->{Map<String,Object> m=new HashMap<>();m.put("id",r.getObject("id",UUID.class));m.put("sectorId",r.getObject("sector_id",UUID.class));m.put("quantity",r.getInt("quantity"));m.put("seatId",r.getObject("seat_id",UUID.class));return m;},orderId));
            List<Map<String,Object>> reserveItems=new ArrayList<>(); items.forEach(i->{Map<String,Object> x=new LinkedHashMap<>();x.put("sectorId",i.path("sectorId").asText()); if(!i.path("seatId").isMissingNode()&&!i.path("seatId").isNull())x.put("seatId",i.path("seatId").asText());else x.put("quantity",i.path("quantity").asInt());reserveItems.add(x);});
            JsonNode reservation=call(inventoryUrl+"/internal/reservations",trace,Map.of("orderId",orderId,"eventId",o.path("event_id").asText(),"ttlSeconds",holdTtl,"items",reserveItems));
            UUID reservationId=UUID.fromString(reservation.path("reservationId").asText());
            JsonNode payment=call(paymentUrl+"/internal/payments",trace,Map.of("orderId",orderId,"amount",o.path("total").decimalValue(),"currency","ARS","paymentMethod",o.path("payment_method").asText(),"idempotencyKey",orderId.toString(),"outcome",o.path("payment_outcome").asText(outcome)));
            if(!payment.path("status").asText().equals("APPROVED")) { call(inventoryUrl+"/internal/reservations/"+reservationId+"/release",trace,Map.of()); jdbc.update("UPDATE purchase_orders SET status='REJECTED',payment_id=? WHERE id=?",UUID.fromString(payment.path("paymentId").asText()),orderId); return view(orderId); }
            jdbc.update("UPDATE purchase_orders SET status='PAYMENT_APPROVED',payment_id=? WHERE id=?",UUID.fromString(payment.path("paymentId").asText()),orderId);
            call(inventoryUrl+"/internal/reservations/"+reservationId+"/confirm",trace,Map.of());
            jdbc.update("UPDATE purchase_orders SET status='INVENTORY_CONFIRMED' WHERE id=?",orderId);
            List<Map<String,Object>> lines=jdbc.query("SELECT * FROM purchase_order_items WHERE order_id=? ORDER BY id",(r,n)->{Map<String,Object> m=new HashMap<>();m.put("id",r.getObject("id",UUID.class));m.put("sectorId",r.getObject("sector_id",UUID.class));m.put("sectorName",r.getString("sector_name"));m.put("quantity",r.getInt("quantity"));m.put("seatId",r.getObject("seat_id",UUID.class));m.put("row",r.getString("row_label"));m.put("seat",r.getObject("seat_number"));return m;},orderId);
            for(Map<String,Object> line:lines) {
                List<Map<String,Object>> units=new ArrayList<>(); int q=(int)line.get("quantity"); for(int n=1;n<=q;n++){Map<String,Object> unit=new LinkedHashMap<>();unit.put("unitNumber",n);unit.put("rowLabel",line.get("row"));unit.put("seatNumber",line.get("seat"));units.add(unit);}
                Map<String,Object> ticketBody=Map.of("customerId",o.path("customer_id").asText(),"eventId",o.path("event_id").asText(),"eventName",o.path("event_name").asText(),"eventStartsAt",o.path("event_starts_at").asText(),"venueName",o.path("venue_name").asText(),"venueCity",o.path("venue_city").asText(),"sectorName",line.get("sectorName"),"tickets",units);
                JsonNode tickets=call(ticketUrl+"/internal/orders/"+orderId+"/items/"+line.get("id")+"/tickets",trace,ticketBody);
                jdbc.update("UPDATE purchase_order_items SET ticket_payload=? WHERE id=?",tickets.toString(),line.get("id"));
            }
            jdbc.update("UPDATE purchase_orders SET status='PAID' WHERE id=?",orderId);
        } catch(Exception e) {
            String current=jdbc.queryForObject("SELECT status FROM purchase_orders WHERE id=?",String.class,orderId);
            if("PAYMENT_APPROVED".equals(current)) {
                UUID paymentId=jdbc.queryForObject("SELECT payment_id FROM purchase_orders WHERE id=?",UUID.class,orderId);
                try { call(paymentUrl+"/internal/payments/"+paymentId+"/refunds",trace,Map.of("idempotencyKey",orderId+"-expiry-refund")); }
                catch(Exception ignored) { /* Persisted recovery state allows the same idempotent refund to be retried. */ }
            }
            jdbc.update("UPDATE purchase_orders SET status='RECOVERY_REQUIRED' WHERE id=? AND status NOT IN ('PAID','REJECTED')",orderId);
        }
        return view(orderId);
    }

    private PurchaseView view(UUID id) {
        JsonNode order=jdbc.queryForObject("SELECT to_jsonb(purchase_orders) FROM purchase_orders WHERE id=?",(r,n)->read(r.getString(1)),id);
        List<ItemView> items=jdbc.query("SELECT id,sector_name,unit_price,quantity,seat_id,ticket_payload FROM purchase_order_items WHERE order_id=? ORDER BY id",(r,n)->{JsonNode t=r.getString("ticket_payload")==null?null:read(r.getString("ticket_payload"));return new ItemView(r.getObject("id",UUID.class),r.getString("sector_name"),r.getBigDecimal("unit_price"),r.getInt("quantity"),r.getObject("seat_id",UUID.class),t);},id);
        return new PurchaseView(id,order.path("event_id").asText(),order.path("status").asText(),order.path("total").decimalValue(),items);
    }
    private JsonNode call(String url,String trace) { return call(url,trace,null); }
    private JsonNode callGet(String url,String trace) { try{return read(http.get().uri(url).header("X-Correlation-Id",trace).retrieve().body(String.class));}catch(Exception ex){throw new IllegalStateException("Downstream request failed: "+url,ex);} }
    private JsonNode call(String url,String trace,Object body) {
        try { var req=http.post().uri(url).header("X-Correlation-Id",trace); if(body==null) return mapper.readTree(req.retrieve().body(String.class)); return mapper.readTree(req.body(body).retrieve().body(String.class)); }
        catch(Exception ex) { throw new IllegalStateException("Downstream request failed: "+url,ex); }
    }
    private ResponseStatusException problem(HttpStatus status,String message){return new ResponseStatusException(status,message);}
    private void lock(String key){jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection->{try(PreparedStatement statement=connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))")){statement.setString(1,key);statement.execute();}return null;});}
    private ResponseEntity<PurchaseView> response(PurchaseView view,HttpStatus completed){return ResponseEntity.status("RECOVERY_REQUIRED".equals(view.status())?HttpStatus.ACCEPTED:completed).body(view);}
    private JsonNode read(String json){try{return mapper.readTree(json);}catch(Exception e){throw new IllegalStateException(e);}}
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    public record PurchaseRequest(@NotNull UUID eventId,@NotEmpty List<@Valid ItemRequest> items,@NotNull String paymentMethod,@Pattern(regexp="APPROVED|REJECTED") String paymentOutcome) { public PurchaseRequest {if(paymentOutcome==null)paymentOutcome="APPROVED";} }
    public record ItemRequest(@NotNull UUID sectorId,UUID seatId,@Positive Integer quantity,String rowLabel,Integer seatNumber){}
    private record PreparedItem(UUID id,UUID sectorId,String sectorName,BigDecimal price,int quantity,UUID seatId,String row,Integer seat){}
    public record PurchaseView(UUID orderId,String eventId,String status,BigDecimal total,List<ItemView> items){}
    public record ItemView(UUID orderItemId,String sectorName,BigDecimal unitPrice,int quantity,UUID seatId,JsonNode tickets){}
    private static class ExistingOrder extends RuntimeException { private final UUID orderId; private ExistingOrder(UUID orderId){this.orderId=orderId;} }
}
