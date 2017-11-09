package com.vings.words.repository;

import com.vings.words.model.Word;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WordsRepository extends ReactiveCrudRepository<Word, Integer> {
}
