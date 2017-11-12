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

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = WordsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WordsServletTest {

    @LocalServerPort
    private int port;

    private Word word = new Word("Reactive", "Це реактив");

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
    void getFromEmptyDictionary() {
        client.get().uri("/dictionary/" + UUIDs.timeBased()).exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void getWordById() throws Exception {

        wordsRepository.save(word).block();

        client.get().uri("/dictionary/{0}", word.getId()).exchange()
                .expectStatus().isOk()
                .expectBody(Word.class).isEqualTo(word);
    }

    @Test
    void getWordByInvalidUUID() throws Exception {

        int notValidUUID = 1;

        client.get().uri("/dictionary/{0}", notValidUUID).exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void saveWord() throws Exception {

        client.post().uri("/dictionary/").body(BodyInserters.fromObject(word)).exchange()
                .expectStatus().isOk()
                .expectBody(Word.class).isEqualTo(word);
    }

    @Test
    void deleteWord() throws Exception {

        wordsRepository.save(word).block();

        client.delete().uri("/dictionary/{0}", word.getId()).exchange().expectStatus().isOk();

        assertThat(wordsRepository.count().block()).isEqualTo(0);
    }
}