package com.ticketya.preferences.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

/**
 * Define la base de MongoDB de forma explícita: la URI puede omitir la base y
 * el nombre se toma de {@code PREFERENCES_MONGODB_DATABASE}. La colección usada
 * es {@code customer_preferences}.
 */
@Configuration
public class MongoConfiguration {

    @Bean
    MongoDatabaseFactory mongoDatabaseFactory(PreferencesProperties properties) {
        MongoClient client = MongoClients.create(properties.mongodb().uri());
        return new SimpleMongoClientDatabaseFactory(client, properties.mongodb().database());
    }
}
