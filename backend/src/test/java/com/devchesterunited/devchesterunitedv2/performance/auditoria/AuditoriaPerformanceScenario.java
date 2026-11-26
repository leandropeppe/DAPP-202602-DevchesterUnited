package com.devchesterunited.devchesterunitedv2.performance.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaAspect;
import com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaEventWriter;
import com.devchesterunited.devchesterunitedv2.configuration.auditoria.CorrelacionContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Reproducible paired micro-scenario for SC-008; measurements are not simulated. */
class AuditoriaPerformanceScenario {
    private static final int WARMUP = 20;
    private static final int PAIRS = 30;
    private static final int DATASET_SIZE = 20_000_000;
    private static volatile long BLACK_HOLE;

    @AfterEach
    void cleanup() { CorrelacionContext.clear(); }

    @Test
    void recordsThirtyAlternatedPairsAndWriterDiagnostic() throws Exception {
        List<Long> baseline = new ArrayList<>();
        List<Long> treatment = new ArrayList<>();
        List<Long> writerControlled = new ArrayList<>();
        AuditoriaAspect aspect = new AuditoriaAspect(new AuditoriaEventWriter());
        for (int i = 0; i < WARMUP; i++) {
            operation(DATASET_SIZE);
            invoke(aspect, false, DATASET_SIZE);
        }
        for (int pair = 0; pair < PAIRS; pair++) {
            baseline.add(measure(() -> operation(DATASET_SIZE)));
            treatment.add(measure(() -> invoke(aspect, false, DATASET_SIZE)));
            writerControlled.add(measure(() -> invoke(new AuditoriaAspect(new AuditoriaEventWriter() {
                @Override public void write(com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaEvento event) { }
            }), true, DATASET_SIZE)));
        }
        Report report = new Report(baseline, treatment, writerControlled);
        Path directory = Path.of("build", "diagnostics", "auditoria-performance");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("sc-008-" + System.currentTimeMillis() + ".txt"), report.render());
        System.out.println(report.render());
        assertThat(baseline).hasSize(PAIRS);
        assertThat(treatment).hasSize(PAIRS);
        assertThat(writerControlled).hasSize(PAIRS);
    }

    private static void invoke(AuditoriaAspect aspect, boolean controlledWriter, int size) {
        try {
            ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
            when(point.proceed()).thenAnswer(invocation -> operation(size));
            CorrelacionContext.installBackground();
            aspect.marketInitialize(point);
        } catch (Throwable error) {
            throw new AssertionError(error);
        } finally {
            CorrelacionContext.clear();
        }
    }

    private static long measure(Runnable action) {
        long start = System.nanoTime();
        action.run();
        return System.nanoTime() - start;
    }

    private static long operation(int size) {
        long checksum = 0;
        for (int i = 0; i < size; i++) checksum = (checksum * 31) ^ (i * 17L + 3L);
        BLACK_HOLE = checksum;
        return checksum;
    }

    private record Report(List<Long> baseline, List<Long> treatment, List<Long> writerControlled) {
        String render() {
            double baselineMedian = percentile(baseline, .50);
            double treatmentMedian = percentile(treatment, .50);
            double baselineP95 = percentile(baseline, .95);
            double treatmentP95 = percentile(treatment, .95);
            return String.format(Locale.ROOT,
                    "SC-008\nmethod=30 alternating pairs; warmup=%d; dataset=%d; units=nanoseconds\n"
                            + "timestamp=%s\njava=%s\nos=%s %s; processors=%d\n"
                            + "sameCompilationConfigurationDatasetEnvironment=true\n"
                            + "externalLogDestinationTimeIncluded=false\n"
                            + "baseline.median=%.2f\ntreatment.median=%.2f\n"
                            + "baseline.p95=%.2f\ntreatment.p95=%.2f\nmedian.delta.percent=%.2f\n"
                            + "p95.delta.percent=%.2f\nwriter_controlled.median=%.2f\n"
                            + "writer_controlled.p95=%.2f\nbaseline.samples=%s\ntreatment.samples=%s\n"
                            + "writer_controlled.samples=%s\n",
                    WARMUP, DATASET_SIZE, Instant.now(), System.getProperty("java.version"),
                    System.getProperty("os.name"), System.getProperty("os.arch"),
                    Runtime.getRuntime().availableProcessors(), baselineMedian, treatmentMedian,
                    baselineP95, treatmentP95, percent(treatmentMedian, baselineMedian),
                    percent(treatmentP95, baselineP95), percentile(writerControlled, .50),
                    percentile(writerControlled, .95), baseline, treatment, writerControlled);
        }
        private static double percentile(List<Long> values, double percentile) {
            List<Long> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
            int index = Math.min(sorted.size() - 1, (int) Math.ceil(percentile * sorted.size()) - 1);
            return sorted.get(index);
        }
        private static double percent(double value, double base) { return ((value - base) / base) * 100.0; }
    }
}
