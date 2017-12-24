package com.vings.words.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.IOException;

@Component
public class ObjectParser {

    public <T> Mono<T> parse(String data, Class<T> outputClass) throws IOException {
        if (data == null) {
            return Mono.empty();
        }
        ObjectMapper mapper = new ObjectMapper();
        return Mono.just(mapper.readValue(data, outputClass));
    }
}
