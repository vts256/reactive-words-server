package com.vings.words.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.springframework.data.annotation.Id;

import java.util.Set;

@Data
@JsonInclude
public class Word {

    @Id
    private String user;

    private String category;

    private String word;

    private int answers;

    private Set<String> translation;

    public Word() {
    }

    public Word(String user, String category, String word, int answers, Set<String> translation) {
        this.user = user;
        this.category = category;
        this.word = word;
        this.answers = answers;
        this.translation = translation;
    }
}
