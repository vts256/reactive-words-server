package com.vings.words.servlet;

import com.vings.words.handlers.DictionaryExceptionHandler;
import com.vings.words.routes.WordsRoutes;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ServletHttpHandlerAdapter;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;

import static org.springframework.web.reactive.function.server.RouterFunctions.toHttpHandler;

@Configuration
public class WordsServlet {

    @Bean
    public ServletRegistrationBean servletRegistrationBean(WordsRoutes wordsRoutes, DictionaryExceptionHandler dictionaryExceptionHandler) throws Exception {
        HttpHandler httpHandler = WebHttpHandlerBuilder
                .webHandler((WebHandler) toHttpHandler(wordsRoutes.routingFunction()))
                .exceptionHandler(dictionaryExceptionHandler)
                .build();
        ServletRegistrationBean registrationBean = new ServletRegistrationBean<>(new ServletHttpHandlerAdapter(httpHandler), "/");
        registrationBean.setLoadOnStartup(1);
        registrationBean.setAsyncSupported(true);
        return registrationBean;
    }

}
