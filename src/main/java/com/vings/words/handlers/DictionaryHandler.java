package com.vings.words.handlers;

import com.vings.words.model.Word;
import com.vings.words.repository.WordsRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

import static org.springframework.web.reactive.function.BodyInserters.fromObject;
import static org.springframework.web.reactive.function.server.ServerResponse.*;

@Component
public class DictionaryHandler {
    private static final String ID = "id";
    private static final String CATEGORY = "category";

    private WordsRepository wordsRepository;

    public DictionaryHandler(WordsRepository wordsRepository) {
        this.wordsRepository = wordsRepository;
    }

    public Mono<ServerResponse> getWordById(ServerRequest servletRequest) {
        UUID id = UUID.fromString(servletRequest.pathVariable(ID));
        Mono<Word> wordMono = wordsRepository.findById(id);
        return wordMono.flatMap(word -> ok().body(fromObject(word)))
                .switchIfEmpty(notFound().build());
    }

    public Mono<ServerResponse> getWordsByCategory(ServerRequest servletRequest) {
        Flux<Word> wordsFlux = wordsRepository.findByCategory(servletRequest.pathVariable(CATEGORY));
        return wordsFlux.collectList().flatMap(data -> {
            if (data.isEmpty()) {
                return notFound().build();
            } else {
                return ok().body(fromObject(data));
            }
        });
    }

    public Mono<ServerResponse> save(ServerRequest serverRequest) {
        return serverRequest.bodyToMono(Word.class)
                .filter(word -> word.getWord() != null && word.getTranslation() != null)
                .flatMap(word -> ok().body(wordsRepository.save(word), Word.class))
                .switchIfEmpty(badRequest().body(Mono.just("Parameters wasn't specified correctly"), String.class));
    }

    public Mono<ServerResponse> delete(ServerRequest serverRequest) {
        UUID id = UUID.fromString(serverRequest.pathVariable(ID));
        return wordsRepository.deleteById(id).flatMap(data -> ok().build());
    }
}
