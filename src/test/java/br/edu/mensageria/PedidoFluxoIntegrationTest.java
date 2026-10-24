package br.edu.mensageria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import br.edu.mensageria.config.RabbitMQConfig;
import br.edu.mensageria.messaging.PedidoEvent;
import br.edu.mensageria.messaging.TipoEvento;
import br.edu.mensageria.pedido.Pedido;
import br.edu.mensageria.pedido.PedidoService;
import br.edu.mensageria.pedido.StatusPedido;
import br.edu.mensageria.pedido.dto.PedidoRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
        "pagamento.tempo-processamento-ms=0",
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms",
        "dlq.consumer.auto-startup=false"
})
class PedidoFluxoIntegrationTest {

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4-management");

    @Autowired
    PedidoService pedidoService;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    AmqpAdmin amqpAdmin;

    @BeforeEach
    void limparDlq() {
        amqpAdmin.purgeQueue(RabbitMQConfig.DLQ, false);
    }

    @Test
    void pedidoCriadoEhPagoPeloConsumidorDePagamento() {
        Pedido pedido = pedidoService.criar(new PedidoRequest("Maria", "Notebook", 2, new BigDecimal("3500")));

        await().atMost(Duration.ofSeconds(10))
                .until(() -> pedidoService.buscar(pedido.getId()).getStatus() == StatusPedido.PAGO);
    }

    @Test
    void falhaTransitoriaNoPagamentoVaiParaDlqAposAsRetentativas() {
        Pedido pedido = pedidoService.criar(new PedidoRequest("João", "Servidor", 1, new BigDecimal("15000")));

        // Só a pagamento.queue falha; estoque e notificação processam normalmente.
        await().atMost(Duration.ofSeconds(10)).until(() -> mensagensNaDlq() == 1);
        await().during(Duration.ofSeconds(1)).until(() -> mensagensNaDlq() == 1);
        assertThat(pedidoService.buscar(pedido.getId()).getStatus()).isEqualTo(StatusPedido.CRIADO);
    }

    @Test
    void jsonMalformadoVaiParaDlq() {
        Message mensagem = MessageBuilder.withBody("{ isto não é json".getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .build();
        rabbitTemplate.send(RabbitMQConfig.EXCHANGE, RabbitMQConfig.RK_PEDIDO_CRIADO, mensagem);

        // pedido.criado é roteado para estoque, pagamento e notificação: 3 cópias rejeitadas.
        await().atMost(Duration.ofSeconds(10)).until(() -> mensagensNaDlq() == 3);
    }

    @Test
    void payloadInvalidoVaiParaDlq() {
        PedidoEvent evento = new PedidoEvent(999L, TipoEvento.CRIADO,
                new PedidoEvent.DadosPedido("Maria", "Notebook", 0, new BigDecimal("3500")), Instant.now());
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.RK_PEDIDO_CRIADO, evento);

        await().atMost(Duration.ofSeconds(10)).until(() -> mensagensNaDlq() == 3);
    }

    private int mensagensNaDlq() {
        return amqpAdmin.getQueueInfo(RabbitMQConfig.DLQ).getMessageCount();
    }
}
