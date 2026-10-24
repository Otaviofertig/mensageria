package br.edu.mensageria.messaging;

import br.edu.mensageria.config.RabbitMQConfig;
import br.edu.mensageria.monitoramento.RegistroDeAtividades;
import br.edu.mensageria.pedido.Pedido;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class PedidoEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final RegistroDeAtividades atividades;

    public PedidoEventPublisher(RabbitTemplate rabbitTemplate, RegistroDeAtividades atividades) {
        this.rabbitTemplate = rabbitTemplate;
        this.atividades = atividades;
    }

    public void publicar(Pedido pedido, TipoEvento tipo) {
        PedidoEvent evento = PedidoEvent.de(pedido, tipo);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, tipo.routingKey(), evento);
        atividades.info("PUBLISHER", "{} publicado para o pedido {}", tipo.routingKey(), pedido.getId());
    }
}
