package pl.aiwcorpo.mcp.gitlab.tools;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limits of a tool argument, published in the tool's {@code inputSchema} ({@code minLength}/{@code maxLength}
 * for text, {@code minimum}/{@code maximum} for integers). Spring AI's schema generator has no such keywords,
 * so mcp-app adds them when it registers the tools. The tool still checks every limit itself: the schema
 * tells the client what is accepted, it does not enforce anything.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ArgumentLimits {

    int minLength() default -1;

    int maxLength() default -1;

    long minimum() default Long.MIN_VALUE;

    long maximum() default Long.MAX_VALUE;
}
