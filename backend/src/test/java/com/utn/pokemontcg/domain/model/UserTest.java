package com.utn.pokemontcg.domain.model;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void constructor_initializesUpdatedAtForPostgresNotNullConstraint() {
        User user = new User("ash", "ash@example.com", "hash");

        assertThat(ReflectionTestUtils.getField(user, "updatedAt")).isNotNull();
    }
}
