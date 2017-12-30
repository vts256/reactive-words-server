package com.vings.words.servlet;

import com.datastax.driver.core.utils.UUIDs;
import com.vings.words.WordsApplication;
import com.vings.words.model.Word;
import com.vings.words.model.quiz.Sprint;
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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = WordsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QuizServletTest {
    @LocalServerPort
    private int port;

    @Autowired
    private WordsRepository wordsRepository;

    private WebTestClient client;

    private String user = "user1";
    private UUID category1 = UUIDs.random();
    private UUID category2 = UUIDs.random();
    private List<Word> words = Arrays.asList(new Word.WordBuilder(user, category1, "Reactive").withAnswers(16).withTranslation(new HashSet<>(singletonList("Реактив"))).build(),
            new Word.WordBuilder(user, category1, "Core").withAnswers(100).withTranslation(new HashSet<>(singletonList("Основа"))).build(),
            new Word.WordBuilder(user, category1, "Tangos").withAnswers(95).withTranslation(new HashSet<>(asList("Тангос"))).build(),
            new Word.WordBuilder(user, category2, "Tango").withAnswers(95).withTranslation(new HashSet<>(asList("Танго", "Супер"))).build());

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
    void getSpringQuestions() {
        wordsRepository.saveAll(words).blockLast();

        List<Sprint> sprints = client.get().uri("/quiz/sprint/{user}/{category}/{page}/{offset}", user, category1, 0, 10).exchange()
                .expectStatus().isOk()
                .expectBodyList(Sprint.class).returnResult().getResponseBody();

        assertThat(sprints).hasSize(2);
        assertThat(sprints).extracting("word").containsExactly("Reactive", "Tangos");
    }
}