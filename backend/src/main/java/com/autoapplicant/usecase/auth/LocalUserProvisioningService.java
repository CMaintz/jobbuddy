package com.autoapplicant.usecase.auth;

import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.domain.user.UserRole;
import com.autoapplicant.port.in.auth.ProvisionLocalUserUseCase;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import com.autoapplicant.port.out.user.UserRepositoryPort;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    // Creation gets a transaction of its own, so a losing insert rolls back on its own and the
    // re-read below runs outside it: Postgres refuses every statement in an aborted transaction.
    private final TransactionTemplate creation;

    public LocalUserProvisioningService(UserRepositoryPort userRepo,
                                        ProfileRepositoryPort profileRepo,
                                        PlatformTransactionManager transactions) {
        this.userRepo = userRepo;
        this.profileRepo = profileRepo;
        this.creation = new TransactionTemplate(transactions);
    }

    @Override
    public User provisionLocalUser() {
        return userRepo.findByEmail(LOCAL_EMAIL).orElseGet(this::createOrAdoptLocalUser);
    }

    private User createOrAdoptLocalUser() {
        try {
            return creation.execute(status -> createLocalUser());
        } catch (DataIntegrityViolationException concurrentInsert) {
            // Parallel requests right after the account was deleted both found no user and both
            // inserted; the other one won. Its row is the local user.
            return userRepo.findByEmail(LOCAL_EMAIL).orElseThrow(() -> concurrentInsert);
        }
    }

    // USER, not ADMIN: the admin endpoints run crawls and LinkedIn plans on demand, outside their
    // schedules, and edit data shared across users, of whom there is one here.
    private User createLocalUser() {
        User saved = userRepo.save(new User(null, LOCAL_EMAIL, null, null, null,
                UserRole.USER, true, false, null, null));
        profileRepo.save(new Profile(null, saved.id(), null, null, null, List.of(), List.of(),
                null, null, DEFAULT_CURRENCY, null, null, null, null));
        return saved;
    }
}
