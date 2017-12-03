package com.vings.words.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vings.words.model.Category;
import com.vings.words.repository.CategoryRepository;
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
        return serverRequest.body(BodyExtractors.toMultipartData())
                .flatMap(parts -> {
                    Map<String, Part> partsMap = parts.toSingleValueMap();
                    Part filePart = partsMap.get("image");
                    Part category = partsMap.get("category");

                    return StringDecoder.textPlainOnly(false).decodeToMono(category.content(),
                            ResolvableType.forClass(Category.class), MediaType.TEXT_PLAIN,
                            Collections.emptyMap())
                            .flatMap(obj -> {
                                try {
                                    ObjectMapper mapper = new ObjectMapper();
                                    Category newCategory = mapper.readValue(obj, Category.class);
                                    return Mono.just(newCategory);
                                } catch (IOException exp) {
                                    Exceptions.propagate(exp);
                                }
                                return Mono.empty();
                            });

//                    return filePart.content().flatMap(buffer -> {
//                        try (InputStream inputStream = buffer.asInputStream(); OutputStream outputStream = new FileOutputStream(new File("src/test/resources/test.png"))) {
//                            copy(inputStream, outputStream);
//                        } catch (IOException exp) {
//                            Exceptions.propagate(exp);
//                        }
//                        return null;
//                    }).then(ok().build());
                })
                .filter(category -> category.getUser() != null && category.getTitle() != null)
                .flatMap(category ->
                        categoryRepository.findByUserAndTitle(category.getUser(), category.getTitle())
                                .flatMap(existingCategory -> badRequest().body(Mono.just("Category already exists"), String.class))
                                .switchIfEmpty(ok().body(categoryRepository.save(new Category(category.getUser(), category.getTitle())), Category.class))
                )
                .switchIfEmpty(badRequest().body(Mono.just("Parameters isn't specified correctly"), String.class));
    }

    public Mono<ServerResponse> update(ServerRequest serverRequest) {
        String user = serverRequest.pathVariable(USER);
        String title = serverRequest.pathVariable(TITLE);
        String newTitle = serverRequest.pathVariable(NEW_TITLE);

        return categoryRepository.findByUserAndTitle(user, title).log()
                .flatMap(category ->
                        categoryRepository.findByUserAndTitle(user, newTitle).log()
                                .flatMap(newCategory -> badRequest().body(Mono.just("Can't update, as new category already exist"), String.class))
                                .switchIfEmpty(categoryRepository.delete(new Category(user, title))
                                        .then(ok().body(categoryRepository.save(new Category(category.getUser(), newTitle, category.getId())), Category.class))))
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
