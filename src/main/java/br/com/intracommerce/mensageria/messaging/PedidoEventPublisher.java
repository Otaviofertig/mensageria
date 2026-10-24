package br.com.intracommerce.mensageria.messaging;

import br.com.intracommerce.mensageria.config.RabbitMQConfig;
import br.com.intracommerce.mensageria.pedido.Pedido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class PedidoEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PedidoEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public PedidoEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publicar(Pedido pedido, TipoEvento tipo) {
        PedidoEvent evento = PedidoEvent.de(pedido, tipo);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, tipo.routingKey(), evento);
        log.info("[PUBLISHER] {} publicado para o pedido {}", tipo.routingKey(), pedido.getId());
    }
}
