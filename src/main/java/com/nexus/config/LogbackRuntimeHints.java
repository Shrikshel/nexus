package com.nexus.config;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Registers Logback pattern converters, encoders, layouts, and appenders
 * for GraalVM Native Image reflection and dynamic class loading.
 */
public class LogbackRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        Class<?>[] logbackClasses = new Class<?>[] {
                // Pattern converters
                ch.qos.logback.classic.pattern.DateConverter.class,
                ch.qos.logback.classic.pattern.MessageConverter.class,
                ch.qos.logback.classic.pattern.LineSeparatorConverter.class,
                ch.qos.logback.classic.pattern.LevelConverter.class,
                ch.qos.logback.classic.pattern.LoggerConverter.class,
                ch.qos.logback.classic.pattern.ThreadConverter.class,
                ch.qos.logback.classic.pattern.MDCConverter.class,
                ch.qos.logback.classic.pattern.MarkerConverter.class,
                ch.qos.logback.classic.pattern.ExtendedThrowableProxyConverter.class,
                ch.qos.logback.classic.pattern.ThrowableProxyConverter.class,
                ch.qos.logback.classic.pattern.RootCauseFirstThrowableProxyConverter.class,
                ch.qos.logback.classic.pattern.FileOfCallerConverter.class,
                ch.qos.logback.classic.pattern.ClassOfCallerConverter.class,
                ch.qos.logback.classic.pattern.LineOfCallerConverter.class,
                ch.qos.logback.classic.pattern.MethodOfCallerConverter.class,
                ch.qos.logback.classic.pattern.ContextNameConverter.class,
                ch.qos.logback.classic.pattern.RelativeTimeConverter.class,
                ch.qos.logback.classic.pattern.SyslogStartConverter.class,
                ch.qos.logback.classic.pattern.color.HighlightingCompositeConverter.class,
                ch.qos.logback.classic.pattern.PrefixCompositeConverter.class,
                ch.qos.logback.classic.pattern.PropertyConverter.class,

                // Encoders, layouts, appenders, policies & filters
                ch.qos.logback.classic.encoder.PatternLayoutEncoder.class,
                ch.qos.logback.classic.PatternLayout.class,
                ch.qos.logback.core.ConsoleAppender.class,
                ch.qos.logback.core.rolling.RollingFileAppender.class,
                ch.qos.logback.core.rolling.TimeBasedRollingPolicy.class,
                ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy.class,
                ch.qos.logback.core.rolling.SizeAndTimeBasedFileNamingAndTriggeringPolicy.class,
                ch.qos.logback.core.util.FileSize.class,
                ch.qos.logback.core.rolling.DefaultTimeBasedFileNamingAndTriggeringPolicy.class,
                ch.qos.logback.core.rolling.TimeBasedFileNamingAndTriggeringPolicyBase.class,
                ch.qos.logback.core.rolling.helper.Compressor.class,
                ch.qos.logback.core.rolling.helper.ArchiveRemover.class,
                ch.qos.logback.core.rolling.helper.SizeAndTimeBasedArchiveRemover.class,
                ch.qos.logback.core.rolling.helper.TimeBasedArchiveRemover.class,
                ch.qos.logback.core.rolling.helper.FileFilterUtil.class,
                ch.qos.logback.classic.filter.ThresholdFilter.class
        };

        for (Class<?> clazz : logbackClasses) {
            hints.reflection().registerType(clazz,
                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.PUBLIC_FIELDS,
                    MemberCategory.DECLARED_FIELDS);
        }
    }
}
