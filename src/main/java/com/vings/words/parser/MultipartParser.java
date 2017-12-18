package com.vings.words.parser;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.StringDecoder;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collections;

@Component
public class MultipartParser {

    public Mono<String> parse(Part part, Class partClass) {
        return part == null ? Mono.empty() : StringDecoder.textPlainOnly(false).decodeToMono(part.content(),
                ResolvableType.forClass(partClass), MediaType.TEXT_PLAIN,
                Collections.emptyMap());
    }
}
