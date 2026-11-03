package pl.aiwcorpo.mcp.gitlab.core.domain;

import org.jspecify.annotations.Nullable;
import pl.aiwcorpo.mcp.platform.text.UntrustedText;

/**
 * The change of one file. {@code diff} is cut at a size limit per file; GitLab itself leaves it empty for a change it
 * considers too large, and for binary files.
 *
 * @param hiddenCharactersRemoved invisible and direction-changing characters the text hygiene dropped from the diff,
 *                                counted before the cut: in code review they can make a line read differently from
 *                                what it does
 */
public record FileDiff(@Nullable UntrustedText oldPath, UntrustedText newPath, boolean newFile, boolean renamedFile,
        boolean deletedFile, int hiddenCharactersRemoved, UntrustedText diff) {
}
