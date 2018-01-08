package com.vings.words.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;

import java.util.Set;

@Data
@AllArgsConstructor
@UserDefinedType
public class Example {

    private String word;
    private Set<String> definitions;
    private Set<String> sentences;
}
