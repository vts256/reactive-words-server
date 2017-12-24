package com.vings.words.repository;

import com.vings.words.model.Link;
import com.vings.words.model.Category;
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

    @Query("UPDATE category SET image = :image WHERE user = :user AND title = :title")
    Mono<Category> updateImage(@Param("user") String user, @Param("title") String title, @Param("image") Link image);

    @Query("SELECT count(*) FROM category WHERE user = :user AND title = :title")
    Mono<Integer> hasCategory(@Param("user") String user, @Param("title") String title);
}
