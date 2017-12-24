package com.vings.words.parser;

import com.vings.words.model.Word;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.HashSet;
import java.util.UUID;

import static java.util.Collections.singletonList;

class ObjectParserTest {

    private final ObjectParser objectParser = new ObjectParser();
    private final Word word = new Word.WordBuilder("user1", UUID.fromString("c80176b4-22a9-4c8e-af33-cfb0c8832e78"), "Reactive").withAnswers(16).withTranslation(new HashSet<>(singletonList("Реактив"))).build();
    private final String wordData = "{\"user\":\"user1\",\"category\":\"c80176b4-22a9-4c8e-af33-cfb0c8832e78\",\"word\":\"Reactive\",\"answers\":16,\"translation\":[\"Реактив\"],\"image\":null}";

    @Test
    void parseCreateMonoOfSpecifiedClass() throws IOException {
        Mono<Word> mono = objectParser.parse(wordData, Word.class);

        StepVerifier.create(mono).expectNext(this.word).expectComplete().verify();
    }

    @Test
    void parseWithEmptyData() throws IOException {
        String wordData = null;
        Mono<Word> mono = objectParser.parse(wordData, Word.class);

        StepVerifier.create(mono).expectComplete().verify();
    }
}