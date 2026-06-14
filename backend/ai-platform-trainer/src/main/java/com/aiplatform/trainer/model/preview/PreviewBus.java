package com.aiplatform.trainer.model.preview;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Process-local pub-sub for live training events. Each job has its own
 * listener list; the SSE controller attaches a listener when a client
 * subscribes and detaches it when the stream closes.
 *
 * <p>This intentionally avoids Spring's {@code ApplicationEventPublisher}
 * because we want fine-grained, per-job fan-out and explicit lifecycle.
 */
@Slf4j
public final class PreviewBus {

    /** Event kinds. */
    public enum EventType { STEP, SAMPLE, METRIC, WARN, DONE }

    /** Event payload pushed to listeners. */
    public record Event(EventType type, int step, double loss, Map<String, Double> metrics,
                        Map<String, Double> antiHallucination, String sample) {}

    private static final Map<String, List<Consumer<Event>>> LISTENERS = new ConcurrentHashMap<>();
    /** Last N events per job, replayed to late subscribers. */
    private static final Map<String, java.util.Deque<Event>> RING = new ConcurrentHashMap<>();
    private static final int RING_SIZE = 64;

    private PreviewBus() {}

    /** Register a listener for {@code jobId}. Returns a handle to detach.
     *  Late subscribers get a replay of the last {@link #RING_SIZE} events first. */
    public static AutoCloseable subscribe(String jobId, Consumer<Event> listener) {
        List<Consumer<Event>> list = LISTENERS.computeIfAbsent(jobId, k -> new CopyOnWriteArrayList<>());
        list.add(listener);
        log.debug("[PREVIEW] subscribe job={} (now {} listeners)", jobId, list.size());
        // Replay last N events to the new listener so a client that connects
        // after the job started still sees the history.
        java.util.Deque<Event> ring = RING.get(jobId);
        if (ring != null) {
            for (Event e : ring) {
                try { listener.accept(e); } catch (Exception ignore) {}
            }
        }
        return () -> {
            List<Consumer<Event>> ls = LISTENERS.get(jobId);
            if (ls != null) ls.remove(listener);
            log.debug("[PREVIEW] unsubscribe job={} (now {} listeners)", jobId, ls == null ? 0 : ls.size());
        };
    }

    public static boolean hasListeners(String jobId) {
        List<Consumer<Event>> ls = LISTENERS.get(jobId);
        return ls != null && !ls.isEmpty();
    }

    /** Push an event to every listener of {@code jobId}. */
    public static void publish(String jobId, Event event) {
        // ring buffer for replay
        java.util.Deque<Event> ring = RING.computeIfAbsent(jobId,
                k -> new java.util.concurrent.ConcurrentLinkedDeque<>());
        ring.addLast(event);
        while (ring.size() > RING_SIZE) ring.pollFirst();
        List<Consumer<Event>> ls = LISTENERS.get(jobId);
        if (ls == null || ls.isEmpty()) return;
        for (Consumer<Event> l : ls) {
            try {
                l.accept(event);
            } catch (Exception e) {
                log.warn("[PREVIEW] listener threw: {}", e.getMessage());
            }
        }
    }

    /** Drop all listeners for a finished job. */
    public static void clear(String jobId) {
        LISTENERS.remove(jobId);
    }
}
