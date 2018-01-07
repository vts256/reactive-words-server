package com.vings.words.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Set;

@Data
@AllArgsConstructor
public class Example {

    private String word;
    private Set<String> definitions;
    private Set<String> sentences;
}
