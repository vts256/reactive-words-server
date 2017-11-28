package com.vings.words.model;

import lombok.Data;
import org.springframework.data.annotation.Id;

import java.util.UUID;

@Data
public class Category {

    @Id
    private String user;

    private String title;

    private UUID id;

    public Category() {
    }

    public Category(String user, String title, UUID id) {
        this.user = user;
        this.title = title;
        this.id = id;
    }
}
