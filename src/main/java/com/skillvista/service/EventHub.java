package com.skillvista.service;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Pushes "something changed" events to logged-in browsers so pages update in real time. */
@Component
public class EventHub {
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String gmail) {
        String key = gmail.toLowerCase();
        SseEmitter emitter = new SseEmitter(0L); // never time out
        List<SseEmitter> list = emitters.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        emitter.onCompletion(() -> list.remove(emitter));
        emitter.onTimeout(() -> list.remove(emitter));
        emitter.onError(e -> list.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (Exception e) {
            list.remove(emitter);
        }
        return emitter;
    }

    public void publish(String... gmails) {
        for (String gmail : gmails) {
            if (gmail == null) continue;
            List<SseEmitter> list = emitters.get(gmail.toLowerCase());
            if (list == null) continue;
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().name("update").data("1"));
                } catch (Exception e) {
                    list.remove(emitter);
                }
            }
        }
    }
}
