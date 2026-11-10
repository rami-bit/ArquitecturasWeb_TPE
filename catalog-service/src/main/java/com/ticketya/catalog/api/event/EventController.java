@RestController
@RequestMapping("/events")
public class EventController {

    @Autowired
    private EventService eventService;

    @PostMapping("/{eventId}/cancel")
    public ResponseEntity<Event> cancelEvent(@PathVariable UUID eventId) {
        Event event = eventService.cancelEvent(eventId);
        return ResponseEntity.ok(event);
    }
}
