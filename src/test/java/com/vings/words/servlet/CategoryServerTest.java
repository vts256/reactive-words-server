package com.vings.words.servlet;

import com.datastax.driver.core.utils.UUIDs;
import com.vings.words.WordsApplication;
import com.vings.words.model.Category;
import com.vings.words.repository.CategoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.test.StepVerifier;

import java.util.Arrays;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = WordsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CategoryServerTest {
    @LocalServerPort
    private int port;

    @Autowired
    private CategoryRepository categoryRepository;

    private WebTestClient client;

    private String user = "user1";
    private Category firstCategory = new Category(user, "category1", UUIDs.timeBased());
    private Category secondCategory = new Category(user, "category2", UUIDs.timeBased());

    @BeforeEach
    void setUp() {
        client = WebTestClient
                .bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }


    @AfterEach
    void tearDown() {
        categoryRepository.deleteAll().block();
    }

    @Test
    void getUserCategories() {
        categoryRepository.saveAll(Arrays.asList(firstCategory, secondCategory)).blockLast();

        client.get().uri("/category/{0}", user, firstCategory).exchange()
                .expectStatus().isOk()
                .expectBodyList(Category.class).hasSize(2).contains(firstCategory, secondCategory);
    }

    @Test
    void saveCategory() {
        client.post().uri("/category/").body(BodyInserters.fromObject(firstCategory)).exchange()
                .expectStatus().isOk()
                .expectBody(Category.class).isEqualTo(firstCategory);

        StepVerifier.create(categoryRepository.findByUser(user))
                .expectNext(firstCategory).expectComplete().verify();
    }

    @Test
    void deleteCategory() {
        categoryRepository.saveAll(Arrays.asList(firstCategory, secondCategory)).blockLast();

        client.delete().uri("/category/{0}/{1}", user, firstCategory.getTitle()).exchange()
                .expectStatus().isOk();

        StepVerifier.create(categoryRepository.findByUser(user))
                .expectNext(secondCategory).expectComplete().verify();
    }

    @Test
    void deleteNonExistingCategory() {
        categoryRepository.save(secondCategory).block();

        client.delete().uri("/category/{0}/{1}", user, firstCategory.getTitle()).exchange()
                .expectStatus().isNotFound();

        StepVerifier.create(categoryRepository.findByUser(user))
                .expectNext(secondCategory).expectComplete().verify();
    }
}
