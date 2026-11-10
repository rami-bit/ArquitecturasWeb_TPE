@Service
public class EventService {

    @Autowired
    private EventRepository eventRepository;

    public Event cancelEvent(UUID eventId) {
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new ApiProblemException(HttpStatus.NOT_FOUND, "Not found", "Event not found"));
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new ApiProblemException(HttpStatus.BAD_REQUEST, "Invalid operation", "Event is already cancelled");
        }
        event.setStatus(EventStatus.CANCELLED);
        return eventRepository.save(event);
    }
}
