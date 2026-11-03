package pl.aiwcorpo.mcp.gitlab.application.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import pl.aiwcorpo.mcp.platform.text.TextHygiene;

/**
 * Turns the raw log of a CI job into text a model may read. GitLab masks the variables a project marked as masked and
 * nothing else, while job output regularly prints tokens, URLs with passwords and key files. The patterns below are the
 * shapes that are recognisable without knowing the secret; they are a second line behind GitLab's masking, not a
 * guarantee, and the result says how many strings were replaced.
 */
final class JobLogText {

    static final String REDACTED = "[redacted]";

    /** CSI sequences (colours, cursor moves, erase line) as the runner writes them. */
    private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;?]*[ -/]*[@-~]");

    /** {@code section_start:1700000000:name} and {@code section_end:...}, written by the runner for collapsible sections. */
    private static final Pattern SECTION_MARKER = Pattern.compile("section_(?:start|end):[0-9]{9,12}:[^\\r\\n]*\\r?");

    private static final Pattern[] CREDENTIALS = {
        Pattern.compile("\\b(?:glpat|gldt|glrt|glcbt|glptt|glft|glimt|glagent|gloas|glsoat|glffct|glrtr)-[A-Za-z0-9_-]{8,}"),
        Pattern.compile("\\bGR1348941[A-Za-z0-9_-]{16,}"),
        Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b"),
        Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?(?:-----END [A-Z ]*PRIVATE KEY-----|$)"),
        Pattern.compile("(?i)(?<=\\bauthorization:\\s{0,3}(?:bearer|basic|token)\\s{1,3})\\S+"),
        Pattern.compile("(?i)(?<=\\b(?:private-token|job-token|x-gitlab-token|x-api-key)\\s{0,3}[:=]\\s{0,3})[^\\s'\"]+"),
        Pattern.compile("(?<=https?://[^/\\s:@]{1,255}:)[^/\\s@]{1,255}(?=@)"),
    };

    record Cleaned(String text, int redactions) {
    }

    private JobLogText() {
    }

    static Cleaned clean(String raw) {
        String text = SECTION_MARKER.matcher(ANSI.matcher(raw).replaceAll("")).replaceAll("");
        text = TextHygiene.normalize(text);
        int redactions = 0;
        for (Pattern pattern : CREDENTIALS) {
            Matcher matcher = pattern.matcher(text);
            StringBuilder out = new StringBuilder();
            while (matcher.find()) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(REDACTED));
                redactions++;
            }
            matcher.appendTail(out);
            text = out.toString();
        }
        return new Cleaned(text, redactions);
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
