package com.autoapplicant.usecase.notification;

import com.autoapplicant.domain.notification.Nudge;
import com.autoapplicant.domain.notification.UpcomingDate;
import com.autoapplicant.domain.user.UiLanguage;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.domain.user.UserPreferences;
import com.autoapplicant.port.in.notification.SendDueRemindersUseCase;
import com.autoapplicant.port.out.notification.NotificationPort;
import com.autoapplicant.port.out.user.PreferencesRepositoryPort;
import com.autoapplicant.port.out.user.UserLanguagePort;
import com.autoapplicant.port.out.user.UserRepositoryPort;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The daily reminder email.
 *
 * <p>Follow-up dates were being set and then only ever seen by someone who opened the screen they
 * live on, which is not where a person who has forgotten about a follow-up will look. Danish
 * advice on unsolicited applications is consistent that the follow-up is what makes them work, so
 * a follow-up nobody is told about is the feature failing quietly.
 *
 * <p>Only dates the user chose are sent: reminders and follow-ups that have arrived (from
 * {@link DueNudgeFinder#findDueToday}), and their own deadlines and interviews coming up in the
 * next few days ({@link DueNudgeFinder#findUpcoming}). The crawled posting's deadline and the
 * 14-day follow-up heuristic already surface in the app and do not change hour to hour; mailing
 * them daily would turn this into noise and get the whole thing filtered.
 *
 * <p>The mail is written in the language the user reads the app in, Danish unless they chose
 * otherwise.
 */
@Service
public class DueReminderDigestService implements SendDueRemindersUseCase {

    private static final Logger log = LoggerFactory.getLogger(DueReminderDigestService.class);

    private final PreferencesRepositoryPort preferencesRepo;
    private final UserRepositoryPort userRepo;
    private final UserLanguagePort languages;
    private final DueNudgeFinder dueNudges;
    private final NotificationPort notification;

    public DueReminderDigestService(PreferencesRepositoryPort preferencesRepo,
                                    UserRepositoryPort userRepo,
                                    UserLanguagePort languages,
                                    DueNudgeFinder dueNudges,
                                    NotificationPort notification) {
        this.preferencesRepo = preferencesRepo;
        this.userRepo = userRepo;
        this.languages = languages;
        this.dueNudges = dueNudges;
        this.notification = notification;
    }

    @Override
    public int sendDueReminders() {
        List<UserPreferences> optedIn = preferencesRepo.findAllWithNotificationsEnabled();
        if (optedIn.isEmpty()) return 0;

        int sent = 0;
        for (UserPreferences prefs : optedIn) {
            if (sendDigestTo(prefs)) sent++;
        }
        if (sent > 0) log.info("Due-reminder digest sent to {} user(s)", sent);
        return sent;
    }

    /**
     * Mails one user their digest, if there is anything to say. Returns whether a mail went out:
     * false when the user has no usable email or nothing is due or coming up (that is not news).
     */
    private boolean sendDigestTo(UserPreferences prefs) {
        Optional<User> user = userRepo.findById(prefs.userId());
        if (user.isEmpty() || user.get().email() == null || user.get().email().isBlank()) return false;

        List<Nudge> due = dueNudges.findDueToday(prefs.userId());
        List<UpcomingDate> upcoming = dueNudges.findUpcoming(prefs.userId());
        if (due.isEmpty() && upcoming.isEmpty()) return false;

        ReminderDigestText text = new ReminderDigestText(
                languages.findLanguage(prefs.userId()).orElse(UiLanguage.DA));
        notification.send(user.get().email(), text.subject(due.size() + upcoming.size()),
                text.body(due, upcoming));
        return true;
    }
}
