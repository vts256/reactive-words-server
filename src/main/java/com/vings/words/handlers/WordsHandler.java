package com.vings.words.handlers;

import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface WordsHandler<T extends ServerResponse> {
    Mono<T> handle(ServerRequest request);
}
