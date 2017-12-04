package com.vings.words.handlers;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vings.words.model.Category;
import com.vings.words.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.StringDecoder;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Collections;
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

    @Value("${s3.words.url}")
    private String wordsServerUrl;

    private AmazonS3 s3Client = AmazonS3ClientBuilder.standard().build();

    private CategoryRepository categoryRepository;

    public CategoryHandler(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
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

                    return parseCategoryPart(categoryPart).flatMap(data -> {
                        try {
                            return parseCategory(data)
                                    .filter(elem -> elem.getTitle() != null)
                                    .flatMap(category -> categoryRepository.hasCategory(user, category.getTitle())
                                            .flatMap(foundCategories -> {
                                                        if (foundCategories != 0) {
                                                            return badRequest().body(Mono.just("Category already exists"), String.class);
                                                        }
                                                        Part filePart = partsMap.get("image");//TODO: empty scenario
                                                        return filePart.content().flatMap(buffer -> {
                                                            String imageName = user + "-" + category.getTitle();//TODO: possible name duplication
                                                            s3Client.putObject(wordsBucket, imageName, buffer.asInputStream(), new ObjectMetadata());
                                                            return Mono.just(wordsServerUrl + wordsBucket + "/" + imageName);
                                                        }).collectList()
                                                                .flatMap(urls -> {
                                                                    category.setImageUrl(urls.get(0));
                                                                    return Mono.just(category);
                                                                }).flatMap(updatedCategory -> ok().body(categoryRepository.save(new Category(user,
                                                                        updatedCategory.getTitle(), updatedCategory.getImageUrl())), Category.class));

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

    private Mono<Category> parseCategory(String obj) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Category category = mapper.readValue(obj, Category.class);
        return Mono.just(category);
    }

    private Mono<String> parseCategoryPart(Part categoryPart) {
        return StringDecoder.textPlainOnly(false).decodeToMono(categoryPart.content(),
                ResolvableType.forClass(Category.class), MediaType.TEXT_PLAIN,
                Collections.emptyMap());
    }

    public Mono<ServerResponse> update(ServerRequest serverRequest) {
        String user = serverRequest.pathVariable(USER);
        String title = serverRequest.pathVariable(TITLE);
        String newTitle = serverRequest.pathVariable(NEW_TITLE);

        return categoryRepository.findByUserAndTitle(user, title)
                .flatMap(category ->
                        categoryRepository.findByUserAndTitle(user, newTitle)
                                .flatMap(newCategory -> badRequest().body(Mono.just("Can't update, as new category already exist"), String.class))
                                .switchIfEmpty(serverRequest.body(BodyExtractors.toMultipartData()).flatMap(parts -> {
                                            Map<String, Part> partsMap = parts.toSingleValueMap();
                                            Part filePart = partsMap.get("image");

                                            return categoryRepository.delete(category).then(filePart.content().flatMap(buffer -> {//TODO: not change image if wasn't send
                                                String imageName = category.getUser() + "-" + newTitle;
                                                s3Client.putObject(wordsBucket, imageName, buffer.asInputStream(), new ObjectMetadata());
                                                return Mono.just(wordsServerUrl + wordsBucket + "/" + imageName);
                                            }).collectList().flatMap(urls -> ok().body(categoryRepository.save(new Category(category.getUser(), newTitle, urls.get(0), category.getId())), Category.class)))
                                                    .switchIfEmpty(ok().body(categoryRepository.save(new Category(category.getUser(), newTitle, category.getId())), Category.class));
                                        })
                                ))
                .switchIfEmpty(badRequest().body(Mono.just("Category doesn't exist"), String.class));
    }

    public Mono<ServerResponse> delete(ServerRequest serverRequest) {
        String user = serverRequest.pathVariable(USER);
        String title = serverRequest.pathVariable(TITLE);
        return categoryRepository.findByUserAndTitle(user, title)
                .flatMap(category -> categoryRepository.delete(category)
                        .then(ok().build()))
                .switchIfEmpty(notFound().build());
    }
}
