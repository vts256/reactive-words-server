package com.vings.words.routes;

import com.vings.words.handlers.CategoryHandler;
import com.vings.words.handlers.DictionaryHandler;
import com.vings.words.handlers.QuizHandler;
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

    private final DictionaryHandler dictionaryHandler;
    private final CategoryHandler categoryHandler;
    private final QuizHandler quizHandler;

    public WordsRoutes(DictionaryHandler dictionaryHandler, CategoryHandler categoryHandler, QuizHandler quizHandler) {
        this.dictionaryHandler = dictionaryHandler;
        this.categoryHandler = categoryHandler;
        this.quizHandler = quizHandler;
    }

    public RouterFunction<ServerResponse> routingFunction() {
        return baseRoutes()
                .and(dictionaryRoutes())
                .and(categoryRoutes())
                .and(quizRoutes());
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
                                .andRoute(POST("/{user}/{category}/{word}/image"), dictionaryHandler::updateImage)
                                .andRoute(DELETE("/{user}/{category}/{word}"), dictionaryHandler::deleteWord)
                                .andRoute(DELETE("/{user}/{category}"), dictionaryHandler::deleteCategory)
                                .andRoute(PATCH("/{user}/{category}/{word}/add/{translation}"), dictionaryHandler::addTranslation)
                                .andRoute(DELETE("/{user}/{category}/{word}/delete/{translation}"), dictionaryHandler::deleteTranslation)
                ));
    }

    private RouterFunction<ServerResponse> categoryRoutes() {
        return nest(path("/category"),
                nest(accept(APPLICATION_JSON, APPLICATION_FORM_URLENCODED, MULTIPART_FORM_DATA),
                        route(GET("/{user}"), categoryHandler::get)
                                .andRoute(POST("/{user}"), categoryHandler::create)
                                .andRoute(PATCH("/{user}/{title}/image"), categoryHandler::updateImage)
                                .andRoute(PATCH("/{user}/{title}/{newTitle}"), categoryHandler::update)
                                .andRoute(DELETE("/{user}/{title}"), categoryHandler::delete)
                ));
    }

    private RouterFunction<ServerResponse> quizRoutes() {
        return nest(path("/quiz"),
                nest(accept(APPLICATION_JSON, APPLICATION_FORM_URLENCODED),
                        route(GET("/sprint/{user}/{category}/{page}/{offset}"), quizHandler::sprint)
                                .andRoute(GET("/crossword/{user}/{category}/{page}/{offset}"), quizHandler::crossword)
                                .andRoute(GET("/guess/{user}/{category}/{page}/{offset}"), quizHandler::guess)
                ));
    }
}
