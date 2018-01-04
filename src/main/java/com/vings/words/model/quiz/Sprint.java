package com.vings.words.model.quiz;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Sprint {

    private String word;
    private String answer;
    private boolean isCorrect;
}
