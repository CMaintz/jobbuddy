package com.autoapplicant.usecase.notification;

import com.autoapplicant.domain.notification.Nudge;
import com.autoapplicant.domain.notification.UpcomingDate;
import com.autoapplicant.domain.user.UiLanguage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The words of the daily reminder email, in the user's language. Plain text: the mail is read in
 * whatever client the user has, often on a phone.
 */
final class ReminderDigestText {

    private static final String BULLET = "• ";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final UiLanguage language;
    private final DateTimeFormatter date;

    ReminderDigestText(UiLanguage language) {
        this.language = language;
        this.date = DateTimeFormatter.ofPattern(danish() ? "d. MMMM" : "d MMMM", language.locale());
    }

    String subject(int count) {
        if (danish()) return count + (count == 1 ? " påmindelse" : " påmindelser") + " fra Jobbuddy";
        return count + (count == 1 ? " reminder" : " reminders") + " from Jobbuddy";
    }

    String body(List<Nudge> dueToday, List<UpcomingDate> upcoming) {
        StringBuilder sb = new StringBuilder();
        section(sb, danish() ? "I dag:" : "Due today:", dueToday.stream().map(this::describe).toList());
        section(sb, danish() ? "Snart:" : "Coming up:", upcoming.stream().map(this::describe).toList());
        return sb.append(danish() ? "Åbn Jobbuddy for at se det hele." : "Open Jobbuddy to see everything.")
                .toString();
    }

    private static void section(StringBuilder sb, String heading, List<String> lines) {
        if (lines.isEmpty()) return;
        sb.append(heading).append('\n');
        lines.forEach(line -> sb.append(BULLET).append(line).append('\n'));
        sb.append('\n');
    }

    private String describe(Nudge nudge) {
        if (nudge.type() == Nudge.NudgeType.OUTREACH_FOLLOW_UP) {
            String who = isSet(nudge.note()) ? " (" + nudge.note() + ")" : "";
            String company = isSet(nudge.companyName())
                    ? nudge.companyName() : (danish() ? "en gemt virksomhed" : "a saved company");
            return (danish() ? "Følg op hos " : "Follow up with ") + company + who;
        }
        String what = isSet(nudge.note()) ? nudge.note() : (danish() ? "Påmindelse" : "Reminder");
        String job = job(nudge.jobTitle(), nudge.companyName());
        return job.isEmpty() ? what : what + ": " + job;
    }

    private String describe(UpcomingDate upcoming) {
        String when = upcoming.kind() == UpcomingDate.Kind.DEADLINE
                ? (danish() ? "Ansøgningsfrist " : "Deadline ") + format(upcoming.date())
                : (danish() ? "Samtale " : "Interview ") + format(upcoming.date())
                        + (danish() ? " kl. " : " at ") + TIME.format(upcoming.time());
        String job = job(upcoming.jobTitle(), upcoming.companyName());
        return job.isEmpty() ? when : when + ": " + job;
    }

    private String job(String title, String company) {
        if (!isSet(title)) return isSet(company) ? company : "";
        return isSet(company) ? title + (danish() ? " hos " : " at ") + company : title;
    }

    private String format(LocalDate day) {
        return date.format(day);
    }

    private boolean danish() {
        return language == UiLanguage.DA;
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
