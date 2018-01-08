package com.vings.words.servlet;

import com.amazonaws.services.polly.AmazonPolly;
import com.amazonaws.services.polly.model.SynthesizeSpeechRequest;
import com.amazonaws.services.polly.model.SynthesizeSpeechResult;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.datastax.driver.core.utils.UUIDs;
import com.vings.words.WordsApplication;
import com.vings.words.model.Link;
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
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.test.StepVerifier;

import java.io.InputStream;
import java.util.HashSet;
import java.util.UUID;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
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
    private Link link = new Link("key", "url");
    private Word first = new Word.WordBuilder(user, category1, "Reactive").withAnswers(16).withTranslation(new HashSet<>(singletonList("Реактив"))).build();
    private Word second = new Word.WordBuilder(user, category1, "Core").withAnswers(100).withTranslation(new HashSet<>(singletonList("Основа"))).build();
    private Word third = new Word.WordBuilder(user, category2, "Tango").withAnswers(95).withTranslation(new HashSet<>(asList("Танго", "Супер"))).build();

    @Autowired
    private WordsRepository wordsRepository;

    private WebTestClient client;

    @MockBean
    private AmazonS3 amazonS3;

    @MockBean
    private AmazonPolly amazonPolly;

    @BeforeEach
    void setUp() {
        client = WebTestClient
                .bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        when(amazonPolly.synthesizeSpeech(any(SynthesizeSpeechRequest.class))).thenReturn(mock(SynthesizeSpeechResult.class));
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
        assertThat(actual.getSpeech()).isNotNull();
        assertThat(actual.getSpeech().getUrl()).isNotEmpty();
        assertThat(actual.getWord()).isNotEmpty();
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
    void saveImage() {
        wordsRepository.save(first).block();

        client.post().uri("/dictionary/{0}/{1}/{2}/image", first.getUser(), first.getCategory(), first.getWord())
                .contentType(MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(generateImageData())).exchange()
                .expectStatus().isOk();

        StepVerifier.create(wordsRepository.findByUserAndCategoryAndWord(user, first.getCategory(), first.getWord()))
                .expectNextMatches(data -> data.getImage() != null && data.getImage() != first.getImage()).verifyComplete();
    }

    @Test
    void saveImageForNotExistedWord() {
        client.post().uri("/dictionary/{0}/{1}/{2}/image", first.getUser(), first.getCategory(), first.getWord())
                .contentType(MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(generateImageData())).exchange()
                .expectStatus().isBadRequest()
                .expectBody(String.class).isEqualTo("word doesn't exists");

        StepVerifier.create(wordsRepository.findByUserAndCategoryAndWord(user, first.getCategory(), first.getWord()))
                .verifyComplete();
    }

    @Test
    void saveImageWithoutData() {
        wordsRepository.save(first).block();

        client.post().uri("/dictionary/{0}/{1}/{2}/image", first.getUser(), first.getCategory(), first.getWord())
                .contentType(MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(new LinkedMultiValueMap<>())).exchange()
                .expectStatus().isBadRequest()
                .expectBody(String.class).isEqualTo("Image wasn't found");

        StepVerifier.create(wordsRepository.findByUserAndCategoryAndWord(user, first.getCategory(), first.getWord()))
                .expectNext(first).verifyComplete();
    }

    @Test
    void updateTranslation() {

        wordsRepository.save(first).block();

        String translation = "библиотека";
        client.patch().uri("/dictionary/{0}/{1}/{2}/add/{3}", first.getUser(), first.getCategory(), first.getWord(), translation)
                .exchange()
                .expectStatus().isOk();

        StepVerifier.create(wordsRepository.findByUserAndCategoryAndWord(user, first.getCategory(), first.getWord()))
                .expectNextMatches(data -> new HashSet<>(asList("библиотека", "Реактив")).equals(data.getTranslation())).verifyComplete();
    }

    @Test
    void badRequestWhenUpdateTranslationWithNonValidUUIDCategory() {
        String category = "category";
        String translation = "библиотека";
        client.patch().uri("/dictionary/{0}/{1}/{2}/add/{3}", first.getUser(), category, first.getWord(), translation)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void badRequestWhenUpdateTranslationNotExistingWord() {
        String translation = "библиотека";
        client.patch().uri("/dictionary/{0}/{1}/{2}/add/{3}", first.getUser(), first.getCategory(), first.getWord(), translation)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(String.class).isEqualTo("word doesn't exists");
    }

    @Test
    void updateTranslationWithDuplicateTranslation() {

        wordsRepository.save(first).block();

        String translation = "Реактив";
        client.patch().uri("/dictionary/{0}/{1}/{2}/add/{3}", first.getUser(), first.getCategory(), first.getWord(), translation)
                .exchange()
                .expectStatus().isOk();

        StepVerifier.create(wordsRepository.findByUserAndCategoryAndWord(user, first.getCategory(), first.getWord()))
                .expectNextMatches(data -> new HashSet<>(asList(translation)).equals(data.getTranslation())).verifyComplete();
    }

    @Test
    void deleteTranslation() {
        wordsRepository.save(third).block();

        String translation = "Супер";
        client.delete().uri("/dictionary/{0}/{1}/{2}/delete/{3}", third.getUser(), third.getCategory(), third.getWord(), translation)
                .exchange()
                .expectStatus().isOk();

        StepVerifier.create(wordsRepository.findByUserAndCategoryAndWord(user, third.getCategory(), third.getWord()))
                .expectNextMatches(data -> new HashSet<>(asList("Танго")).equals(data.getTranslation())).verifyComplete();
    }

    @Test
    void badRequestWhenDeleteTranslationWithNonValidUUIDCategory() {
        String category = "category";
        String translation = "Супер";
        client.delete().uri("/dictionary/{0}/{1}/{2}/delete/{3}", third.getUser(), category, third.getWord(), translation)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void deleteNotExistingTranslation() {
        wordsRepository.save(third).block();

        String translation = "NotExisting";
        client.delete().uri("/dictionary/{0}/{1}/{2}/delete/{3}", third.getUser(), third.getCategory(), third.getWord(), translation)
                .exchange()
                .expectStatus().isOk();

        StepVerifier.create(wordsRepository.findByUserAndCategoryAndWord(user, third.getCategory(), third.getWord()))
                .expectNext(third).verifyComplete();
    }

    @Test
    void deleteTranslationFromNotExistingWord() {
        wordsRepository.save(third).block();

        String translation = "NotExisting";
        client.delete().uri("/dictionary/{0}/{1}/{2}/delete/{3}", third.getUser(), third.getCategory(), first.getWord(), translation)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(String.class).isEqualTo("word doesn't exists");

        StepVerifier.create(wordsRepository.findByUserAndCategoryAndWord(user, third.getCategory(), third.getWord()))
                .expectNext(third).verifyComplete();
    }

    @Test
    void deleteCategory() {
        first.setImage(link);
        second.setImage(link);
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
        first.setImage(link);
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