package com.vings.words.repository;

import com.vings.words.model.Word;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;

@Repository
public interface WordsRepository extends ReactiveCassandraRepository<Word, String> {

    Flux<Word> findByUserAndCategory(String user, UUID category);

    Mono<Word> findByUserAndCategoryAndWord(String user, UUID category, String word);

    Mono<Word> findOneByUserAndCategory(String user, UUID category);

    @Query("UPDATE word SET translation = translation + :translation WHERE user = :user AND category = :category AND word = :word;")
    Mono<Word> updateTranslation(@Param("user") String user, @Param("category") UUID category, @Param("word") String word, @Param("translation") Set<String> translation);

    @Query("UPDATE word SET translation = translation - :translation WHERE user = :user AND category = :category AND word = :word;")
    Mono<Word> deleteTranslation(@Param("user") String user, @Param("category") UUID category, @Param("word") String word, @Param("translation") Set<String> translation);

    @Query("DELETE FROM word WHERE user = :user AND category = :category;")
    Flux<Word> deleteByUserAndCategory(@Param("user") String user, @Param("category") UUID category);

}
