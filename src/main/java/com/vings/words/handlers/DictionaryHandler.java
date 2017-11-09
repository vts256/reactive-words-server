package com.vings.words.handlers;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import static org.springframework.web.reactive.function.BodyInserters.fromObject;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@Component
public class DictionaryHandler {

    public Mono<ServerResponse> getWordsByCategory(ServerRequest servletRequest) {
        return ok().body(fromObject("This is from dictionary"));
    }
}
