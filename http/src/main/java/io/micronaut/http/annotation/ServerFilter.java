package io.micronaut.http.annotation;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.DefaultScope;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.filter.FilterPatternStyle;
import jakarta.inject.Singleton;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Mark a bean as a filter for the HTTP server. The bean may declare {@link RequestFilter}s and
 * {@link ResponseFilter}s.
 *
 * @since 4.0.0
 * @author Jonas Konrad
 */
@Documented
@Retention(RUNTIME)
@Target(ElementType.TYPE)
@Bean
@DefaultScope(Singleton.class)
@Experimental
public @interface ServerFilter {
    /**
     * Pattern used to match all requests.
     */
    String MATCH_ALL_PATTERN = Filter.MATCH_ALL_PATTERN;

    /**
     * @return The patterns this filter should match
     */
    String[] value() default {};

    /**
     * @return The style of pattern this filter uses
     */
    FilterPatternStyle patternStyle() default FilterPatternStyle.ANT;

    /**
     * Same as {@link #value()}.
     *
     * @return The patterns
     */
    @AliasFor(member = "value")
    String[] patterns() default {};

    /**
     * @return The methods to match. Defaults to all
     */
    HttpMethod[] methods() default {};
}
