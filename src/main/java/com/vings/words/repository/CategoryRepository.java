package com.vings.words.repository;

import com.vings.words.model.Category;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface CategoryRepository extends ReactiveCassandraRepository<Category, String> {

    Flux<Category> findByUser(String user);
}
