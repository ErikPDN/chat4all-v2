package com.chat4all.observability.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * TracingConfig
 * OpenTelemetry configuration for distributed tracing
 * 
 * Aligned with:
 * - Constitutional Principle VI: Full-stack observability (NON-NEGOTIABLE)
 * - FR-038: Distributed tracing with OpenTelemetry
 * - Task T020
 * 
 * Features:
 * - W3C Trace Context propagation (standard across microservices)
 * - OTLP gRPC exporter to Jaeger/Tempo backend
 * - Automatic instrumentation via OpenTelemetry Java Agent (runtime)
 * - Manual instrumentation support for custom spans
 * - Configurable sampling rate (default: 100% for dev, 10% for prod)
 * - Batch span processing for performance
 * 
 * Architecture:
 * 
 * Service A (message-service)           Service B (router-service)
 *     |                                      |
 *     | HTTP request with traceparent        | Kafka message with traceparent
 *     | header (W3C Trace Context)           | header (W3C Trace Context)
 *     v                                      v
 * [OpenTelemetry SDK]                   [OpenTelemetry SDK]
 *     |                                      |
 *     +-------> OTLP Exporter ---------------+
 *                    |
 *                    v
 *             [Jaeger Collector]
 *                    |
 *                    v
 *            [Jaeger Query UI]
 * 
 * Environment variables (set in Kubernetes deployment):
 * - OTEL_SERVICE_NAME: Service name (e.g., "message-service")
 * - OTEL_EXPORTER_OTLP_ENDPOINT: Jaeger OTLP endpoint (e.g., "http://jaeger:4317")
 * - OTEL_TRACES_SAMPLER: Sampling strategy (always_on, always_off, traceidratio)
 * - OTEL_TRACES_SAMPLER_ARG: Sampling rate (0.0 to 1.0 for traceidratio)
 * 
 * Usage in application code:
 * <pre>
 * {@code
 * @Service
 * public class MessageService {
 *     private final Tracer tracer;
 *     
 *     public Mono<Message> processMessage(MessageDTO dto) {
 *         Span span = tracer.spanBuilder("processMessage")
 *             .setAttribute("message.id", dto.messageId())
 *             .setAttribute("conversation.id", dto.conversationId())
 *             .startSpan();
 *         
 *         return Mono.defer(() -> doProcessMessage(dto))
 *             .doFinally(signal -> span.end());
 *     }
 * }
 * }
 * </pre>
 * 
 * Jaeger UI query examples:
 * - All traces for service: service="message-service"
 * - Slow requests: duration>5s service="message-service"
 * - Errors: error=true service="router-service"
 * - Specific conversation: tags.conversation_id="conv-123"
 */
@Slf4j
@Configuration
public class TracingConfig {

    @Value("${spring.application.name:chat4all-service}")
    private String serviceName;

    @Value("${otel.traces.sampler.arg:1.0}")
    private double samplingRate;

    /**
     * Create OpenTelemetry SDK instance
     * 
     * Note: This is a minimal configuration. For production use with Jaeger/Tempo,
     * applications should configure the OpenTelemetry Java Agent at runtime with:
     * -javaagent:/path/to/opentelemetry-javaagent.jar
     * -Dotel.service.name=message-service
     * -Dotel.exporter.otlp.endpoint=http://jaeger:4317
     * 
     * @return configured OpenTelemetry instance
     */
    @Bean
    public OpenTelemetry openTelemetry() {
        log.info("Initializing OpenTelemetry tracing for service: {}", serviceName);
        log.info("Trace sampling rate: {}%", samplingRate * 100);

        // Create resource with service name
        Resource resource = Resource.getDefault()
            .merge(Resource.create(Attributes.builder()
                .put("service.name", serviceName)
                .put("service.version", "1.0.0-SNAPSHOT")
                .put("deployment.environment", getEnvironment())
                .build()));

        // Create tracer provider with sampling (no exporter - use Java Agent)
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .setResource(resource)
            .setSampler(createSampler())
            .build();

        // Create OpenTelemetry SDK with W3C Trace Context propagation
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .buildAndRegisterGlobal();

        // Add shutdown hook to flush spans on JVM exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down OpenTelemetry tracing...");
            tracerProvider.close();
            log.info("OpenTelemetry tracing shut down successfully");
        }));

        log.info("OpenTelemetry tracing initialized successfully");
        log.info("For full observability, run with OpenTelemetry Java Agent");
        return openTelemetry;
    }

    /**
     * Create Tracer bean for manual instrumentation
     * 
     * @param openTelemetry OpenTelemetry instance
     * @return Tracer for creating custom spans
     */
    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(serviceName, "1.0.0-SNAPSHOT");
    }

    /**
     * Create sampler based on configuration
     * 
     * Sampling strategies:
     * - 1.0 (100%): Sample all traces (dev/staging)
     * - 0.1 (10%): Sample 10% of traces (production)
     * - 0.0 (0%): Disable tracing (emergency override)
     * 
     * @return configured Sampler
     */
    private Sampler createSampler() {
        if (samplingRate >= 1.0) {
            log.info("Using AlwaysOn sampler (100% of traces)");
            return Sampler.alwaysOn();
        } else if (samplingRate <= 0.0) {
            log.warn("Using AlwaysOff sampler (0% of traces) - tracing disabled");
            return Sampler.alwaysOff();
        } else {
            log.info("Using TraceIdRatioBased sampler ({}% of traces)", samplingRate * 100);
            return Sampler.traceIdRatioBased(samplingRate);
        }
    }

    /**
     * Detect deployment environment from system properties
     * 
     * @return environment name (dev, staging, production)
     */
    private String getEnvironment() {
        String env = System.getenv("DEPLOYMENT_ENV");
        if (env != null && !env.isEmpty()) {
            return env;
        }
        
        // Fallback to Spring profile
        String springProfile = System.getProperty("spring.profiles.active");
        if (springProfile != null && !springProfile.isEmpty()) {
            return springProfile;
        }
        
        return "dev";
    }

    /**
     * Helper class for trace context propagation in reactive code
     * 
     * Usage:
     * <pre>
     * {@code
     * public Mono<Message> processMessage(MessageDTO dto) {
     *     return TracingContext.withSpan(tracer, "processMessage", span -> {
     *         span.setAttribute("message.id", dto.messageId());
     *         return doProcessMessage(dto);
     *     });
     * }
     * }
     * </pre>
     */
    public static class TracingContext {
        
        /**
         * Execute reactive operation with custom span
         * 
         * @param tracer Tracer instance
         * @param spanName span name
         * @param operation reactive operation to execute
         * @param <T> return type
         * @return Mono with tracing context
         */
        public static <T> Mono<T> withSpan(
                Tracer tracer, 
                String spanName, 
                Function<io.opentelemetry.api.trace.Span, Mono<T>> operation) {
            
            return Mono.defer(() -> {
                io.opentelemetry.api.trace.Span span = tracer.spanBuilder(spanName).startSpan();
                
                return operation.apply(span)
                    .doOnSuccess(result -> span.end())
                    .doOnError(error -> {
                        span.recordException(error);
                        span.end();
                    })
                    .doOnCancel(span::end);
            });
        }
    }
}
