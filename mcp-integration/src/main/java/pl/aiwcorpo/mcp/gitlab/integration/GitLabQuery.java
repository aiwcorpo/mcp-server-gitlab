package pl.aiwcorpo.mcp.gitlab.integration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A request path with its variables. Everything that comes from an argument is a variable, never part of the template,
 * so the URI builder encodes it as a whole ({@code team-a/app} becomes {@code team-a%2Fapp}) and an argument cannot add
 * a path segment or a query parameter. A parameter with a {@code null} value is left out.
 */
final class GitLabQuery {

    private final StringBuilder template;
    private final Map<String, Object> variables = new LinkedHashMap<>();
    private boolean hasQuery;

    GitLabQuery(String pathTemplate) {
        this.template = new StringBuilder(pathTemplate);
        this.hasQuery = pathTemplate.indexOf('?') >= 0;
    }

    /** A variable of the path template. */
    GitLabQuery with(String name, Object value) {
        variables.put(name, value);
        return this;
    }

    GitLabQuery param(String name, Object value) {
        if (value != null) {
            template.append(hasQuery ? '&' : '?').append(name).append("={q_").append(name).append('}');
            variables.put("q_" + name, value);
            hasQuery = true;
        }
        return this;
    }

    /** A constant parameter, written by the server and never taken from an argument. */
    GitLabQuery fixed(String name, String value) {
        template.append(hasQuery ? '&' : '?').append(name).append('=').append(value);
        hasQuery = true;
        return this;
    }

    GitLabQuery page(int page, int perPage) {
        return fixed("per_page", Integer.toString(perPage)).fixed("page", Integer.toString(page));
    }

    String template() {
        return template.toString();
    }

    Map<String, Object> variables() {
        return variables;
    }
}
