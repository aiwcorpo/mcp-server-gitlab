package pl.aiwcorpo.mcp.gitlab.application.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import pl.aiwcorpo.mcp.platform.text.TextHygiene;

/**
 * Turns the raw log of a CI job into text a model may read. GitLab masks the variables a project marked as masked and
 * nothing else, while job output regularly prints tokens, URLs with passwords and key files. The patterns below are the
 * shapes that are recognisable without knowing the secret. This is best effort: a secret in a shape not listed here passes,
 * and the log is data written by whoever can run a job in the project, so a reader must treat it as untrusted.
 *
 * <p>The work is bounded: only the last {@value #WINDOW_CHARS} characters of the log are looked at (the part a tail
 * is cut from, with margin so that a block that starts before the tail is still masked as a whole), every quantifier of
 * every pattern has an upper bound, no pattern uses a lookbehind, and none can backtrack catastrophically. A log of any
 * size therefore costs time proportional to the window, not to the log.
 */
final class JobLogText {

    static final String REDACTED = "[redacted]";

    /** The part of the log that is cleaned and masked: far more than the tail that is returned. */
    static final int WINDOW_CHARS = 400_000;

    private static final int PEM_MAX_CHARS = 8_192;
    private static final String VALUE = "[^\\s\"',;]{1,2048}";

