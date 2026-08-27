package com.autoapplicant.usecase.notification;

import com.autoapplicant.domain.notification.Nudge;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.domain.user.UserPreferences;
import com.autoapplicant.port.in.notification.GetNudgesUseCase;
import com.autoapplicant.port.in.notification.SendDueRemindersUseCase;
import com.autoapplicant.port.out.notification.NotificationPort;
import com.autoapplicant.port.out.user.PreferencesRepositoryPort;
import com.autoapplicant.port.out.user.UserRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The daily "this is due today" email.
 *
 * <p>Follow-up dates were being set and then only ever seen by someone who opened the screen they
 * live on — which is not where a person who has forgotten about a follow-up will look. Danish
 * advice on unsolicited applications is consistent that the follow-up is what makes them work, so
 * a follow-up nobody is told about is the feature failing quietly.
 *
 * <p>Only the time-critical nudge types are sent. Deadlines and the 14-day follow-up heuristic
 * already surface in the app and do not change hour to hour; mailing them daily would turn this
 * into noise and get the whole thing filtered.
 */
@Service
public class DueReminderDigestService implements SendDueRemindersUseCase {

    private static final Logger log = LoggerFactory.getLogger(DueReminderDigestService.class);

    /** What is worth an email: things with a date the user chose, that have arrived. */
    private static final Set<Nudge.NudgeType> MAILED_TYPES =
            EnumSet.of(Nudge.NudgeType.REMINDER_DUE, Nudge.NudgeType.OUTREACH_FOLLOW_UP);

    private final PreferencesRepositoryPort preferencesRepo;
    private final UserRepositoryPort userRepo;
    private final GetNudgesUseCase nudges;
    private final NotificationPort notification;

    public DueReminderDigestService(PreferencesRepositoryPort preferencesRepo,
                                    UserRepositoryPort userRepo,
                                    GetNudgesUseCase nudges,
                                    NotificationPort notification) {
        this.preferencesRepo = preferencesRepo;
        this.userRepo = userRepo;
        this.nudges = nudges;
        this.notification = notification;
    }

    @Override
    public int sendDueReminders() {
        List<UserPreferences> optedIn = preferencesRepo.findAllWithNotificationsEnabled();
        if (optedIn.isEmpty()) return 0;

        int sent = 0;
        for (UserPreferences prefs : optedIn) {
            Optional<User> user = userRepo.findById(prefs.userId());
            if (user.isEmpty() || user.get().email() == null || user.get().email().isBlank()) continue;

            List<Nudge> due = nudges.getNudges(prefs.userId()).stream()
                    .filter(nudge -> MAILED_TYPES.contains(nudge.type()))
                    .toList();
            if (due.isEmpty()) continue;   // nothing due is not news

            notification.send(user.get().email(), subject(due), body(due));
            sent++;
        }
        if (sent > 0) log.info("Due-reminder digest sent to {} user(s)", sent);
        return sent;
    }

    private static String subject(List<Nudge> due) {
        return due.size() == 1 ? "1 follow-up due today" : due.size() + " follow-ups due today";
    }

    private static String body(List<Nudge> due) {
        StringBuilder sb = new StringBuilder("Due today:\n\n");
        for (Nudge nudge : due) {
            sb.append("• ").append(describe(nudge)).append('\n');
        }
        return sb.append("\nOpen the app to mark them done.").toString();
    }

    private static String describe(Nudge nudge) {
        if (nudge.type() == Nudge.NudgeType.OUTREACH_FOLLOW_UP) {
            String who = nudge.note() != null && !nudge.note().isBlank()
                    ? " (" + nudge.note() + ")" : "";
            return "Follow up with " + orUnknown(nudge.companyName()) + who;
        }
        String what = nudge.note() != null && !nudge.note().isBlank() ? nudge.note() : "Reminder";
        String where = nudge.jobTitle() != null
                ? " — " + nudge.jobTitle()
                  + (nudge.companyName() != null ? " at " + nudge.companyName() : "")
                : "";
        return what + where;
    }

    private static String orUnknown(String value) {
        return value != null && !value.isBlank() ? value : "a saved company";
    }
}
