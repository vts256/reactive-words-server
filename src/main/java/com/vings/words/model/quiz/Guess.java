package com.vings.words.model.quiz;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
@AllArgsConstructor
public class Guess {
    private String word;
    private Set<String> answers;
    private String correct;
}
