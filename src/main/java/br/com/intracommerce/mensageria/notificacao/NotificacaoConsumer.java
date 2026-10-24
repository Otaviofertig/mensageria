package br.com.intracommerce.mensageria.notificacao;

import br.com.intracommerce.mensageria.config.RabbitMQConfig;
import br.com.intracommerce.mensageria.messaging.PedidoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Recebe todos os eventos de pedido (binding pedido.#). */
@Component
public class NotificacaoConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoConsumer.class);

    @RabbitListener(queues = RabbitMQConfig.NOTIFICACAO_QUEUE)
    public void receber(PedidoEvent evento) {
        evento.validar();
        String situacao = switch (evento.tipo()) {
            case CRIADO -> "foi recebido e está aguardando pagamento";
            case PAGO -> "teve o pagamento aprovado";
            case CANCELADO -> "foi cancelado";
        };
        log.info("[NOTIFICACAO] E-mail enviado para {}: seu pedido {} ({}) {}",
                evento.dados().cliente(), evento.pedidoId(), evento.dados().produto(), situacao);
    }
}
