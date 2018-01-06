package com.vings.words.servlet;

import com.datastax.driver.core.utils.UUIDs;
import com.vings.words.WordsApplication;
import com.vings.words.model.Word;
import com.vings.words.model.Word.WordBuilder;
import com.vings.words.model.quiz.Guess;
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

import java.util.*;

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
    private List<Word> words = Arrays.asList(new WordBuilder(user, category1, "Reactive").withAnswers(16).withTranslation(new HashSet<>(singletonList("Реактив"))).build(),
            new WordBuilder(user, category1, "Reactor").withAnswers(8).withTranslation(new HashSet<>(singletonList("Реактор"))).build(),
            new WordBuilder(user, category1, "Core").withAnswers(100).withTranslation(new HashSet<>(singletonList("Основа"))).build(),
            new WordBuilder(user, category1, "Tangos").withAnswers(95).withTranslation(new HashSet<>(singletonList("Тангос"))).build(),
            new WordBuilder(user, category2, "Tango").withAnswers(95).withTranslation(new HashSet<>(asList("Танго", "Супер"))).build());

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
    void getSprintQuestions() {
        wordsRepository.saveAll(words).blockLast();

        List<Sprint> sprints = client.get().uri("/quiz/sprint/{user}/{category}/{page}/{offset}", user, category1, 0, 10).exchange()
                .expectStatus().isOk()
                .expectBodyList(Sprint.class).returnResult().getResponseBody();

        assertThat(sprints).hasSize(3);
        assertThat(sprints).extracting("word").containsExactly("Reactive", "Reactor", "Tangos");
        assertThat(sprints).extracting("answer").doesNotContainNull();
    }

    @Test
    void getSprintQuestionsWithOffset() {
        wordsRepository.saveAll(words).blockLast();

        List<Sprint> sprints = client.get().uri("/quiz/sprint/{user}/{category}/{page}/{offset}", user, category1, 1, 2).exchange()
                .expectStatus().isOk()
                .expectBodyList(Sprint.class).returnResult().getResponseBody();

        assertThat(sprints).hasSize(1);
        assertThat(sprints).extracting("word").containsExactly("Tangos");
        assertThat(sprints).extracting("answer").doesNotContainNull();
    }

    @Test
    void badRequestWhenGetSprintQuestionsWithInvalidCategoryUUID() {
        wordsRepository.saveAll(words).blockLast();

        String notValidCategoryUUID = "notValidCategoryUUID";
        client.get().uri("/quiz/sprint/{user}/{category}/{page}/{offset}", user, notValidCategoryUUID, 1, 2).exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void getSprintQuestionsWhenUserDoesNotExist() {
        wordsRepository.saveAll(words).blockLast();

        String notExistingUser = "notExistingUser";
        List<Sprint> sprints = client.get().uri("/quiz/sprint/{user}/{category}/{page}/{offset}", notExistingUser, category1, 1, 2).exchange()
                .expectStatus().isOk()
                .expectBodyList(Sprint.class).returnResult().getResponseBody();

        assertThat(sprints).isEmpty();
    }

    @Test
    void getGuessQuestions() {
        wordsRepository.saveAll(words).blockLast();

        List<Guess> guessList = client.get().uri("/quiz/guess/{user}/{category}/{page}/{offset}", user, category1, 0, 10).exchange()
                .expectStatus().isOk()
                .expectBodyList(Guess.class).returnResult().getResponseBody();

        assertThat(guessList).hasSize(3);
        assertThat(guessList).extracting("word").containsExactly("Reactive", "Reactor", "Tangos");
        assertThat(guessList).extracting("answers").isNotNull();
        assertThat(guessList).extracting("correct").isNotNull();
    }

    @Test
    void getGuessQuestionsWithOffset() {
        wordsRepository.saveAll(words).blockLast();

        List<Guess> guessList = client.get().uri("/quiz/guess/{user}/{category}/{page}/{offset}", user, category1, 1, 2).exchange()
                .expectStatus().isOk()
                .expectBodyList(Guess.class).returnResult().getResponseBody();

        assertThat(guessList).hasSize(1);
        assertThat(guessList).extracting("word").containsExactly("Tangos");
        assertThat(guessList).extracting("answers").isNotNull();
        assertThat(guessList).extracting("correct").isNotNull();
    }

    @Test
    void badRequestWhenGetGuessQuestionsWithInvalidCategoryUUID() {
        wordsRepository.saveAll(words).blockLast();

        String notValidCategoryUUID = "notValidCategoryUUID";
        client.get().uri("/quiz/guess/{user}/{category}/{page}/{offset}", user, notValidCategoryUUID, 1, 2).exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void getGuessQuestionsWhenUserDoesNotExist() {
        wordsRepository.saveAll(words).blockLast();

        String notExistingUser = "notExistingUser";
        List<Guess> guessList = client.get().uri("/quiz/guess/{user}/{category}/{page}/{offset}", notExistingUser, category1, 1, 2).exchange()
                .expectStatus().isOk()
                .expectBodyList(Guess.class).returnResult().getResponseBody();

        assertThat(guessList).isEmpty();
    }

}