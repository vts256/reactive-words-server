package com.vings.words.parser;

import com.vings.words.model.Word;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.multipart.Part;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class MultipartParserTest {

    private final MultipartParser multipartParser = new MultipartParser();

    private final String json = "{\"user\":\"user1\",\"category\":\"4bd70b80-4cd4-4aec-b6ae-de2ad44877cd\",\"json\":\"Reactive\",\"answers\":16,\"translation\":[\"Реактив\"],\"image\":null}";

    @Test
    public void emptyMonoWhenParseNotExistingPart() {
        Mono<String> parsedWord = multipartParser.parse(null, Word.class);

        StepVerifier.create(parsedWord).expectComplete().verify();
    }

    @Test
    public void wordMonoWhenParseWordPart() {

        Mono<String> parsedWord = multipartParser.parse(new StubPart(json), Word.class);

        StepVerifier.create(parsedWord).expectNext(json).expectComplete().verify();
    }

    private class StubPart implements Part {

        private final DefaultDataBufferFactory defaultDataBufferFactory = new DefaultDataBufferFactory();

        private final String word;

        public StubPart(String word) {
            this.word = word;
        }

        @Override
        public String name() {
            return null;
        }

        @Override
        public HttpHeaders headers() {
            return null;
        }

        @Override
        public Flux<DataBuffer> content() {
            byte[] bytes = word.getBytes();
            ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
            buffer.put(bytes);
            buffer.flip();
            return Flux.fromIterable(Arrays.asList(defaultDataBufferFactory.wrap(buffer)));
        }
    }
}