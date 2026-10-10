package com.minecraftdiscordbridge.core.console;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ConsoleRedactor {
    static final String HIDDEN = "[hidden]";
    static final String ADDRESS = "[ip]";
    private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;?]*[ -/]*[@-~]");
    private static final Pattern WEBHOOK =
            Pattern.compile("(?i)https?://(?:[a-z0-9-]+\\.)*discord(?:app)?\\.com/api/(?:v\\d+/)?webhooks/\\S+");
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{24,}\\.[A-Za-z0-9_-]{6}\\.[A-Za-z0-9_-]{27,}");
    private static final Pattern PASSWORD_COMMAND =
            Pattern.compile("(?i)(/(?:login|l|register|reg|changepassword))(?=\\s)[^\\r\\n]*");
    private static final Pattern IPV4 = Pattern.compile("(?<![0-9.])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![0-9]|\\.[0-9])");
    private static final Pattern IPV6 =
            Pattern.compile("(?<![A-Za-z0-9_:.])(?:[0-9A-Fa-f]{0,4}:){2,7}[0-9A-Fa-f]{0,4}(?![A-Za-z0-9_:])");

    private ConsoleRedactor() {}

    static String redact(String line, Collection<String> secrets) {
        String text = ANSI.matcher(line).replaceAll("");
        text = WEBHOOK.matcher(text).replaceAll(HIDDEN);
        for (String secret : secrets) {
            if (!secret.isEmpty()) {
                text = text.replace(secret, HIDDEN);
            }
        }
        text = TOKEN.matcher(text).replaceAll(HIDDEN);
        text = PASSWORD_COMMAND.matcher(text).replaceAll("$1 " + Matcher.quoteReplacement(HIDDEN));
        text = IPV4.matcher(text).replaceAll(ADDRESS);
        return IPV6.matcher(text).replaceAll(match -> isIpv6(match.group()) ? ADDRESS : match.group());
    }

    private static boolean isIpv6(String candidate) {
        boolean hasHexDigit = candidate.chars().anyMatch(c -> Character.digit(c, 16) >= 0);
        long colons = candidate.chars().filter(c -> c == ':').count();
        return hasHexDigit && (candidate.contains("::") || colons == 7);
    }
}
