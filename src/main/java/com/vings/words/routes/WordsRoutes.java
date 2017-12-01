package com.vings.words.routes;

import com.vings.words.handlers.CategoryHandler;
import com.vings.words.handlers.DictionaryHandler;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

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
                nest(accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED),
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
                nest(accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED),
                        route(GET("/{user}"), categoryHandler::getCategories)
                        .andRoute(POST("/"), categoryHandler::createCategory)
                ));
    }
}
