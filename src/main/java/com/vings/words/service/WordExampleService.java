package com.vings.words.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.vings.words.model.Example;
import com.vings.words.parser.ExampleParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Set;

@Component
public class WordExampleService {
    @Value("${example.dictionary.api}")
    private String exampleUrl;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ExampleParser exampleParser;

    public Set<Example> request(String word) {
        JsonNode examples = restTemplate.getForObject(exampleUrl + word, JsonNode.class);

        return exampleParser.parse(examples);
    }
}
