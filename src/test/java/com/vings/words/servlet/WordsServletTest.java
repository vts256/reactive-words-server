package com.vings.words.servlet;

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
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.HashSet;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = WordsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WordsServletTest {

    @LocalServerPort
    private int port;

    private String user = "user1";
    private String category1 = "Cool";
    private String category2 = "Huge";
    private Word first = new Word(user, category1, "Reactive", 16, new HashSet<>(Arrays.asList("Реактив")));
    private Word second = new Word(user, category1, "Core", 100, new HashSet<>(Arrays.asList("Основа")));
    private Word third = new Word(user, category2, "Tango", 95, new HashSet<>(Arrays.asList("Танго", "Супер")));

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
    void noContentWhenGetWordsByNotExistingUserAndCategory() {
        String notExistingUser = "notExistingUser";
        String notExistingCategory = "notExistingCategory";
        client.get().uri("/dictionary/{0}/{1}", notExistingUser, notExistingCategory).exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void getWordsByCategory() {
        wordsRepository.saveAll(Arrays.asList(first, second, third)).blockLast();

        client.get().uri("/dictionary/{0}/{1}", user, category1).exchange()
                .expectStatus().isOk()
                .expectBodyList(Word.class).hasSize(2).contains(first, second);
    }

    @Test
    void getLearnedWords() {
        wordsRepository.saveAll(Arrays.asList(first, second)).blockLast();

        client.get().uri("/dictionary/{0}/{1}/{2}", user, category1, true).exchange()
                .expectStatus().isOk()
                .expectBodyList(Word.class).hasSize(1).contains(second);
    }

    @Test
    void getNotLearnedWords() {
        wordsRepository.saveAll(Arrays.asList(first, second)).blockLast();

        client.get().uri("/dictionary/{0}/{1}/{2}", user, category1, false).exchange()
                .expectStatus().isOk()
                .expectBodyList(Word.class).hasSize(1).contains(first);
    }

    @Test
    void getWordsByCategoryFromEmptyDictionary() {

        String category = "notExisted";
        client.get().uri("/dictionary/category/{0}", category).exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void saveWord() {
        client.post().uri("/dictionary/{0}", user).body(BodyInserters.fromObject(first)).exchange()
                .expectStatus().isOk()
                .expectBody(Word.class).isEqualTo(first);

        StepVerifier.create(wordsRepository.findByUserAndCategoryAndWord(user, first.getCategory(), first.getWord()))
                .expectNext(first).expectComplete().verify();
    }

    @Test
    void updateTranslation() {

        wordsRepository.save(first).block();

        client.patch().uri("/dictionary/{0}/{1}/{2}", first.getUser(), first.getCategory(), first.getWord())
                .body(BodyInserters.fromObject("{\"translation\":\"библиотека\"}")).exchange()
                .expectStatus().isOk();

        StepVerifier.create(wordsRepository.findByUserAndCategoryAndWord(user, first.getCategory(), first.getWord()))
                .expectNextMatches(data -> new HashSet<>(Arrays.asList("библиотека", "Реактив")).equals(data.getTranslation())).verifyComplete();
    }

    @Test
    void updateTranslationWithNotSpecifiedTranslationTag() {

        wordsRepository.save(first).block();

        client.patch().uri("/dictionary/{0}/{1}/{2}", first.getUser(), first.getCategory(), first.getWord())
                .body(BodyInserters.fromObject("{\"wrong tag\":\"библиотека\"}")).exchange()
                .expectStatus().isBadRequest();

        StepVerifier.create(wordsRepository.findByUserAndCategoryAndWord(user, first.getCategory(), first.getWord()))
                .expectNextMatches(data -> new HashSet<>(Arrays.asList("Реактив")).equals(data.getTranslation())).verifyComplete();
    }

    @Test
    void updateTranslationWitDuplicateTranslation() {

        wordsRepository.save(first).block();

        client.patch().uri("/dictionary/{0}/{1}/{2}", first.getUser(), first.getCategory(), first.getWord())
                .body(BodyInserters.fromObject("{\"translation\":\"Реактив\"}")).exchange()
                .expectStatus().isOk();

        StepVerifier.create(wordsRepository.findByUserAndCategoryAndWord(user, first.getCategory(), first.getWord()))
                .expectNextMatches(data -> new HashSet<>(Arrays.asList("Реактив")).equals(data.getTranslation())).verifyComplete();
    }

    @Test
    void deleteTranslation() {
        wordsRepository.save(third).block();

        String translation = "Супер";
        client.delete().uri("/dictionary/{0}/{1}/{2}/{3}", third.getUser(), third.getCategory(), third.getWord(), translation)
                .exchange()
                .expectStatus().isOk();

        StepVerifier.create(wordsRepository.findByUserAndCategoryAndWord(user, third.getCategory(), third.getWord()))
                .expectNextMatches(data -> new HashSet<>(Arrays.asList("Танго")).equals(data.getTranslation())).verifyComplete();
    }

    @Test
    void deleteNotExistingTranslation() {
        wordsRepository.save(third).block();

        String translation = "NotExisting";
        client.delete().uri("/dictionary/{0}/{1}/{2}/{3}", third.getUser(), third.getCategory(), third.getWord(), translation)
                .exchange()
                .expectStatus().isOk();

        StepVerifier.create(wordsRepository.findByUserAndCategoryAndWord(user, third.getCategory(), third.getWord()))
                .expectNext(third).verifyComplete();
    }

    @Test
    void deleteCategory() {
        wordsRepository.saveAll(Arrays.asList(first, second)).blockLast();

        client.delete().uri("/dictionary/{0}/{1}", user, category1).exchange().expectStatus().isOk();

        StepVerifier.create(wordsRepository.findByUserAndCategory(user, category1)).expectNextCount(0).verifyComplete();
    }

    @Test
    void deleteNotExistingCategory() {
        String notExistingCategory = "notExistingCategory";
        client.delete().uri("/dictionary/{0}/{1}", user, notExistingCategory).exchange().expectStatus().isNotFound();
    }

    @Test
    void deleteWord() {
        wordsRepository.save(first).block();

        client.delete().uri("/dictionary/{0}/{1}/{2}", first.getUser(), first.getCategory(), first.getWord()).exchange().expectStatus().isOk();

        StepVerifier.create(wordsRepository.findByUserAndCategoryAndWord(first.getUser(), first.getCategory(), first.getWord())).expectNextCount(0).verifyComplete();
    }

    @Test
    void deleteNotExistingWord() {
        String notExistingWord = "notExistingWord";
        client.delete().uri("/dictionary/{0}/{1}/{2}", user, category1, notExistingWord).exchange().expectStatus().isNotFound();
    }
}