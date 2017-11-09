package com.vings.words.routes;

import com.vings.words.handlers.DictionaryHandler;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.BodyInserters.fromObject;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RequestPredicates.path;
import static org.springframework.web.reactive.function.server.RouterFunctions.nest;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@Component
public class WordsRoutes {

    private DictionaryHandler dictionaryHandler;

    public WordsRoutes(DictionaryHandler dictionaryHandler) {
        this.dictionaryHandler = dictionaryHandler;
    }

    public RouterFunction<ServerResponse> routingFunction() {
        return baseRoutes()
                .and(dictionaryRoutes());
    }

    private RouterFunction<ServerResponse> baseRoutes() {
        return route(GET("/"), request -> ok().body(fromObject("Hello, from reactive handler;)")));
    }

    private RouterFunction<ServerResponse> dictionaryRoutes() {
        return nest(path("/dictionary"),
                nest(accept(MediaType.APPLICATION_JSON),
                        route(GET("/{user}/{category}"), dictionaryHandler::getWordsByCategory)));
    }
}
