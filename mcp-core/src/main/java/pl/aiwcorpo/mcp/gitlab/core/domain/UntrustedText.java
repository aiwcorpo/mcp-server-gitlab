package pl.aiwcorpo.mcp.gitlab.core.domain;

/**
 * Free text written by GitLab users (file content, titles, names, labels, branches, descriptions), handed to
 * the model as data. Anyone with write access to a repository or a merge request controls this text, so it is
 * wrapped rather than inlined: the client sees {@code kind = untrusted-data} next to the text. The shape is
 * {@code UntrustedText} of the contracts (CC-072); {@code origin}, what the text is, is a field of this system
 * next to the four required ones.
 *
 * @param kind      always {@link #KIND}
 * @param format    {@link #PLAIN} or {@link #MARKDOWN}
 * @param origin    what the text is, e.g. {@code gitlab-file-content}
 * @param text      the text, cut to the limit of the field
 * @param truncated whether {@code text} was cut
 */
public record UntrustedText(String kind, String format, String origin, String text, boolean truncated) {

    public static final String KIND = "untrusted-data";
    public static final String PLAIN = "text/plain";
    public static final String MARKDOWN = "text/markdown";

    /** Cuts {@code text} to {@code maxChars} without splitting a surrogate pair; {@code null} for no text. */
    public static UntrustedText of(String origin, String format, String text, int maxChars) {
        if (text == null) {
            return null;
        }
        if (text.length() <= maxChars) {
            return new UntrustedText(KIND, format, origin, text, false);
        }
        int end = Character.isHighSurrogate(text.charAt(maxChars - 1)) ? maxChars - 1 : maxChars;
        return new UntrustedText(KIND, format, origin, text.substring(0, end), true);
    }
}
