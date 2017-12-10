package com.vings.words.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;

@Data
@AllArgsConstructor
@UserDefinedType
public class Image {

    private String key;

    private String url;
}
