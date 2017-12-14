package com.vings.words.handlers;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.datastax.driver.core.utils.UUIDs;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vings.words.model.Image;
import com.vings.words.model.Word;
import com.vings.words.repository.WordsRepository;
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
import java.util.*;

import static org.springframework.web.reactive.function.BodyInserters.fromObject;
import static org.springframework.web.reactive.function.server.ServerResponse.*;

@Component
public class DictionaryHandler {

    private static final String USER = "user";
    private static final String CATEGORY = "category";
    private static final String WORD = "word";
    private static final String LEARNED = "learned";
    private static final String TRANSLATION = "translation";

    @Value("${s3.words.bucket.name}")
    private String wordsBucket;

    @Value("${s3.words.url}")
    private String wordsServerUrl;

    private AmazonS3 s3Client;

    private WordsRepository wordsRepository;

    public DictionaryHandler(WordsRepository wordsRepository, AmazonS3 s3Client) {
        this.wordsRepository = wordsRepository;
        this.s3Client = s3Client;
    }

    public Mono<ServerResponse> getWords(ServerRequest serverRequest) {
        String user = serverRequest.pathVariable(USER);
        String category = serverRequest.pathVariable(CATEGORY);
        Flux<Word> words = wordsRepository.findByUserAndCategory(user, UUID.fromString(category));
        return words.collectList().flatMap(data -> {
            if (data.isEmpty()) {
                return notFound().build();
            } else {
                return ok().body(fromObject(data));
            }
        });
    }

    public Mono<ServerResponse> getWordsByLearnedFilter(ServerRequest serverRequest) {
        String user = serverRequest.pathVariable(USER);
        String category = serverRequest.pathVariable(CATEGORY);
        boolean learned = Boolean.valueOf(serverRequest.pathVariable(LEARNED));
        Flux<Word> words = wordsRepository.findByUserAndCategory(user, UUID.fromString(category)).filter(word -> word.learned() == learned);
        return words.collectList().flatMap(data -> {
            if (data.isEmpty()) {
                return notFound().build();
            } else {
                return ok().body(fromObject(data));
            }
        });
    }

    public Mono<ServerResponse> save(ServerRequest serverRequest) {
        return serverRequest.body(BodyExtractors.toMultipartData())
                .flatMap(parts -> {
                    Map<String, Part> partsMap = parts.toSingleValueMap();
                    Part wordPart = partsMap.get("word");
                    return parseWordPart(wordPart).flatMap(data -> {
                        try {
                            return parseWord(data).filter(elem -> elem.getUser() != null && elem.getWord() != null && elem.getCategory() != null && elem.getTranslation() != null)
                                    .flatMap(word -> wordsRepository.findByUserAndCategoryAndWord(word.getUser(), word.getCategory(), word.getWord())
                                            .flatMap(foundWords -> badRequest().body(Mono.just("Category already exists"), String.class))
                                            .switchIfEmpty(saveWord(word, partsMap)))
                                    .switchIfEmpty(badRequest().body(Mono.just("Parameters isn't specified correctly"), String.class));


                        } catch (IOException exp) {
                            Exceptions.propagate(exp);
                        }
                        throw new IllegalStateException();
                    });
                });
    }

    private Mono<? extends ServerResponse> saveWord(Word word, Map<String, Part> partsMap) {
        Part filePart = partsMap.get("image");
        return filePart == null ? ok().body(wordsRepository.save(word), Word.class) :
                saveImage(word.getUser(), word.getWord(), filePart)
                        .flatMap(urls -> ok().body(wordsRepository.save(new Word(word.getUser(), word.getCategory(), word.getWord(), 0, urls.get(0), word.getTranslation())), Word.class));
    }

    private Mono<List<Image>> saveImage(String user, String word, Part filePart) {
        return filePart.content().flatMap(buffer -> {
            String imageName = user + "-" + word + "-" + UUIDs.timeBased().toString();
            s3Client.putObject(wordsBucket, imageName, buffer.asInputStream(), new ObjectMetadata());
            return Mono.just(new Image(imageName, wordsServerUrl + wordsBucket + "/" + imageName));
        }).collectList();
    }

