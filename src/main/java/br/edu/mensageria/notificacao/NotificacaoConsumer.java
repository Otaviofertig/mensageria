package br.edu.mensageria.notificacao;

import br.edu.mensageria.config.RabbitMQConfig;
import br.edu.mensageria.messaging.PedidoEvent;
import br.edu.mensageria.monitoramento.RegistroDeAtividades;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Recebe todos os eventos de pedido (binding pedido.#). */
@Component
public class NotificacaoConsumer {

    public static final String ID = "notificacao";

    private final RegistroDeAtividades atividades;
    private final long tempoProcessamentoMs;

    public NotificacaoConsumer(RegistroDeAtividades atividades,
                               @Value("${notificacao.tempo-processamento-ms}") long tempoProcessamentoMs) {
        this.atividades = atividades;
        this.tempoProcessamentoMs = tempoProcessamentoMs;
    }

    @RabbitListener(id = ID, queues = RabbitMQConfig.NOTIFICACAO_QUEUE)
    public void receber(PedidoEvent evento) throws InterruptedException {
        evento.validar();
        Thread.sleep(tempoProcessamentoMs);
        String situacao = switch (evento.tipo()) {
            case CRIADO -> "foi recebido e está aguardando pagamento";
            case PAGO -> "teve o pagamento aprovado";
            case CANCELADO -> "foi cancelado";
        };
        atividades.info("NOTIFICACAO", "E-mail enviado para {}: seu pedido {} ({}) {}",
                evento.dados().cliente(), evento.pedidoId(), evento.dados().produto(), situacao);
    }
}
