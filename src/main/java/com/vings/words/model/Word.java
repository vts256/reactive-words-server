package com.vings.words.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

import java.util.Set;
import java.util.UUID;

@Data
@JsonInclude
public class Word {

    private static final int ANSWERS_ON_LEARNED_WORD = 100;

    @PrimaryKeyColumn(ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private String user;

    @PrimaryKeyColumn(ordinal = 1, type = PrimaryKeyType.PARTITIONED)
    private UUID category;

    @PrimaryKeyColumn(ordinal = 2, type = PrimaryKeyType.CLUSTERED, ordering = Ordering.ASCENDING)
    private String word;

    private int answers;

    private Set<String> translation;

    private Link image;

    private Link speech;

    private Set<Example> examples;

    private Word() {

    }

    private Word(WordBuilder wordBuilder) {
        this.user = wordBuilder.user;
        this.category = wordBuilder.category;
        this.word = wordBuilder.word;
        this.answers = wordBuilder.answers;
        this.translation = wordBuilder.translation;
        this.image = wordBuilder.image;
        this.speech = wordBuilder.speech;
        this.examples = wordBuilder.examples;
    }

    public boolean learned() {
        return answers >= ANSWERS_ON_LEARNED_WORD;
    }

    public static class WordBuilder {
        private String user;

        private UUID category;

        private String word;

        private int answers;

        private Set<String> translation;

        private Link image;

        private Link speech;

        private Set<Example> examples;

        public WordBuilder(String user, UUID category, String word) {
            this.user = user;
            this.category = category;
            this.word = word;
        }

        public WordBuilder withAnswers(int answers) {
            this.answers = answers;
            return this;
        }

        public WordBuilder withTranslation(Set<String> translation) {
            this.translation = translation;
            return this;
        }

        public WordBuilder withImage(Link link) {
            this.image = link;
            return this;
        }

        public WordBuilder withSpeech(Link speech) {
            this.speech = speech;
            return this;
        }

        public WordBuilder withExamples(Set<Example> examples) {
            this.examples = examples;
            return this;
        }

        public Word build() {
            return new Word(this);
        }
    }
}
