package com.vings.words.handlers;

import com.amazonaws.services.polly.AmazonPolly;
import com.amazonaws.services.polly.model.OutputFormat;
import com.amazonaws.services.polly.model.SynthesizeSpeechRequest;
import com.amazonaws.services.polly.model.SynthesizeSpeechResult;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.datastax.driver.core.utils.UUIDs;
import com.vings.words.model.Example;
import com.vings.words.model.Link;
import com.vings.words.model.Word;
import com.vings.words.parser.MultipartParser;
import com.vings.words.parser.ObjectParser;
import com.vings.words.repository.WordsRepository;
import com.vings.words.service.WordExampleService;
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

    @Value("${s3.speech.bucket.name}")
    private String speechBucket;

    @Value("${polly.words.voice}")
    private String speechVoice;

    @Value("${s3.url}")
    private String wordsServerUrl;

    private final AmazonS3 s3Client;

    private final AmazonPolly pollyClient;

    private final WordsRepository wordsRepository;

    private final WordExampleService exampleService;

    private final MultipartParser multipartParser;

    private final ObjectParser objectParser;

    public DictionaryHandler(WordsRepository wordsRepository, AmazonS3 s3Client, AmazonPolly pollyClient,
                             MultipartParser multipartParser, ObjectParser objectParser, WordExampleService exampleService) {
        this.wordsRepository = wordsRepository;
        this.s3Client = s3Client;
        this.pollyClient = pollyClient;
        this.multipartParser = multipartParser;
        this.objectParser = objectParser;
        this.exampleService = exampleService;
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
                    return multipartParser.parse(wordPart, Word.class).flatMap(data -> {
                        try {
                            return objectParser.parse(data, Word.class)
                                    .filter(elem -> elem.getUser() != null && elem.getWord() != null && elem.getCategory() != null && elem.getTranslation() != null)
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
                        if (word.getSpeech() != null) {
                            s3Client.deleteObject(speechBucket, word.getSpeech().getKey());
                        }
                    });

                    return wordsRepository.deleteByUserAndCategory(user, category).then(ok().build());
                });
    }

    public Mono<ServerResponse> updateImage(ServerRequest serverRequest) {
        String user = serverRequest.pathVariable(USER);
        UUID category = UUID.fromString(serverRequest.pathVariable(CATEGORY));
        String word = serverRequest.pathVariable(WORD);

        return serverRequest.body(BodyExtractors.toMultipartData())
                .flatMap(parts -> wordsRepository.findByUserAndCategoryAndWord(user, category, word).flatMap(foundWord -> {
                            if (foundWord.getImage() != null) {
                                s3Client.deleteObject(wordsBucket, foundWord.getImage().getKey());//TODO: move to separate class
                            }
                            if (foundWord.getSpeech() != null) {
                                s3Client.deleteObject(speechBucket, foundWord.getSpeech().getKey());
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
                    if (existingWord.getSpeech() != null) {
                        s3Client.deleteObject(speechBucket, existingWord.getSpeech().getKey());
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
        return wordsRepository.findByUserAndCategoryAndWord(user, UUID.fromString(category), word)
                .flatMap(existingWord -> wordsRepository.deleteTranslation(user, UUID.fromString(category), word, new HashSet<>(Arrays.asList(translation))).then(ok().build()))
                .switchIfEmpty(badRequest().body(Mono.just("word doesn't exists"), String.class));
    }

    public Mono<ServerResponse> addTranslation(ServerRequest serverRequest) {
        String user = serverRequest.pathVariable(USER);
        UUID category = UUID.fromString(serverRequest.pathVariable(CATEGORY));
        String word = serverRequest.pathVariable(WORD);
        Set<String> translation = new HashSet<>(Arrays.asList(serverRequest.pathVariable(TRANSLATION)));
        return wordsRepository.findByUserAndCategoryAndWord(user, category, word)
                .flatMap(existingWord -> ok().body(wordsRepository.addTranslation(user, category, word, translation), Word.class))
                .switchIfEmpty(badRequest().body(Mono.just("word doesn't exists"), String.class));

    }

    private Mono<ServerResponse> saveWord(Word word, Map<String, Part> partsMap) {

        Link speech = generateSpeech(word);
        word.setSpeech(speech);

        Set<Example> examples = generateExamples(word);
        word.setExamples(examples);

        Part filePart = partsMap.get("image");
        return filePart == null ? ok().body(wordsRepository.save(word), Word.class) :
                saveImage(word.getUser(), word.getWord(), filePart)
                        .flatMap(urls -> ok().body(wordsRepository.save(new Word.WordBuilder(word.getUser(), word.getCategory(), word.getWord())
                                .withImage(urls.get(0)).withSpeech(word.getSpeech()).withTranslation(word.getTranslation()).withExamples(word.getExamples()).build()), Word.class));
    }

    private Set<Example> generateExamples(Word word) {
        return exampleService.request(word.getWord());
    }

    private Link generateSpeech(Word word) {
        SynthesizeSpeechRequest synthesizeSpeechRequest = new SynthesizeSpeechRequest()
                .withText(word.getWord())
                .withVoiceId(speechVoice)
                .withOutputFormat(OutputFormat.Mp3);

        SynthesizeSpeechResult synthesizeSpeechResult = pollyClient.synthesizeSpeech(synthesizeSpeechRequest);
        String speechName = word.getUser() + "-" + word.getWord() + "-" + UUIDs.timeBased().toString();
        s3Client.putObject(speechBucket, speechName, synthesizeSpeechResult.getAudioStream(), new ObjectMetadata());
        return createLink(speechName, speechBucket);
    }

    private Mono<List<Link>> saveImage(String user, String word, Part filePart) {
        return filePart.content().flatMap(buffer -> {
            String imageName = user + "-" + word + "-" + UUIDs.timeBased().toString();
            s3Client.putObject(wordsBucket, imageName, buffer.asInputStream(), new ObjectMetadata());
            return Mono.just(createLink(imageName, wordsBucket));
        }).collectList();
    }

    private Link createLink(String key, String bucket) {
        return new Link(key, wordsServerUrl + bucket + "/" + key);
    }

}
