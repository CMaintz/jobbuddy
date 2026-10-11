package com.autoapplicant.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.domain.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(UserPersistenceAdapter.class)
class UserPersistenceAdapterTest extends PostgresIntegrationTest {

  @Autowired UserPersistenceAdapter users;

  @Test
  void finds_an_account_by_email_regardless_of_case() {
    User saved =
        users.save(
            new User(null, "Foo@Example.com", null, null, null, UserRole.USER, true, false, null,
                null));

    assertThat(users.findByEmail("foo@example.com")).map(User::id).contains(saved.id());
  }
}
