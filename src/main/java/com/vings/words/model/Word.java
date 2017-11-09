package com.vings.words.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.springframework.data.annotation.Id;

@Data
@JsonInclude
public class Word {

    @Id
    private Integer id;

    private String word;

    private String translation;
}
