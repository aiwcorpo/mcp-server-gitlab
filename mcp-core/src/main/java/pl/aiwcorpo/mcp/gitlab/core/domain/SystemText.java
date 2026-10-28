package pl.aiwcorpo.mcp.gitlab.core.domain;

import pl.aiwcorpo.mcp.platform.text.TextHygiene;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;

/**
 * Text that GitLab users wrote, as a result field. GitLab text is handed out as it is (Markdown stays Markdown, a
 * file stays a file) and the client never renders it as HTML, so there is no conversion and no server filter in
 * the order the starter prescribes: normalize, cut, (filter), build the value from the cut text.
 */
public final class SystemText {

    private SystemText() {
    }

    /** @return {@code null} for a missing or empty text, which the result records mark {@code @Nullable} */
    public static UntrustedText plain(String raw, int maxLength) {
        return raw == null || raw.isEmpty() ? null : plainOrEmpty(raw, maxLength);
    }

    /** @return {@code null} for a missing or empty text */
    public static UntrustedText markdown(String raw, int maxLength) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String clean = TextHygiene.normalize(raw);
        String kept = UntrustedText.cut(clean, maxLength);
        return UntrustedText.markdownCut(kept, kept.length() < clean.length());
    }

    /**
     * How many characters {@link TextHygiene#normalize} drops without a trace in the text: C0/C1 controls (other
     * than tab and line breaks), unpaired surrogates, noncharacters and every format character. Line and
     * paragraph separators become newlines and are not counted.
     */
    public static int hiddenCharacters(String raw) {
        return (int) raw.codePoints().filter(SystemText::isDroppedByNormalize).count();
    }

    private static boolean isDroppedByNormalize(int point) {
        if (point == '\n' || point == '\t' || point == '\r' || point == 0x2028 || point == 0x2029 || point == 0x85) {
            return false;
        }
        return Character.isISOControl(point)
                || (point >= Character.MIN_SURROGATE && point <= Character.MAX_SURROGATE)
                || (point >= 0xFDD0 && point <= 0xFDEF) || (point & 0xFFFE) == 0xFFFE
                || Character.getType(point) == Character.FORMAT;
    }

    /** For a field that is always present, such as the content of a file: an empty file is an empty text. */
    public static UntrustedText plainOrEmpty(String raw, int maxLength) {
        String clean = TextHygiene.normalize(raw);
        String kept = UntrustedText.cut(clean, maxLength);
        return UntrustedText.plainCut(kept, kept.length() < clean.length());
    }
}
