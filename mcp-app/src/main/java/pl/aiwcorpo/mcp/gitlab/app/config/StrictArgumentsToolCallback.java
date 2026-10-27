package pl.aiwcorpo.mcp.gitlab.app.config;

import java.util.Map;
import java.util.TreeSet;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.method.MethodToolCallback;
import pl.aiwcorpo.mcp.platform.error.InvalidToolArgumentException;
import pl.aiwcorpo.mcp.platform.guard.RiskClassifiedTool;
import pl.aiwcorpo.mcp.platform.guard.RiskLevel;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A {@code @Tool} method that refuses arguments its schema does not declare ({@code additionalProperties:
 * false}) and arguments of the wrong JSON type (a string where an integer is declared would otherwise fail in
 * Spring AI's conversion and reach the client as {@code internal_error}). The MCP SDK's own schema check is switched off (see {@link McpToolsConfiguration}) because it
 * answers before the starter with a non-C10 text and no audit event; this check fails inside the tool call,
 * so the client gets {@code invalid_argument} and the call is audited.
 *
 * <p>Wrapping hides the method from the starter's risk registry, so the {@code @ToolRisk} of the method is
 * passed on through {@link RiskClassifiedTool}; a method without it yields {@code null} and the server does
 * not start.
 */
final class StrictArgumentsToolCallback implements ToolCallback, RiskClassifiedTool {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MethodToolCallback delegate;
    private final RiskLevel risk;
    private final Map<String, Class<?>> declared;

    /** @param declared argument name -> Java type of the method parameter */
    StrictArgumentsToolCallback(MethodToolCallback delegate, RiskLevel risk, Map<String, Class<?>> declared) {
        this.delegate = delegate;
        this.risk = risk;
        this.declared = Map.copyOf(declared);
    }

    @Override
    public RiskLevel riskLevel() {
        return risk;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        checkArguments(toolInput);
        return delegate.call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        checkArguments(toolInput);
        return delegate.call(toolInput, toolContext);
    }

    private void checkArguments(String toolInput) {
        JsonNode arguments;
        try {
            arguments = toolInput == null || toolInput.isBlank() ? JSON.createObjectNode() : JSON.readTree(toolInput);
        } catch (JacksonException notJson) {
            throw new InvalidToolArgumentException("arguments must be a JSON object");
        }
        if (!arguments.isObject()) {
            throw new InvalidToolArgumentException("arguments must be a JSON object");
        }
        for (Map.Entry<String, JsonNode> argument : arguments.properties()) {
            Class<?> type = declared.get(argument.getKey());
            if (type == null) {
                // The unknown name is not echoed: it is model-chosen text of any length.
                throw new InvalidToolArgumentException("unknown argument; accepted arguments: "
                        + String.join(", ", new TreeSet<>(declared.keySet())));
            }
            JsonNode value = argument.getValue();
            boolean fits = value.isNull()
                    || (type == String.class && value.isString())
                    || (type == Long.class && value.isIntegralNumber());
            if (!fits) {
                throw new InvalidToolArgumentException(argument.getKey() + " must be "
                        + (type == String.class ? "a string" : "an integer"));
            }
        }
    }
}
