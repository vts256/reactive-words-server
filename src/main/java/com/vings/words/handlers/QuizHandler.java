package com.vings.words.handlers;

import com.vings.words.model.Word;
import com.vings.words.model.quiz.Crossword;
import com.vings.words.model.quiz.Guess;
import com.vings.words.model.quiz.Sprint;
import com.vings.words.repository.WordsRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.web.reactive.function.BodyInserters.fromObject;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@Component
public class QuizHandler {

    private static final String CATEGORY = "category";
    private static final String USER = "user";
    private static final String PAGE = "page";
    private static final String OFFSET = "offset";
    private static final String NO_TRANSLATION = "No translation";

    private final WordsRepository wordsRepository;

    public QuizHandler(WordsRepository wordsRepository) {
        this.wordsRepository = wordsRepository;
    }

    public Mono<ServerResponse> sprint(ServerRequest serverRequest) {
        String user = serverRequest.pathVariable(USER);
        UUID category = UUID.fromString(serverRequest.pathVariable(CATEGORY));
        int page = Integer.parseInt(serverRequest.pathVariable(PAGE));
        int offset = Integer.parseInt(serverRequest.pathVariable(OFFSET));

        return wordsRepository.findByUserAndCategory(user, category)
                .collectList()
                .flatMap(allWords ->
                        Flux.fromIterable(allWords)
                                .filter(word -> word.getAnswers() < 100)
                                .skip(page * offset)
                                .take(offset)
                                .map(word -> {
                                    double random = Math.random();
                                    String answer = Double.compare(random, 0.55) > 0 ? findTranslation(word) : findTranslation(allWords.get((int) Math.random() * allWords.size()));
                                    return new Sprint(word.getWord(), answer, word.getTranslation().contains(answer));
                                }).collectList())
                .flatMap(questions -> ok().body(fromObject(questions)));
    }

    private String findTranslation(Word word) {
        return word.getTranslation().stream().findAny().get();
    }

    public Mono<ServerResponse> crossword(ServerRequest serverRequest) {
        String user = serverRequest.pathVariable(USER);
        UUID category = UUID.fromString(serverRequest.pathVariable(CATEGORY));
        int page = Integer.parseInt(serverRequest.pathVariable(PAGE));
        int offset = Integer.parseInt(serverRequest.pathVariable(OFFSET));
        return wordsRepository.findByUserAndCategory(user, category)
                .filter(word -> word.getAnswers() < 100)
                .skip(page * offset)
                .take(offset)
                .map(word -> new Crossword(word.getWord(), findTranslation(word)))
                .collectList()
                .flatMap(questions -> ok().body(fromObject(questions)));
    }

    public Mono<ServerResponse> guess(ServerRequest serverRequest) {
        String user = serverRequest.pathVariable(USER);
        UUID category = UUID.fromString(serverRequest.pathVariable(CATEGORY));
        int page = Integer.parseInt(serverRequest.pathVariable(PAGE));
        int offset = Integer.parseInt(serverRequest.pathVariable(OFFSET));
        return wordsRepository.findByUserAndCategory(user, category)
                .collectList()
                .flatMap(allWords -> {
                    Set<String> allAnswers = allWords.stream().flatMap(word -> word.getTranslation().stream()).collect(Collectors.toSet());
                    return Flux.fromIterable(allWords)
                            .filter(word -> word.getAnswers() < 100)
                            .skip(page * offset)
                            .take(offset)
                            .map(word -> {
                                Set<String> answers = generateAnswers(allAnswers, word.getTranslation());
                                String correctAnswer = addCorrectAnswer(answers, word.getTranslation());
                                return new Guess(word.getWord(), answers, correctAnswer);
                            })
                            .collectList();
                })
                .flatMap(questions -> ok().body(fromObject(questions)));
    }

    private String addCorrectAnswer(Set<String> answers, Set<String> correctAnswers) {

        String correctAnswer = correctAnswers.stream().findAny().orElse(NO_TRANSLATION);

        while (answers.size() < 4) {
            answers.add(correctAnswer);
        }

        return correctAnswer;
    }

    private Set<String> generateAnswers(Set<String> allWords, Set<String> filter) {

        Set<String> answers = new HashSet<>();
        List<String> wrongAnswers = allWords.stream().filter(word -> !filter.contains(word)).collect(Collectors.toList());

        Random random = new Random();
        while (!wrongAnswers.isEmpty() && answers.size() < 3) {
            int index = random.nextInt(wrongAnswers.size());
            answers.add(wrongAnswers.get(index));
            wrongAnswers.remove(index);
        }

        return answers;
    }
}
