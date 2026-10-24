package br.edu.mensageria.notificacao;

import br.edu.mensageria.config.RabbitMQConfig;
import br.edu.mensageria.messaging.PedidoEvent;
import br.edu.mensageria.monitoramento.RegistroDeAtividades;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Recebe todos os eventos de pedido (binding pedido.#). */
@Component
public class NotificacaoConsumer {

    public static final String ID = "notificacao";

    private final RegistroDeAtividades atividades;

    public NotificacaoConsumer(RegistroDeAtividades atividades) {
        this.atividades = atividades;
    }

    @RabbitListener(id = ID, queues = RabbitMQConfig.NOTIFICACAO_QUEUE)
    public void receber(PedidoEvent evento) {
        evento.validar();
        String situacao = switch (evento.tipo()) {
            case CRIADO -> "foi recebido e está aguardando pagamento";
            case PAGO -> "teve o pagamento aprovado";
            case CANCELADO -> "foi cancelado";
        };
        atividades.info("NOTIFICACAO", "E-mail enviado para {}: seu pedido {} ({}) {}",
                evento.dados().cliente(), evento.pedidoId(), evento.dados().produto(), situacao);
    }
}