    /** CSI sequences (colours, cursor moves, erase line) as the runner writes them. */
    private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;?]{0,32}[ -/]{0,8}[@-~]");

    /** {@code section_start:1700000000:name} and {@code section_end:...}, written by the runner for collapsible sections. */
    private static final Pattern SECTION_MARKER = Pattern.compile("section_(?:start|end):[0-9]{9,12}:[^\\r\\n]{0,256}\\r?");

    /** Whole matches replaced by the marker: strings that are credentials by their own shape. */
    private static final Pattern[] WHOLE = {
        Pattern.compile("\\b(?:glpat|gldt|glrt|glcbt|glptt|glft|glimt|glagent|gloas|glsoat|glffct|glrtr)-[A-Za-z0-9_-]{8,256}"),
        Pattern.compile("\\b(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{20,256}"),
        Pattern.compile("\\bgithub_pat_[A-Za-z0-9_]{20,256}"),
        Pattern.compile("\\bxox[abposr]-[A-Za-z0-9-]{10,256}"),
        Pattern.compile("\\bGR1348941[A-Za-z0-9_-]{16,256}"),
        Pattern.compile("\\b(?:AKIA|ASIA)[0-9A-Z]{16}\\b"),
        Pattern.compile("\\beyJ[A-Za-z0-9_-]{10,2048}\\.[A-Za-z0-9_-]{10,2048}\\.[A-Za-z0-9_-]{5,2048}"),
    };

    /** Prefix kept, the value after it replaced. */
    private static final Pattern[] VALUE_AFTER = {
        // Authorization header, plain or inside JSON ("Authorization": "Bearer ...")
        Pattern.compile("(?i)(authorization[\"']{0,2}\\s{0,3}[:=]\\s{0,3}[\"']{0,2}(?:bearer|basic|token)\\s{1,3})" + VALUE),
        Pattern.compile("(?i)((?:private-token|job-token|x-gitlab-token|x-api-key)[\"']{0,2}\\s{0,3}[:=]\\s{0,3}[\"']{0,2})" + VALUE),
        // password=..., AWS_SECRET_ACCESS_KEY=..., GITLAB_TOKEN: ...
        Pattern.compile("(?i)(\\b[A-Za-z0-9_]{0,60}(?:password|passwd|secret|token|api_?key|private_?key)[A-Za-z0-9_]{0,60}[\"']{0,2}"
                + "\\s{0,3}[:=]\\s{0,3}[\"']{0,2})" + VALUE),
        // docker login ... -p secret / --password secret ; curl ... -u user:secret
        Pattern.compile("(?i)(\\bdocker\\s{1,4}login\\b[^\\n]{0,300}?\\s(?:-p|--password)(?:\\s{1,4}|=))[^\\s]{1,1024}"),
        Pattern.compile("(?i)(\\bcurl\\b[^\\n]{0,300}?\\s(?:-u|--user)(?:\\s{1,4}|=)[^\\s:]{1,255}:)[^\\s]{1,1024}"),
    };

    /** {@code scheme://user:password@host}: the whole userinfo is replaced; the password may itself contain '@'. */
    private static final Pattern URL_USERINFO = Pattern.compile("([A-Za-z][A-Za-z0-9+.-]{1,20}://)([^/\\s?#]{1,512})@");

    private static final String PEM_BEGIN = "-----BEGIN ";
    private static final String PEM_END = "-----END ";

    record Cleaned(String text, int redactions) {
    }

    private JobLogText() {
    }

    static Cleaned clean(String raw) {
        String window = raw.length() > WINDOW_CHARS ? raw.substring(raw.length() - WINDOW_CHARS) : raw;
        String text = SECTION_MARKER.matcher(ANSI.matcher(window).replaceAll("")).replaceAll("");
        text = TextHygiene.normalize(text);
        int[] redactions = {0};
        text = redactPrivateKeys(text, redactions);
        text = redactUserinfo(text, redactions);
        for (Pattern pattern : WHOLE) {
            text = replace(pattern, text, redactions, false);
        }
        for (Pattern pattern : VALUE_AFTER) {
            text = replace(pattern, text, redactions, true);
        }
        return new Cleaned(text, redactions[0]);
    }

    private static String replace(Pattern pattern, String text, int[] redactions, boolean keepPrefix) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder out = null;
        while (matcher.find()) {
            if (out == null) {
                out = new StringBuilder(text.length());
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement((keepPrefix ? matcher.group(1) : "") + REDACTED));
            redactions[0]++;
        }
        if (out == null) {
            return text;
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String redactUserinfo(String text, int[] redactions) {
        Matcher matcher = URL_USERINFO.matcher(text);
        StringBuilder out = null;
        while (matcher.find()) {
            String userinfo = matcher.group(2);
            if (userinfo.indexOf(':') < 0) {
                continue;
            }
            if (out == null) {
                out = new StringBuilder(text.length());
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group(1) + REDACTED + "@"));
            redactions[0]++;
        }
        if (out == null) {
            return text;
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * A private key block, from its BEGIN line to its END line, or to {@value #PEM_MAX_CHARS} characters when the END line
     * is missing. Scanned by hand so that a log full of BEGIN lines without an END cannot make the search quadratic.
     */
    private static String redactPrivateKeys(String text, int[] redactions) {
        int from = text.indexOf(PEM_BEGIN);
        if (from < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        int copied = 0;
        while (from >= 0) {
            int lineEnd = text.indexOf("-----", from + PEM_BEGIN.length());
            boolean privateKey = lineEnd >= 0 && lineEnd - from <= 64
                    && text.substring(from + PEM_BEGIN.length(), lineEnd).endsWith("PRIVATE KEY");
            if (!privateKey) {
                from = text.indexOf(PEM_BEGIN, from + PEM_BEGIN.length());
                continue;
            }
            int limit = Math.min(text.length(), from + PEM_MAX_CHARS);
            int end = text.indexOf(PEM_END, lineEnd + 5);
            int blockEnd;
            if (end >= 0 && end < limit) {
                int endLine = text.indexOf("-----", end + PEM_END.length());
                blockEnd = endLine >= 0 && endLine < limit + 64 ? endLine + 5 : limit;
            } else {
                blockEnd = limit;
            }
            out.append(text, copied, from).append(REDACTED);
            redactions[0]++;
            copied = blockEnd;
            from = text.indexOf(PEM_BEGIN, blockEnd);
        }
        out.append(text, copied, text.length());
        return out.toString();
    }

    record Tail(String text, boolean cut) {
    }

    /**
     * The last {@code maxLines} lines that fit {@code maxChars}, whole lines only. One line longer than the limit is the
     * exception: it keeps its end, because dropping it would leave nothing.
     */
    static Tail tail(String text, int maxLines, int maxChars) {
        int length = text.length();
        while (length > 0 && text.charAt(length - 1) == '\n') {
            length--;
        }
        String body = text.substring(0, length);
        int start = length;
        int end = length;
        int lines = 0;
        while (end > 0 && lines < maxLines) {
            int lineStart = body.lastIndexOf('\n', end - 1) + 1;
            if (length - lineStart > maxChars) {
                break;
            }
            start = lineStart;
            lines++;
            end = lineStart - 1;
        }
        if (lines == 0 && length > 0) {
            start = Math.max(0, length - maxChars);
        }
        return new Tail(body.substring(start), start > 0);
    }
}
