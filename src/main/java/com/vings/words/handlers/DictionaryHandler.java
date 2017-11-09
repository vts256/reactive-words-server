package com.vings.words.handlers;

import com.vings.words.model.Word;
import com.vings.words.repository.WordsRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@Component
public class DictionaryHandler {
    private static final String ID = "id";

    private WordsRepository wordsRepository;

    public DictionaryHandler(WordsRepository wordsRepository) {
        this.wordsRepository = wordsRepository;
    }

    public Mono<ServerResponse> getWord(ServerRequest servletRequest) {
        String id = servletRequest.pathVariable(ID);
        Mono<Word> monoWord = wordsRepository.findById(Integer.parseInt(id));

        return ok().body(monoWord, Word.class);
    }
}
