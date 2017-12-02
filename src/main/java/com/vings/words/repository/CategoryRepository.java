package com.vings.words.repository;

import com.vings.words.model.Category;
import com.vings.words.model.Word;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface CategoryRepository extends ReactiveCassandraRepository<Category, String> {

    Flux<Category> findByUser(String user);

    Mono<Category> findByUserAndTitle(String user, String title);
}
