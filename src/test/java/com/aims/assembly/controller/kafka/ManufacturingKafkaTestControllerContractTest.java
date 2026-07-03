package com.aims.assembly.controller.kafka;

import com.aims.assembly.kafka.ManufacturingKafkaProducer;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ManufacturingKafkaTestControllerContractTest {
    @Test
    void keepsExistingSendUrlsAndHasNoRepositoryOrProducerDependency() throws Exception {
        assertThat(ManufacturingKafkaTestController.class.getDeclaredMethod("send", long.class)
                .getAnnotation(PostMapping.class).value()).containsExactly("/send/{id}");
        assertThat(ManufacturingKafkaTestController.class.getDeclaredMethod("sendSample")
                .getAnnotation(PostMapping.class).value()).containsExactly("/send-sample");

        assertThat(Arrays.stream(ManufacturingKafkaTestController.class.getDeclaredFields())
                .map(field -> field.getType()))
                .noneMatch(type -> type.getPackageName().contains("repository"))
                .noneMatch(ManufacturingKafkaProducer.class::equals);
    }
}
