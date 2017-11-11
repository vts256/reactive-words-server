package com.vings.words.handlers;

import com.vings.words.model.Word;
import com.vings.words.repository.WordsRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.springframework.web.reactive.function.BodyExtractors.toFormData;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@Component
public class DictionaryHandler {
    private static final String ID = "id";

    private WordsRepository wordsRepository;

    public DictionaryHandler(WordsRepository wordsRepository) {
        this.wordsRepository = wordsRepository;
    }

    public Mono<ServerResponse> get(ServerRequest servletRequest) {
        String id = servletRequest.pathVariable(ID);
        Mono<Word> monoWord = wordsRepository.findById(UUID.fromString(id));

        return ok().body(monoWord, Word.class);
    }

    public Mono<ServerResponse> save(ServerRequest serverRequest) {
        return serverRequest.body(toFormData())
                .flatMap(formData -> ok().body(wordsRepository.save(new Word("word", "translation")), Word.class))
                .switchIfEmpty(ServerResponse.badRequest()
                        .build());
    }
}
