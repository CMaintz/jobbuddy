package com.autoapplicant.port.in.user;

import com.autoapplicant.domain.user.User;

public interface RegisterUserUseCase {
    User register(String email, String password, String fullName);
}
