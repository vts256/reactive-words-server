package com.vings.words.routes;

import com.vings.words.handlers.CategoryHandler;
import com.vings.words.handlers.DictionaryHandler;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA;
import static org.springframework.web.reactive.function.BodyInserters.fromObject;
import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.nest;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@Component
public class WordsRoutes {

    private DictionaryHandler dictionaryHandler;
    private CategoryHandler categoryHandler;

    public WordsRoutes(DictionaryHandler dictionaryHandler, CategoryHandler categoryHandler) {
        this.dictionaryHandler = dictionaryHandler;
        this.categoryHandler = categoryHandler;
    }

    public RouterFunction<ServerResponse> routingFunction() {
        return baseRoutes()
                .and(dictionaryRoutes())
                .and(categoryRoutes());
    }

    private RouterFunction<ServerResponse> baseRoutes() {
        return route(GET("/"), request -> ok().body(fromObject("Hello, from reactive handler;)")));
    }

    private RouterFunction<ServerResponse> dictionaryRoutes() {
        return nest(path("/dictionary"),
                nest(accept(APPLICATION_JSON, APPLICATION_FORM_URLENCODED, MULTIPART_FORM_DATA),
                        route(GET("/{user}/{category}"), dictionaryHandler::getWords)
                                .andRoute(GET("/{user}/{category}/{learned}"), dictionaryHandler::getWordsByLearnedFilter)
                                .andRoute(POST("/{user}"), dictionaryHandler::save)
                                .andRoute(PATCH("/{user}/{category}/{word}"), dictionaryHandler::updateWord)
                                .andRoute(DELETE("/{user}/{category}/{word}"), dictionaryHandler::deleteWord)
                                .andRoute(DELETE("/{user}/{category}"), dictionaryHandler::deleteCategory)
                                .andRoute(DELETE("/{user}/{category}/{word}/{translation}"), dictionaryHandler::deleteTranslation)
                ));
    }

    private RouterFunction<ServerResponse> categoryRoutes() {
        return nest(path("/category"),
                nest(accept(APPLICATION_JSON, APPLICATION_FORM_URLENCODED, MULTIPART_FORM_DATA),
                        route(GET("/{user}"), categoryHandler::get)
                                .andRoute(POST("/{user}"), categoryHandler::create)
                                .andRoute(PATCH("/{user}/{title}/{newTitle}"), categoryHandler::update)
                                .andRoute(DELETE("/{user}/{title}"), categoryHandler::delete)
                ));
    }
}
