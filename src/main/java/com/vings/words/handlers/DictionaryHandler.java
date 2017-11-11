package com.vings.words.handlers;

import com.vings.words.model.Word;
import com.vings.words.repository.WordsRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.springframework.web.reactive.function.server.ServerResponse.badRequest;
import static org.springframework.web.reactive.function.server.ServerResponse.noContent;
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
        return ok().body(wordsRepository.findById(UUID.fromString(id)), Word.class)
                .switchIfEmpty(noContent().build());
    }

    public Mono<ServerResponse> save(ServerRequest serverRequest) {
        return serverRequest.bodyToMono(Word.class)
                .filter(word -> word.getWord() != null && word.getTranslation() != null)
                .flatMap(word -> ok().body(wordsRepository.save(word), Word.class))
                .switchIfEmpty(badRequest().body(Mono.just("Parameters wasn't specified correctly"), String.class));
    }

    public Mono<ServerResponse> delete(ServerRequest serverRequest) {
        String id = serverRequest.pathVariable(ID);
        wordsRepository.deleteById(UUID.fromString(id));
        return ok().build();
    }
}
