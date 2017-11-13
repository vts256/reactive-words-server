package com.vings.words.servlet;

import com.datastax.driver.core.utils.UUIDs;
import com.vings.words.WordsApplication;
import com.vings.words.model.Word;
import com.vings.words.repository.WordsRepository;
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

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = WordsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WordsServletTest {

    @LocalServerPort
    private int port;

    private Word first = new Word("Cool", "Reactive", "Реактив");
    private Word second = new Word("Cool", "Core", "Основа");

    @Autowired
    private WordsRepository wordsRepository;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient
                .bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @AfterEach
    void tearDown() {
        wordsRepository.deleteAll().block();
    }

    @Test
    void getHelloFromBaseUrl() {
        client.get().uri("/").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("Hello, from reactive handler;)");
    }

    @Test
    void getWordByIdFromEmptyDictionary() {
        client.get().uri("/dictionary/" + UUIDs.timeBased()).exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void getWordById() {

        wordsRepository.save(first).block();

        client.get().uri("/dictionary/{0}", first.getId()).exchange()
                .expectStatus().isOk()
                .expectBody(Word.class).isEqualTo(first);
    }

    @Test
    void getWordByInvalidUUID() {

        int notValidUUID = 1;

        client.get().uri("/dictionary/{0}", notValidUUID).exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void getWordsByCategory() {
        wordsRepository.saveAll(Arrays.asList(first, second)).subscribe();

        String category = "Cool";
        client.get().uri("/dictionary/category/{0}", category).exchange()
                .expectStatus().isOk()
                .expectBodyList(Word.class).hasSize(2).contains(first, second);
    }


    @Test
    void getWordsByCategoryFromEmptyDictionary() {

        String category = "notExisted";
        client.get().uri("/dictionary/category/{0}", category).exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void saveWord() {
        client.post().uri("/dictionary/").body(BodyInserters.fromObject(first)).exchange()
                .expectStatus().isOk()
                .expectBody(Word.class).isEqualTo(first);
    }

    @Test
    void deleteWord() {

        wordsRepository.save(first).block();

        client.delete().uri("/dictionary/{0}", first.getId()).exchange().expectStatus().isOk();

        assertThat(wordsRepository.count().block()).isEqualTo(0);
    }
}