package pl.aiwcorpo.mcp.gitlab.app.config;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import io.modelcontextprotocol.server.McpServer;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.mcp.customizer.McpSyncServerCustomizer;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.support.ToolDefinitions;
import org.springframework.ai.tool.support.ToolUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;
import pl.aiwcorpo.mcp.gitlab.tools.ArgumentLimits;
import pl.aiwcorpo.mcp.gitlab.tools.GitLabTools;
import pl.aiwcorpo.mcp.platform.guard.ToolRisk;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The tool set of the server, as an explicit list: the MCP server exposes only tools registered through a
 * {@link ToolCallbackProvider} (an {@code @Tool} bean alone answers "Unknown tool"), and a reviewed list
 * keeps the public surface from growing by accident.
 *
 * <p>The callbacks are the {@link MethodToolCallback}s Spring AI would build, with the {@link ArgumentLimits}
 * of each argument added to the generated input schema (a text argument without a length limit stops the
 * server from starting) and wrapped in {@link StrictArgumentsToolCallback}, which refuses undeclared
 * arguments. The MCP SDK's own input validation is off: it answers schema violations itself, before the
 * starter, with a text that is not a C10 error and without an audit event. The tools check every argument
 * themselves, so nothing reaches GitLab unchecked.
 */
@Configuration
public class McpToolsConfiguration {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Bean
    ToolCallbackProvider mcpTools(GitLabTools gitLabTools) {
        return ToolCallbackProvider.from(withArgumentLimits(gitLabTools));
    }

    /**
     * Spring AI takes exactly one customizer, so this one replaces its servlet default and has to repeat what
     * that default does: {@code immediateExecution(true)} keeps the tool on the request thread, where the
     * starter's CredentialContext lives (without it every call would run with no credential; the tool tests
     * would catch that, as they check the credential reaching GitLab).
     */
    @Bean
    @Primary
    McpSyncServerCustomizer toolInputsCheckedByTheTools() {
        return (McpServer.SyncSpecification<?> server) -> server.immediateExecution(true).validateToolInputs(false);
    }

    static List<ToolCallback> withArgumentLimits(Object... toolObjects) {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (Object tools : toolObjects) {
            Method[] methods = ReflectionUtils.getDeclaredMethods(AopUtils.getTargetClass(tools));
            for (Method method : methods) {
                if (!method.isAnnotationPresent(Tool.class)) {
                    continue;
                }
                ToolDefinition generated = ToolDefinitions.from(method);
                MethodToolCallback callback = MethodToolCallback.builder()
                        .toolDefinition(DefaultToolDefinition.builder()
                                .name(generated.name())
                                .description(generated.description())
                                .inputSchema(limitedSchema(generated, method))
                                .build())
                        .toolMetadata(ToolMetadata.from(method))
                        .toolMethod(method)
                        .toolObject(tools)
                        .toolCallResultConverter(ToolUtils.getToolCallResultConverter(method))
                        .build();
                ToolRisk risk = AnnotatedElementUtils.findMergedAnnotation(method, ToolRisk.class);
                Map<String, Class<?>> declared = new LinkedHashMap<>();
                for (Parameter parameter : method.getParameters()) {
                    if (parameter.getType() != String.class && parameter.getType() != Long.class) {
                        throw new IllegalStateException("Tool '" + generated.name() + "': argument '" + parameter.getName()
                                + "' must be String or Long, the types the argument check knows");
                    }
                    declared.put(parameter.getName(), parameter.getType());
                }
                callbacks.add(new StrictArgumentsToolCallback(callback, risk == null ? null : risk.value(), declared));
            }
        }
        callbacks.sort(Comparator.comparing(callback -> callback.getToolDefinition().name()));
        return callbacks;
    }

    private static String limitedSchema(ToolDefinition generated, Method method) {
        ObjectNode schema = (ObjectNode) JSON.readTree(generated.inputSchema());
        JsonNode properties = schema.path("properties");
        for (Parameter parameter : method.getParameters()) {
            JsonNode node = properties.path(parameter.getName());
            if (!(node instanceof ObjectNode property)) {
                throw new IllegalStateException("Tool '" + generated.name() + "': no schema property for argument '"
                        + parameter.getName() + "' (compile with -parameters)");
            }
            ArgumentLimits limits = parameter.getAnnotation(ArgumentLimits.class);
            boolean text = parameter.getType() == String.class;
            if (text && (limits == null || limits.maxLength() < 0)) {
                throw new IllegalStateException("Tool '" + generated.name() + "': text argument '"
                        + parameter.getName() + "' needs @ArgumentLimits(maxLength = ...)");
            }
            if (limits == null) {
                continue;
            }
            if (limits.minLength() >= 0) {
                property.put("minLength", limits.minLength());
            }
            if (limits.maxLength() >= 0) {
                property.put("maxLength", limits.maxLength());
            }
            if (limits.minimum() != Long.MIN_VALUE) {
                property.put("minimum", limits.minimum());
            }
            if (limits.maximum() != Long.MAX_VALUE) {
                property.put("maximum", limits.maximum());
            }
        }
        return JSON.writeValueAsString(schema);
    }
}
