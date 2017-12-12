package com.vings.words.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

import java.util.Objects;
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

    private Image image;

    public Word() {
    }

    public Word(String user, UUID category, String word, int answers, Image image, Set<String> translation) {
        this(user, category, word, answers, translation);
        this.image = image;
    }

    public Word(String user, UUID category, String word, int answers, Set<String> translation) {
        this.user = user;
        this.category = category;
        this.word = word;
        this.answers = answers;
        this.translation = translation;
    }

    public boolean learned() {
        return answers >= ANSWERS_ON_LEARNED_WORD;
    }
}
