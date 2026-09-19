package dz.tabibi.backend;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.fail;

/** Diagnostic temporaire : pourquoi /actuator/prometheus ne s'enregistre pas. */
@SpringBootTest
class DiagPrometheusTest {

    @Autowired ApplicationContext ctx;
    @MockBean JwtDecoder jwtDecoder;

    @Test
    void diag() {
        StringBuilder sb = new StringBuilder("PROMDIAG\n");
        sb.append("prometheusBeans=[")
          .append(Arrays.stream(ctx.getBeanDefinitionNames())
                  .filter(n -> n.toLowerCase().contains("prometheus"))
                  .collect(Collectors.joining(", ")))
          .append("]\n");
        sb.append("meterRegistries=").append(ctx.getBeansOfType(MeterRegistry.class).keySet()).append("\n");
        try {
            Class.forName("io.micrometer.prometheusmetrics.PrometheusMeterRegistry");
            sb.append("classPresent(io.micrometer.prometheusmetrics.PrometheusMeterRegistry)=true\n");
        } catch (ClassNotFoundException e) {
            sb.append("classPresent(io.micrometer.prometheusmetrics.PrometheusMeterRegistry)=FALSE\n");
        }
        ConditionEvaluationReport report =
                ConditionEvaluationReport.get((ConfigurableListableBeanFactory) ctx.getAutowireCapableBeanFactory());
        report.getConditionAndOutcomesBySource().forEach((source, outcomes) -> {
            if (source.toLowerCase().contains("prometheus")) {
                outcomes.forEach(o -> sb.append("COND ").append(source).append(" -> ").append(o.getOutcome()).append('\n'));
            }
        });
        fail(sb.toString());
    }
}
