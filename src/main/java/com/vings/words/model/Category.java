package com.vings.words.model;

import com.datastax.driver.core.utils.UUIDs;
import lombok.Data;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

import java.util.UUID;

@Data
public class Category {

    @PrimaryKeyColumn(ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private String user;

    @PrimaryKeyColumn(ordinal = 1, type = PrimaryKeyType.CLUSTERED, ordering = Ordering.ASCENDING)
    private String title;

    private UUID id;

    public Category() {
    }

    public Category(String user, String title) {
        this(user, title, UUIDs.random());
    }

    public Category(String user, String title, UUID id) {
        this.user = user;
        this.title = title;
        this.id = id;
    }
}