    private Mono<Word> parseWord(String data) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Word word = mapper.readValue(data, Word.class);
        return Mono.just(word);
    }

    private Mono<String> parseWordPart(Part wordPart) {//TODO: move to separate class, duplicate from category handler
        return StringDecoder.textPlainOnly(false).decodeToMono(wordPart.content(),
                ResolvableType.forClass(Word.class), MediaType.TEXT_PLAIN,
                Collections.emptyMap());
    }

    public Mono<ServerResponse> deleteCategory(ServerRequest serverRequest) {
        String user = serverRequest.pathVariable(USER);
        UUID category = UUID.fromString(serverRequest.pathVariable(CATEGORY));
        return wordsRepository.findByUserAndCategory(user, category).collectList()
                .flatMap(existingWords -> {
                    if (existingWords.isEmpty()) {
                        return notFound().build();
                    }

                    existingWords.forEach(word -> {
                        if (word.getImage() != null) {
                            s3Client.deleteObject(wordsBucket, word.getImage().getKey());
                        }
                    });

                    return wordsRepository.deleteByUserAndCategory(user, category).then(ok().build());
                });
    }

    public Mono<ServerResponse> updateImage(ServerRequest serverRequest) {//TODO: update image
        String user = serverRequest.pathVariable(USER);
        UUID category = UUID.fromString(serverRequest.pathVariable(CATEGORY));
        String word = serverRequest.pathVariable(WORD);

        return serverRequest.body(BodyExtractors.toMultipartData())
                .flatMap(parts -> wordsRepository.findByUserAndCategoryAndWord(user, category, word).flatMap(foundWord -> {
                            if (foundWord.getImage() != null) {
                                s3Client.deleteObject(wordsBucket, foundWord.getImage().getKey());
                            }

                            Map<String, Part> partsMap = parts.toSingleValueMap();
                            Part filePart = partsMap.get("image");

                            if (filePart == null) {
                                return badRequest().body(Mono.just("Image wasn't found"), String.class);
                            }
                            return saveImage(user, word, filePart).flatMap(urls -> wordsRepository.saveImage(user, category, word, urls.get(0))).then(ok().build());
                        }).switchIfEmpty(badRequest().body(Mono.just("word doesn't exists"), String.class))
                );
    }

    public Mono<ServerResponse> deleteWord(ServerRequest serverRequest) {
        String user = serverRequest.pathVariable(USER);
        String category = serverRequest.pathVariable(CATEGORY);
        String word = serverRequest.pathVariable(WORD);

        return wordsRepository.findByUserAndCategoryAndWord(user, UUID.fromString(category), word)
                .flatMap(existingWord -> {
                    if (existingWord.getImage() != null) {
                        s3Client.deleteObject(wordsBucket, existingWord.getImage().getKey());
                    }
                    return wordsRepository.delete(existingWord)
                            .then(ok().build());
                })
                .switchIfEmpty(notFound().build());
    }

    public Mono<ServerResponse> deleteTranslation(ServerRequest serverRequest) {
        String user = serverRequest.pathVariable(USER);
        String category = serverRequest.pathVariable(CATEGORY);
        String word = serverRequest.pathVariable(WORD);
        String translation = serverRequest.pathVariable(TRANSLATION);
        return wordsRepository.deleteTranslation(user, UUID.fromString(category), word, new HashSet<>(Arrays.asList(translation))).then(ok().build());
    }

    public Mono<ServerResponse> addTranslation(ServerRequest serverRequest) {
        String user = serverRequest.pathVariable(USER);
        String category = serverRequest.pathVariable(CATEGORY);
        String word = serverRequest.pathVariable(WORD);
        String translation = serverRequest.pathVariable(TRANSLATION);
        return wordsRepository.findByUserAndCategoryAndWord(user, UUID.fromString(category), word)
                .flatMap(existingWord -> ok().body(wordsRepository.addTranslation(user, UUID.fromString(category), word, new HashSet<>(Arrays.asList(translation))), Word.class))
                .switchIfEmpty(badRequest().body(Mono.just("word doesn't exists"), String.class));

    }
}
