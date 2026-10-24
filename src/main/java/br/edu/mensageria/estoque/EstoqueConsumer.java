package br.edu.mensageria.estoque;

import br.edu.mensageria.config.RabbitMQConfig;
import br.edu.mensageria.messaging.PedidoEvent;
import br.edu.mensageria.monitoramento.RegistroDeAtividades;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Recebe pedido.criado e pedido.cancelado. */
@Component
public class EstoqueConsumer {

    public static final String ID = "estoque";

    private final RegistroDeAtividades atividades;
    private final long tempoProcessamentoMs;

    public EstoqueConsumer(RegistroDeAtividades atividades,
                           @Value("${estoque.tempo-processamento-ms}") long tempoProcessamentoMs) {
        this.atividades = atividades;
        this.tempoProcessamentoMs = tempoProcessamentoMs;
    }

    @RabbitListener(id = ID, queues = RabbitMQConfig.ESTOQUE_QUEUE)
    public void receber(PedidoEvent evento) throws InterruptedException {
        evento.validar();
        Thread.sleep(tempoProcessamentoMs);
        PedidoEvent.DadosPedido dados = evento.dados();

        switch (evento.tipo()) {
            case CRIADO -> atividades.info("ESTOQUE", "Reservando {}x {} do pedido {}",
                    dados.quantidade(), dados.produto(), evento.pedidoId());
            case CANCELADO -> atividades.info("ESTOQUE", "Devolvendo {}x {} ao estoque (pedido {} cancelado)",
                    dados.quantidade(), dados.produto(), evento.pedidoId());
            default -> atividades.info("ESTOQUE", "Evento {} ignorado para o pedido {}",
                    evento.tipo(), evento.pedidoId());
        }
    }
}
