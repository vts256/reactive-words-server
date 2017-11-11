package com.vings.words.model;

import com.datastax.driver.core.utils.UUIDs;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.springframework.data.annotation.Id;

import java.util.UUID;

@Data
@JsonInclude
public class Word {

    @Id
    private UUID id;

    private String word;

    private String translation;

    public Word() {
        this.id = UUIDs.timeBased();
    }

    public Word(String word, String translation) {
        this();
        this.word = word;
        this.translation = translation;
    }
}
