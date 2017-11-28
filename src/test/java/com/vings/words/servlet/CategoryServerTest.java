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
    void getWordsByCategory() {

        categoryRepository.saveAll(Arrays.asList(firstCategory, secondCategory)).blockLast();

        client.get().uri("/category/{0}", user, firstCategory).exchange()
                .expectStatus().isOk()
                .expectBodyList(Category.class).hasSize(2).contains(firstCategory, secondCategory);
    }

}
