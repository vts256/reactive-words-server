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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
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

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = WordsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CategoryServerTest {

    @LocalServerPort
    private int port;

    @Value("${s3.words.bucket.name}")
    private String wordsBucket;

    @Value("${s3.words.url}")
    private String wordsServerUrl;

    @Autowired
    private CategoryRepository categoryRepository;

    private WebTestClient client;

    private final String user = "user1";
    private final String firstTitle = "category1";
    private final String secondTitle = "category2";
    private final Category firstCategory = new Category(user, firstTitle, UUIDs.random());
    private final Category secondCategory = new Category(user, secondTitle, UUIDs.random());

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

    //TODO: expand tests to check saved image
    @Test
    void saveCategory() {
        Category category = createCategory(user, firstTitle);
        Category createdCategory = client.post().uri("/category/{0}", user)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(generateMultipartData(category))).exchange()
                .expectStatus().isOk()
                .expectBody(Category.class).returnResult().getResponseBody();

        assertThat(createdCategory.getUser()).isEqualTo(category.getUser());
        assertThat(createdCategory.getTitle()).isEqualTo(category.getTitle());
        assertThat(createdCategory.getImageUrl()).isEqualTo(wordsServerUrl + wordsBucket + "/" + user + "-" + firstTitle);
        assertThat(createdCategory.getId()).isNotNull();

        StepVerifier.create(categoryRepository.findByUser(user))
                .expectNext(createdCategory).expectComplete().verify();
    }

    @Test
    void saveExistingCategory() {
        categoryRepository.save(firstCategory).block();

        Category category = createCategory(user, firstTitle);
        client.post().uri("/category/{0}", user)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(generateMultipartData(category))).exchange()
                .expectStatus().isBadRequest()
                .expectBody(String.class).isEqualTo("Category already exists");

        StepVerifier.create(categoryRepository.findByUser(user))
                .expectNext(firstCategory).expectComplete().verify();
    }

    @Test
    void saveCategoryWithoutTitle() {

        Category category = createCategory(user, null);
        client.post().uri("/category/{0}", user)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(generateMultipartData(category))).exchange()
                .expectStatus().isBadRequest()
                .expectBody(String.class).isEqualTo("Parameters isn't specified correctly");

        StepVerifier.create(categoryRepository.findByUser(user))
                .expectComplete().verify();
    }

    @Test
    void updateCategory() {
        categoryRepository.save(firstCategory).block();

        Category createdCategory = client.patch().uri("/category/{0}/{1}/{2}", firstCategory.getUser(), firstCategory.getTitle(), secondTitle)
                .exchange().expectStatus().isOk()
                .expectBody(Category.class).returnResult().getResponseBody();

        assertThat(createdCategory.getUser()).isEqualTo(firstCategory.getUser());
        assertThat(createdCategory.getTitle()).isEqualTo(secondTitle);
        assertThat(createdCategory.getId()).isEqualTo(firstCategory.getId());

        StepVerifier.create(categoryRepository.findByUser(user))
                .expectNext(createdCategory).expectComplete().verify();
    }

    @Test
    void updateNotExistingCategory() {

        client.patch().uri("/category/{0}/{1}/{2}", firstCategory.getUser(), firstCategory.getTitle(), secondTitle)
                .exchange().expectStatus().isBadRequest()
                .expectBody(String.class).isEqualTo("Category doesn't exist");

        StepVerifier.create(categoryRepository.findByUser(user))
                .expectComplete().verify();
    }

    @Test
    void updateCategoryWhenCategoryWithNewTitleExist() {
        categoryRepository.saveAll(Arrays.asList(firstCategory, secondCategory)).blockLast();

        client.patch().uri("/category/{0}/{1}/{2}", firstCategory.getUser(), firstCategory.getTitle(), secondTitle)
                .exchange().expectStatus().isBadRequest()
                .expectBody(String.class).isEqualTo("Can't update, as new category already exist");

        StepVerifier.create(categoryRepository.findByUser(user))
                .expectNext(firstCategory, secondCategory).expectComplete().verify();
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

    private MultiValueMap<String, Object> generateMultipartData(Category category) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        ClassPathResource image = new ClassPathResource("dictionary.png");
        HttpEntity<ClassPathResource> imagePart = new HttpEntity<>(image, headers);
        HttpEntity<Category> categoryPart = new HttpEntity<>(category);
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("image", imagePart);
        parts.add("category", categoryPart);
        return parts;
    }

    private Category createCategory(String user, String title) {
        Category category = new Category();
        category.setUser(user);
        category.setTitle(title);
        return category;
    }
}
