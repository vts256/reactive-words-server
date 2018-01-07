package com.vings.words.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.vings.words.model.Example;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

@Component
public class ExampleParser {

    public Set<Example> parse(JsonNode jsonNode) {

        if (jsonNode == null) {
            return Collections.emptySet();
        }

        Set<Example> examples = new HashSet<>();

        for (JsonNode result : jsonNode.path("results")) {
            String word = result.path("headword").asText();


            Set<String> definitions = new HashSet<>();
            Set<String> sentences = new HashSet<>();

            for (JsonNode sense : result.path("senses")) {
                definitions.addAll(parseArray(sense.path("definition"), jsNode -> jsNode.asText()));
                sentences.addAll(parseArray(sense.path("examples"), jsNode -> jsNode.path("text").textValue()));
            }

            examples.add(new Example(word, definitions, sentences));
        }

        return examples;
    }

    private Set<String> parseArray(JsonNode node, Function<JsonNode, String> converter) {
        Set<String> result = new HashSet<>();
        for (JsonNode elem : node) {
            result.add(converter.apply(elem));
        }
        return result;
    }
}
