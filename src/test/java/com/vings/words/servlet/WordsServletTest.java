package com.vings.words.servlet;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.datastax.driver.core.utils.UUIDs;
import com.vings.words.WordsApplication;
import com.vings.words.model.Category;
import com.vings.words.model.Image;
import com.vings.words.model.Word;
import com.vings.words.repository.WordsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.test.StepVerifier;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.UUID;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA;
import static org.springframework.http.MediaType.TEXT_PLAIN;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = WordsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WordsServletTest {

    @LocalServerPort
    private int port;

    private String user = "user1";
    private UUID category1 = UUIDs.random();
    private UUID category2 = UUIDs.random();
    private Image image = new Image("key", "url");
    private Word first = new Word(user, category1, "Reactive", 16, new HashSet<>(singletonList("Реактив")));
    private Word second = new Word(user, category1, "Core", 100, new HashSet<>(singletonList("Основа")));
    private Word third = new Word(user, category2, "Tango", 95, new HashSet<>(asList("Танго", "Супер")));

    @Autowired
    private WordsRepository wordsRepository;

    private WebTestClient client;

    @MockBean
    private AmazonS3 amazonS3;

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
    void notFoundWhenGetWordsByNotExistingUserAndCategory() {
        wordsRepository.saveAll(asList(first, second, third)).blockLast();

        String notExistingUser = "notExistingUser";
        String notExistingCategory = UUIDs.random().toString();
        client.get().uri("/dictionary/{0}/{1}", notExistingUser, notExistingCategory).exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void badRequestWhenGetWordsByNonValidUUID() {
        wordsRepository.saveAll(asList(first, second, third)).blockLast();

        String category = "category";
        client.get().uri("/dictionary/{0}/{1}", user, category).exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void getWordsByCategory() {
        wordsRepository.saveAll(asList(first, second, third)).blockLast();

        client.get().uri("/dictionary/{0}/{1}", user, category1).exchange()
                .expectStatus().isOk()
                .expectBodyList(Word.class).hasSize(2).contains(first, second);
    }

    @Test
    void getLearnedWords() {
        wordsRepository.saveAll(asList(first, second)).blockLast();

        client.get().uri("/dictionary/{0}/{1}/{2}", user, category1, true).exchange()
                .expectStatus().isOk()
                .expectBodyList(Word.class).hasSize(1).contains(second);
    }

    @Test
    void badRequestWhenGetLearnedWordsByNonValidUUID() {
        wordsRepository.saveAll(asList(first, second, third)).blockLast();

        String category = "category";
        client.get().uri("/dictionary/{0}/{1}/{2}", user, category, true).exchange()
                .expectStatus().isBadRequest();
    }


    @Test
    void getNotLearnedWords() {
        wordsRepository.saveAll(asList(first, second)).blockLast();

        client.get().uri("/dictionary/{0}/{1}/{2}", user, category1, false).exchange()
                .expectStatus().isOk()
                .expectBodyList(Word.class).hasSize(1).contains(first);
    }

    @Test
    void getWordsByCategoryFromEmptyDictionary() {

        String category = UUIDs.random().toString();
        client.get().uri("/dictionary/category/{0}", category).exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void saveWord() {
        Word response = client.post().uri("/dictionary/{0}", user)
                .contentType(MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(generateMultipartData(first))).exchange()
                .expectStatus().isOk()
                .expectBody(Word.class).returnResult().getResponseBody();

        assertWord(response, first);

        verify(amazonS3).putObject(anyString(), anyString(), any(InputStream.class), any(ObjectMetadata.class));

        StepVerifier.create(wordsRepository.findByUserAndCategoryAndWord(user, first.getCategory(), first.getWord()))
                .assertNext(word -> assertWord(word, first))
                .expectComplete().verify();
    }

    private void assertWord(Word actual, Word expected) {
        assertThat(actual.getWord()).isEqualTo(expected.getWord());
        assertThat(actual.getUser()).isEqualTo(expected.getUser());
        assertThat(actual.getTranslation()).isEqualTo(expected.getTranslation());
        assertThat(actual.getAnswers()).isEqualTo(0);
        assertThat(actual.getCategory()).isEqualTo(expected.getCategory());
        assertThat(actual.getImage()).isNotNull();
        assertThat(actual.getImage().getUrl()).isNotEmpty();
    }

    private MultiValueMap<String, Object> generateMultipartData(Word word) {
        MultiValueMap<String, Object> parts = generateImageData();
        HttpEntity<Word> wordEntry = new HttpEntity<>(word);
        parts.add("word", wordEntry);
        return parts;
    }

    private MultiValueMap<String, Object> generateImageData() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(TEXT_PLAIN);
        ClassPathResource image = new ClassPathResource("dictionary.png");
        HttpEntity<ClassPathResource> imagePart = new HttpEntity<>(image, headers);
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("image", imagePart);
        return parts;
    }

    @Test
    void updateTranslation() {

        wordsRepository.save(first).block();

        client.patch().uri("/dictionary/{0}/{1}/{2}", first.getUser(), first.getCategory(), first.getWord())
                .body(BodyInserters.fromObject("{\"translation\":\"библиотека\"}")).exchange()
                .expectStatus().isOk();

        StepVerifier.create(wordsRepository.findByUserAndCategoryAndWord(user, first.getCategory(), first.getWord()))
                .expectNextMatches(data -> new HashSet<>(asList("библиотека", "Реактив")).equals(data.getTranslation())).verifyComplete();
    }

    @Test
    void updateTranslationWithNotSpecifiedTranslationTag() {

        wordsRepository.save(first).block();

        client.patch().uri("/dictionary/{0}/{1}/{2}", first.getUser(), first.getCategory(), first.getWord())
                .body(BodyInserters.fromObject("{\"wrong tag\":\"библиотека\"}")).exchange()
                .expectStatus().isBadRequest();

        StepVerifier.create(wordsRepository.findByUserAndCategoryAndWord(user, first.getCategory(), first.getWord()))
                .expectNextMatches(data -> new HashSet<>(asList("Реактив")).equals(data.getTranslation())).verifyComplete();
    }

    @Test
    void badRequestWhenUpdateTranslationWithNonValidUUIDCategory() {
        String category = "category";
        client.patch().uri("/dictionary/{0}/{1}/{2}", first.getUser(), category, first.getWord())
                .body(BodyInserters.fromObject("{\"translation\":\"библиотека\"}")).exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void updateTranslationWitDuplicateTranslation() {

        wordsRepository.save(first).block();

        client.patch().uri("/dictionary/{0}/{1}/{2}", first.getUser(), first.getCategory(), first.getWord())
                .body(BodyInserters.fromObject("{\"translation\":\"Реактив\"}")).exchange()
                .expectStatus().isOk();

        StepVerifier.create(wordsRepository.findByUserAndCategoryAndWord(user, first.getCategory(), first.getWord()))
                .expectNextMatches(data -> new HashSet<>(asList("Реактив")).equals(data.getTranslation())).verifyComplete();
    }

    @Test
    void deleteTranslation() {
        wordsRepository.save(third).block();

        String translation = "Супер";
        client.delete().uri("/dictionary/{0}/{1}/{2}/{3}", third.getUser(), third.getCategory(), third.getWord(), translation)
                .exchange()
                .expectStatus().isOk();

        StepVerifier.create(wordsRepository.findByUserAndCategoryAndWord(user, third.getCategory(), third.getWord()))
                .expectNextMatches(data -> new HashSet<>(asList("Танго")).equals(data.getTranslation())).verifyComplete();
    }

    @Test
    void badRequestWhenDeleteTranslationWithNonValidUUIDCategory() {
        String category = "category";
        String translation = "Супер";
        client.delete().uri("/dictionary/{0}/{1}/{2}/{3}", third.getUser(), category, third.getWord(), translation)
                .exchange()
                .expectStatus().isBadRequest();
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
        first.setImage(image);
        second.setImage(image);
        wordsRepository.saveAll(asList(first, second)).blockLast();

        client.delete().uri("/dictionary/{0}/{1}", user, category1).exchange().expectStatus().isOk();

        verify(amazonS3, times(2)).deleteObject(anyString(), anyString());

        StepVerifier.create(wordsRepository.findByUserAndCategory(user, category1)).expectNextCount(0).verifyComplete();
    }

    @Test
    void deleteNotExistingCategory() {
        String notExistingCategory = UUIDs.random().toString();
        client.delete().uri("/dictionary/{0}/{1}", user, notExistingCategory).exchange().expectStatus().isNotFound();
    }

    @Test
    void badRequestWhenDeleteCategoryWithNonValidUUID() {
        String category = "category";
        client.delete().uri("/dictionary/{0}/{1}", user, category).exchange().expectStatus().isBadRequest();
    }

    @Test
    void deleteWord() {
        first.setImage(image);
        wordsRepository.save(first).block();

        client.delete().uri("/dictionary/{0}/{1}/{2}", first.getUser(), first.getCategory(), first.getWord()).exchange().expectStatus().isOk();

        verify(amazonS3).deleteObject(anyString(), anyString());

        StepVerifier.create(wordsRepository.findByUserAndCategoryAndWord(first.getUser(), first.getCategory(), first.getWord())).expectNextCount(0).verifyComplete();
    }

    @Test
    void badRequestWhenDeleteWordWithNonValidUUIDCategory() {
        String category = "category";

        client.delete().uri("/dictionary/{0}/{1}/{2}", first.getUser(), category, first.getWord()).exchange().expectStatus().isBadRequest();
    }


    @Test
    void deleteNotExistingWord() {
        String notExistingWord = "notExistingWord";
        client.delete().uri("/dictionary/{0}/{1}/{2}", user, category1, notExistingWord).exchange().expectStatus().isNotFound();
    }
}