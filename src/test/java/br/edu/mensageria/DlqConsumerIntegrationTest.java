package br.edu.mensageria;

import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import br.edu.mensageria.config.RabbitMQConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(properties = {
        "pagamento.tempo-processamento-ms=0",
        "estoque.tempo-processamento-ms=0",
        "notificacao.tempo-processamento-ms=0"
})
class DlqConsumerIntegrationTest {

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4-management");

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Test
    void dlqConsumerRegistraJsonMalformadoComAFilaDeOrigem(CapturedOutput output) {
        rabbitTemplate.send(RabbitMQConfig.EXCHANGE, RabbitMQConfig.RK_PEDIDO_CRIADO,
                MessageBuilder.withBody("{ isto não é json".getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .build());

        await().atMost(Duration.ofSeconds(10)).until(() ->
                output.getOut().contains("[DLQ] Mensagem rejeitada na fila estoque.queue")
                        && output.getOut().contains("[DLQ] Mensagem rejeitada na fila pagamento.queue")
                        && output.getOut().contains("[DLQ] Mensagem rejeitada na fila notificacao.queue"));
    }
}
