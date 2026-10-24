package br.com.intracommerce.mensageria.estoque;

import br.com.intracommerce.mensageria.config.RabbitMQConfig;
import br.com.intracommerce.mensageria.messaging.PedidoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Recebe pedido.criado e pedido.cancelado. */
@Component
public class EstoqueConsumer {

    private static final Logger log = LoggerFactory.getLogger(EstoqueConsumer.class);

    @RabbitListener(queues = RabbitMQConfig.ESTOQUE_QUEUE)
    public void receber(PedidoEvent evento) {
        evento.validar();
        PedidoEvent.DadosPedido dados = evento.dados();

        switch (evento.tipo()) {
            case CRIADO -> log.info("[ESTOQUE] Reservando {}x {} do pedido {}",
                    dados.quantidade(), dados.produto(), evento.pedidoId());
            case CANCELADO -> log.info("[ESTOQUE] Devolvendo {}x {} ao estoque (pedido {} cancelado)",
                    dados.quantidade(), dados.produto(), evento.pedidoId());
            default -> log.info("[ESTOQUE] Evento {} ignorado para o pedido {}", evento.tipo(), evento.pedidoId());
        }
    }
}
