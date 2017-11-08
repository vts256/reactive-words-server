package com.vings.words.handlers;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.BodyInserters.fromObject;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@Component
public class WordsHandler {

    public RouterFunction<ServerResponse> routingFunction() {
        return route(GET("/"), request -> ok().body(fromObject("Hello, from reactive handler;)")));
    }
}
