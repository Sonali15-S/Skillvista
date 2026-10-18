package com.skillvista.web;

import com.skillvista.model.Account;
import com.skillvista.service.AuthService;
import com.skillvista.service.EventHub;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class StreamController {
    private final AuthService auth;
    private final EventHub hub;

    public StreamController(AuthService auth, EventHub hub) {
        this.auth = auth;
        this.hub = hub;
    }

    /** Server-Sent Events stream: the page listens here and refreshes itself when data changes. */
    @GetMapping(value = "/api/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpServletRequest req) {
        Account a = auth.require(req, null);
        return hub.subscribe(a.gmail);
    }
}
