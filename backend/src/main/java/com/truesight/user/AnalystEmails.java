package com.truesight.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The emails that are Analysts: the setting {@code truesight.analyst-emails} (the {@code ANALYST_EMAILS} variable),
 * a comma-separated list. Nobody can make themselves an Analyst, because the list is only ever changed by whoever runs
 * the app.
 *
 * <p>The setting is read once, when the app starts. Capitals and spaces do not matter: {@code Analyst@Firm.com} in
 * the setting matches {@code analyst@firm.com}, which is how emails are stored (see
 * {@link com.truesight.auth.AuthService}).
 */
@Component
public class AnalystEmails {

    private final Set<String> emails;

    public AnalystEmails(@Value("${truesight.analyst-emails:}") String setting) {
        this.emails = Arrays.stream(setting.split(","))
                .map(AnalystEmails::normalise)
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Whether this email is listed in the setting. */
    public boolean isAnalyst(String email) {
        return email != null && emails.contains(normalise(email));
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
