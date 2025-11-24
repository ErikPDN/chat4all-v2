package com.chat4all.observability.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.ResourceAttributes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * OpenTelemetry configuration for distributed tracing
 * 
 * Configures:
 * - OTLP gRPC exporter to Jaeger/Tempo backend
 * - W3C Trace Context propagation (industry standard)
 * - Service resource attributes (name, version, environment)
 * - Sampling strategy (100% dev, 10% prod for cost optimization)
 * - Batch span processing for performance
 * 
 * Aligned with:
 * - Task T020
 * - Constitutional Principle VI (Full-Stack Observability)
 * - FR-038: Distributed tracing requirement
 * - Research: Observability stack (Jaeger/Tempo integration)
 * 
 * Context Propagation:
 * - Uses W3C Trace Context standard (traceparent/tracestate headers)
 * - Propagates across HTTP, Kafka, and gRPC boundaries
 * - MDC integration for trace_id and span_id in logs
 * 
 * Usage:
 * <pre>
 * @Autowired
 * private Tracer tracer;
 * 
 * Span span = tracer.spanBuilder("operation-name").startSpan();
 * try (Scope scope = span.makeCurrent()) {
 *     // Your code here
 * } finally {
 *     span.end();
 * }
 * </pre>
 */
@Slf4j
@Configuration
public class TracingConfig {

    @Value("${spring.application.name:unknown-service}")
    private String serviceName;

    @Value("${otel.exporter.otlp.endpoint:http://localhost:4317}")
    private String otlpEndpoint;

    @Value("${otel.traces.sampler.probability:1.0}")
    private double samplingProbability;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Value("${otel.service.version:1.0.0-SNAPSHOT}")
    private String serviceVersion;

    /**
     * Configure OpenTelemetry SDK with OTLP exporter and W3C propagation
     * 
     * @return Configured OpenTelemetry instance
     */
    @Bean
    public OpenTelemetry openTelemetry() {
        log.info("Initializing OpenTelemetry: service={}, endpoint={}, sampling={}", 
            serviceName, otlpEndpoint, samplingProbability);

        // Service resource attributes for identification
        @SuppressWarnings("deprecation")
        Resource resource = Resource.getDefault()
            .merge(Resource.create(Attributes.builder()
                .put(ResourceAttributes.SERVICE_NAME, serviceName)
                .put(ResourceAttributes.SERVICE_VERSION, serviceVersion)
                .put(ResourceAttributes.DEPLOYMENT_ENVIRONMENT, activeProfile)
                .put(ResourceAttributes.SERVICE_NAMESPACE, "chat4all")
                .build()));

        // OTLP gRPC span exporter
        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint(otlpEndpoint)
            .setTimeout(Duration.ofSeconds(10))
            .build();

        // Batch span processor for performance (batches spans before export)
        BatchSpanProcessor batchSpanProcessor = BatchSpanProcessor.builder(spanExporter)
            .setMaxQueueSize(2048) // Max spans in queue
            .setMaxExportBatchSize(512) // Max spans per batch
            .setScheduleDelay(Duration.ofSeconds(5)) // Export every 5s
            .setExporterTimeout(Duration.ofSeconds(30)) // Timeout for export
            .build();

        // Sampler configuration based on environment
        Sampler sampler;
        if ("prod".equalsIgnoreCase(activeProfile) || "production".equalsIgnoreCase(activeProfile)) {
            // Production: sample 10% of traces for cost optimization
            sampler = Sampler.traceIdRatioBased(0.1);
            log.info("Production mode: Using 10% trace sampling");
        } else {
            // Development/Staging: sample all traces for debugging
            sampler = Sampler.traceIdRatioBased(samplingProbability);
            log.info("Development mode: Using {}% trace sampling", samplingProbability * 100);
        }

        // Tracer provider with resource, processor, and sampler
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(batchSpanProcessor)
            .setSampler(sampler)
            .build();

        // OpenTelemetry SDK with W3C Trace Context propagation
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(
                W3CTraceContextPropagator.getInstance() // Standard traceparent/tracestate headers
            ))
            .buildAndRegisterGlobal();

        // Shutdown hook for graceful cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down OpenTelemetry SDK");
            tracerProvider.shutdown();
            spanExporter.shutdown();
            log.info("OpenTelemetry SDK shutdown complete");
        }));

        log.info("OpenTelemetry initialized successfully with W3C Trace Context propagation");
        return openTelemetry;
    }

    /**
     * Provide Tracer bean for manual instrumentation
     * 
     * @param openTelemetry OpenTelemetry instance
     * @return Tracer for creating spans
     */
    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        Tracer tracer = openTelemetry.getTracer(
            serviceName, 
            serviceVersion
        );
        log.info("Tracer created for service: {} version: {}", serviceName, serviceVersion);
        return tracer;
    }
}
