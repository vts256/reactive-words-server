package com.vings.words.repository;

import com.vings.words.model.Word;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Repository
public interface WordsRepository extends ReactiveCrudRepository<Word, UUID> {

    Flux<Word> findByCategory(String category);
}
