package br.edu.mensageria.monitoramento;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import br.edu.mensageria.config.RabbitMQConfig;
import br.edu.mensageria.messaging.PedidoEvent;
import br.edu.mensageria.messaging.TipoEvento;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publica mensagens propositalmente inválidas direto na exchange, sem passar pela API de pedidos,
 * para demonstrar o caminho até a DLQ. Equivale ao "Publish message" do RabbitMQ Management.
 */
@RestController
@RequestMapping("/demo")
public class DemoController {

    private final RabbitTemplate rabbitTemplate;
    private final RegistroDeAtividades atividades;

    public DemoController(RabbitTemplate rabbitTemplate, RegistroDeAtividades atividades) {
        this.rabbitTemplate = rabbitTemplate;
        this.atividades = atividades;
    }

    @PostMapping("/json-malformado")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void jsonMalformado() {
        String corpo = "{ isto não é json";
        rabbitTemplate.send(RabbitMQConfig.EXCHANGE, RabbitMQConfig.RK_PEDIDO_CRIADO,
                MessageBuilder.withBody(corpo.getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .build());
        atividades.aviso("DEMO", "JSON malformado publicado em {}: {}", RabbitMQConfig.RK_PEDIDO_CRIADO, corpo);
    }

    @PostMapping("/evento-invalido")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void eventoInvalido() {
        PedidoEvent evento = new PedidoEvent(0L, TipoEvento.CRIADO,
                new PedidoEvent.DadosPedido("Maria", "Notebook", 0, new BigDecimal("3500")), Instant.now());
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.RK_PEDIDO_CRIADO, evento);
        atividades.aviso("DEMO", "Evento com quantidade 0 publicado em {}", RabbitMQConfig.RK_PEDIDO_CRIADO);
    }
}
