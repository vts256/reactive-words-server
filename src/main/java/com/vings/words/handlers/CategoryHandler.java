package com.vings.words.handlers;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.datastax.driver.core.utils.UUIDs;
import com.vings.words.model.Category;
import com.vings.words.model.Link;
import com.vings.words.parser.MultipartParser;
import com.vings.words.parser.ObjectParser;
import com.vings.words.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.springframework.web.reactive.function.BodyInserters.fromObject;
import static org.springframework.web.reactive.function.server.ServerResponse.*;

@Component
public class CategoryHandler {

    private static final String USER = "user";
    private static final String TITLE = "title";
    private static final String NEW_TITLE = "newTitle";

    @Value("${s3.words.bucket.name}")
    private String wordsBucket;

    @Value("${s3.url}")
    private String wordsServerUrl;

    private final AmazonS3 s3Client;

    private final CategoryRepository categoryRepository;

    private final MultipartParser multipartParser;

    private final ObjectParser objectParser;


    public CategoryHandler(CategoryRepository categoryRepository, AmazonS3 s3Client, MultipartParser multipartParser, ObjectParser objectParser) {
        this.categoryRepository = categoryRepository;
        this.s3Client = s3Client;
        this.multipartParser = multipartParser;
        this.objectParser = objectParser;
    }

    public Mono<ServerResponse> get(ServerRequest serverRequest) {
        String user = serverRequest.pathVariable(USER);
        Flux<Category> categories = categoryRepository.findByUser(user);
        return categories.collectList().flatMap(data -> {
            if (data.isEmpty()) {
                return notFound().build();
            } else {
                return ok().body(fromObject(data));
            }
        });
    }

    public Mono<ServerResponse> create(ServerRequest serverRequest) {
        String user = serverRequest.pathVariable(USER);
        return serverRequest.body(BodyExtractors.toMultipartData())
                .flatMap(parts -> {
                    Map<String, Part> partsMap = parts.toSingleValueMap();
                    Part categoryPart = partsMap.get("category");

                    return multipartParser.parse(categoryPart, Category.class).flatMap(data -> {
                        try {
                            return objectParser.parse(data, Category.class)
                                    .filter(elem -> elem.getTitle() != null)
                                    .flatMap(category -> categoryRepository.hasCategory(user, category.getTitle())
                                            .flatMap(foundCategories -> {
                                                        if (foundCategories != 0) {
                                                            return badRequest().body(Mono.just("Category already exists"), String.class);
                                                        }
                                                        Part filePart = partsMap.get("image");
                                                        return filePart == null ? ok().body(categoryRepository.save(new Category(category.getUser(), category.getTitle())), Category.class) :
                                                                saveImage(category.getUser(), category.getTitle(), filePart)
                                                                        .flatMap(urls -> ok().body(categoryRepository.save(new Category(user, category.getTitle(), urls.get(0))), Category.class));

                                                    }
                                            ))
                                    .switchIfEmpty(badRequest().body(Mono.just("Parameters isn't specified correctly"), String.class));
                        } catch (IOException exp) {
                            Exceptions.propagate(exp);
                        }
                        throw new IllegalStateException();
                    });
                });
    }

    public Mono<ServerResponse> updateImage(ServerRequest serverRequest) {
        String user = serverRequest.pathVariable(USER);
        String title = serverRequest.pathVariable(TITLE);

        return categoryRepository.findByUserAndTitle(user, title)
                .flatMap(category -> serverRequest.body(BodyExtractors.toMultipartData()).flatMap(parts -> {

                    Map<String, Part> partsMap = parts.toSingleValueMap();
                    Part filePart = partsMap.get("image");

                    if (filePart == null) {
                        throw new IllegalArgumentException("image couldn't be empty");
                    }

                    if (category.getImage() != null) {
                        s3Client.deleteObject(wordsBucket, category.getImage().getKey());
                    }

                    return saveImage(category.getUser(), category.getTitle(), filePart)
                            .flatMap(urls -> ok().body(categoryRepository.updateImage(category.getUser(), category.getTitle(), urls.get(0)), Category.class))
                            .switchIfEmpty(badRequest().body(Mono.just("image couldn't be empty"), String.class));
                }))
                .switchIfEmpty(badRequest().body(Mono.just("Category doesn't exist"), String.class));
    }

    public Mono<ServerResponse> update(ServerRequest serverRequest) {
        String user = serverRequest.pathVariable(USER);
        String title = serverRequest.pathVariable(TITLE);
        String newTitle = serverRequest.pathVariable(NEW_TITLE);

        return categoryRepository.findByUserAndTitle(user, title)
                .flatMap(category -> getExistingCategory(category, newTitle)
                        .flatMap(newCategory -> badRequest().body(Mono.just("Can't update, as new category already exist"), String.class))
                        .switchIfEmpty(categoryRepository.delete(category).then(ok().body(categoryRepository.save(new Category(category.getUser(), newTitle, category.getId())), Category.class))))
                .switchIfEmpty(badRequest().body(Mono.just("Category doesn't exist"), String.class));
    }

    public Mono<ServerResponse> delete(ServerRequest serverRequest) {
        String user = serverRequest.pathVariable(USER);
        String title = serverRequest.pathVariable(TITLE);
        return categoryRepository.findByUserAndTitle(user, title)
                .flatMap(category -> {
                    if (category.getImage() != null) {
                        s3Client.deleteObject(wordsBucket, category.getImage().getKey());
                    }
                    return categoryRepository.delete(category)
                            .then(ok().build());
                })
                .switchIfEmpty(notFound().build());
    }

    private Mono<Category> getExistingCategory(Category category, String newTitle) {
        if (category.getTitle().equals(newTitle)) {
            return Mono.empty();
        } else {
            return categoryRepository.findByUserAndTitle(category.getUser(), newTitle);
        }
    }

    private Mono<List<Link>> saveImage(String user, String title, Part filePart) {
        return filePart.content().flatMap(buffer -> {
            String imageName = user + "-" + title + "-" + UUIDs.timeBased().toString();
            s3Client.putObject(wordsBucket, imageName, buffer.asInputStream(), new ObjectMetadata());
            return Mono.just(new Link(imageName, wordsServerUrl + wordsBucket + "/" + imageName));
        }).collectList();
    }
}
