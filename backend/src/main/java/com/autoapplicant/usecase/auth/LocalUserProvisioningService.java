package com.autoapplicant.usecase.auth;

import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.domain.user.UserRole;
import com.autoapplicant.port.in.auth.ProvisionLocalUserUseCase;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import com.autoapplicant.port.out.user.UserRepositoryPort;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@org.springframework.context.annotation.Profile("desktop")
public class LocalUserProvisioningService implements ProvisionLocalUserUseCase {

    /**
     * The key the local user is found by on every start. The .invalid domain (RFC 2606) can never
     * be a real mailbox, so nothing is ever mailed to a stranger by mistake.
     */
    static final String LOCAL_EMAIL = "local@jobbuddy.invalid";

    private static final String DEFAULT_CURRENCY = "DKK";

    private final UserRepositoryPort userRepo;
    private final ProfileRepositoryPort profileRepo;

    public LocalUserProvisioningService(UserRepositoryPort userRepo,
                                        ProfileRepositoryPort profileRepo) {
        this.userRepo = userRepo;
        this.profileRepo = profileRepo;
    }

    @Override
    @Transactional
    public User provisionLocalUser() {
        return userRepo.findByEmail(LOCAL_EMAIL).orElseGet(this::createLocalUser);
    }

    // USER, not ADMIN: the admin endpoints trigger crawls and LinkedIn runs, which the desktop
    // leaves to the scheduled workflow, and edit data shared across users, of whom there is one.
    private User createLocalUser() {
        User saved = userRepo.save(new User(null, LOCAL_EMAIL, null, null, null,
                UserRole.USER, true, false, null, null));
        profileRepo.save(new Profile(null, saved.id(), null, null, null, List.of(), List.of(),
                null, null, DEFAULT_CURRENCY, null, null, null, null));
        return saved;
    }
}
