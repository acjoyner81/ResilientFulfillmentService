package com.fulfillment.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ObservabilityTraceTest {

    @Autowired
    private MockMvc mockMvc;

    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @Test
    @DisplayName("Should populate MDC traceId and spanId during HTTP execution")
    void shouldPopulateMdcTraceAndSpanId() throws Exception {
        // Execute request against an exposed endpoint (e.g., Actuator health)
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        // Verify logged events captured trace metadata in MDC
        boolean traceIdFound = listAppender.list.stream()
                .anyMatch(event -> {
                    String traceId = event.getMDCPropertyMap().get("traceId");
                    String spanId = event.getMDCPropertyMap().get("spanId");
                    return traceId != null && !traceId.isBlank() 
                        && spanId != null && !spanId.isBlank();
                });

        assertThat(traceIdFound)
                .as("Expected at least one log event with populated traceId and spanId MDC attributes")
                .isTrue();
    }
}