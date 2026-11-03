package pl.aiwcorpo.mcp.gitlab.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * The masking of job logs. The fake secrets are assembled from parts so that no file of this repository contains a string
 * that a secret scanner takes for a credential.
 */
class JobLogTextTest {

    private static final int LIMIT = 8 * 1024 * 1024;
    private static final Duration BUDGET = Duration.ofSeconds(5);

    private static String join(String... parts) {
        return String.join("", parts);
    }

    private static String mask(String line) {
        return JobLogText.clean(line).text();
    }

    @Test
    void credentialShapesAreMasked() {
        String gitlabToken = join("gl", "pat", "-", "AbCdEfGhIjKlMnOp12");
        String githubToken = join("gh", "p_", "A".repeat(36));
        String slackToken = join("xo", "xb-", "1234567890-abcdefghij");
        String jwt = join("ey", "JhbGciOiJIUzI1NiJ9", ".", "eyJzdWIiOiIxMjM0NTY3ODkwIn0", ".", "c2lnbmF0dXJl");
        for (String secret : new String[] {gitlabToken, githubToken, slackToken, jwt}) {
            assertThat(mask("value " + secret + " end")).as(secret).isEqualTo("value [redacted] end");
        }
    }

    @Test
    void passwordsInUrlsOfAnySchemeAreMasked() {
        assertThat(mask("clone https://ci:" + "hunter2" + "@host/x.git")).isEqualTo("clone https://[redacted]@host/x.git");
        assertThat(mask("postgres://app:" + "hunter2" + "@db:5432/app")).isEqualTo("postgres://[redacted]@db:5432/app");
        assertThat(mask("redis://:" + "hunter2" + "@cache")).isEqualTo("redis://[redacted]@cache");
        assertThat(mask("https://user:p@" + "ss" + "@host")).as("a password with '@'").isEqualTo("https://[redacted]@host");
        assertThat(mask("https://user@host/x")).as("no password, nothing to hide").isEqualTo("https://user@host/x");
        assertThat(mask("https://host:8080/path")).isEqualTo("https://host:8080/path");
    }

    @Test
    void authorizationHeadersAndJsonFormsAreMasked() {
        assertThat(mask("Authorization: Bearer " + "abc.def")).isEqualTo("Authorization: Bearer [redacted]");
        assertThat(mask("{\"Authorization\": \"Bearer " + "abc.def" + "\"}")).isEqualTo("{\"Authorization\": \"Bearer [redacted]\"}");
        assertThat(mask("PRIVATE-TOKEN: " + "value123")).isEqualTo("PRIVATE-TOKEN: [redacted]");
    }

    @Test
    void assignmentsOfNamesThatHoldSecretsAreMasked() {
        for (String line : new String[] {"PASSWORD=", "AWS_SECRET_ACCESS_KEY=", "export DB_PASSWD: ", "CI_JOB_TOKEN=", "api_key = ",
                "\"client_secret\": \""}) {
            String masked = mask(line + "s3cret-value");
            assertThat(masked).as(line).contains("[redacted]").doesNotContain("s3cret-value");
        }
        assertThat(mask("the password was rotated")).as("prose without an assignment").isEqualTo("the password was rotated");
    }

    @Test
    void commandLineSecretsAreMasked() {
        assertThat(mask("docker login registry -u ci -p " + "hunter2")).isEqualTo("docker login registry -u ci -p [redacted]");
        assertThat(mask("curl -s -u deploy:" + "hunter2" + " https://x/y")).isEqualTo("curl -s -u deploy:[redacted] https://x/y");
    }

    @Test
    void aPrivateKeyBlockIsMaskedWithAndWithoutItsEndLine() {
        String begin = join("-----BEGIN ", "RSA PRIVATE KEY-----");
        String end = join("-----END ", "RSA PRIVATE KEY-----");
        assertThat(mask("a\n" + begin + "\nMIIE\n" + end + "\nb")).isEqualTo("a\n[redacted]\nb");
        String unfinished = mask("a\n" + begin + "\n" + "MIIE\n".repeat(5000) + "tail");
        assertThat(unfinished).startsWith("a\n[redacted]").hasSizeLessThan(5000 * 5 - 8000).endsWith("tail");
        assertThat(mask(join("-----BEGIN ", "CERTIFICATE-----"))).as("public material stays").contains("CERTIFICATE");
    }

    @Test
    void escapesAndSectionMarkersAreRemoved() {
        String esc = "\u001b";
        assertThat(mask("section_start:1762070405:build\r" + esc + "[0K" + esc + "[36;1mBuilding" + esc + "[0;m"))
                .isEqualTo("Building");
    }

    @Test
    void aLogOfTheSizeLimitIsMaskedInTimeWhateverItHolds() {
        String[] hostile = {
            "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\n",
            "https://" + "a".repeat(250) + ":",
            "://" + "a".repeat(600) + "\n",
            join("-----BEGIN ", "PRIVATE KEY-----\n"),
            "token=" + "a".repeat(3000) + " ",
            "a".repeat(5000) + "password ",
            "docker login " + " ".repeat(290) + "x ",
            "curl " + "-".repeat(290) + " ",
            "authorization: bearer " + "b".repeat(5000) + " ",
            "section_start:1762070405:" + "n".repeat(300) + "\r",
            join("gl", "pat-") + "z".repeat(5000) + " ",
        };
        for (String piece : hostile) {
            String log = piece.repeat(LIMIT / piece.length() + 1).substring(0, LIMIT);
            JobLogText.Cleaned cleaned = assertTimeoutPreemptively(BUDGET, () -> JobLogText.clean(log), piece.substring(0, 12));
            assertThat(cleaned.text().length()).isLessThanOrEqualTo(JobLogText.WINDOW_CHARS);
        }
    }

    @Test
    void theTailKeepsWholeLinesAndSaysWhenItCut() {
        String log = "one\ntwo\nthree\nfour\n";
        assertThat(JobLogText.tail(log, 2, 1000)).isEqualTo(new JobLogText.Tail("three\nfour", true));
        assertThat(JobLogText.tail(log, 10, 1000)).isEqualTo(new JobLogText.Tail("one\ntwo\nthree\nfour", false));
        assertThat(JobLogText.tail(log, 10, 9).text()).isEqualTo("four");
        assertThat(JobLogText.tail("x".repeat(50), 10, 8)).isEqualTo(new JobLogText.Tail("x".repeat(8), true));
    }
}
