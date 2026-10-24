package br.edu.mensageria.pagamento;

import java.math.BigDecimal;

import br.edu.mensageria.config.RabbitMQConfig;
import br.edu.mensageria.messaging.PedidoEvent;
import br.edu.mensageria.monitoramento.RegistroDeAtividades;
import br.edu.mensageria.pedido.OperacaoInvalidaException;
import br.edu.mensageria.pedido.PedidoNaoEncontradoException;
import br.edu.mensageria.pedido.PedidoService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Recebe pedido.criado, simula a cobrança e publica pedido.pago (coreografia de eventos).
 * Pedidos acima de pagamento.limite-gateway simulam o gateway fora do ar: a falha é retentada
 * com backoff e, esgotadas as tentativas, a mensagem vai para a DLQ.
 */
@Component
public class PagamentoConsumer {

    public static final String ID = "pagamento";

    private final PedidoService pedidoService;
    private final RegistroDeAtividades atividades;
    private final long tempoProcessamentoMs;
    private final BigDecimal limiteGateway;

    public PagamentoConsumer(PedidoService pedidoService,
                             RegistroDeAtividades atividades,
                             @Value("${pagamento.tempo-processamento-ms}") long tempoProcessamentoMs,
                             @Value("${pagamento.limite-gateway}") BigDecimal limiteGateway) {
        this.pedidoService = pedidoService;
        this.atividades = atividades;
        this.tempoProcessamentoMs = tempoProcessamentoMs;
        this.limiteGateway = limiteGateway;
    }

    @RabbitListener(id = ID, queues = RabbitMQConfig.PAGAMENTO_QUEUE)
    public void receber(PedidoEvent evento) throws InterruptedException {
        evento.validar();
        atividades.info("PAGAMENTO", "Processando cobrança de R$ {} do pedido {}",
                evento.dados().valor(), evento.pedidoId());
        Thread.sleep(tempoProcessamentoMs);

        if (evento.dados().valor().compareTo(limiteGateway) > 0) {
            atividades.aviso("PAGAMENTO", "Gateway indisponível para o pedido {}", evento.pedidoId());
            throw new IllegalStateException("Gateway de pagamento indisponível (simulado para valores acima de R$ "
                    + limiteGateway + ")");
        }

        try {
            pedidoService.registrarPagamento(evento.pedidoId());
            atividades.info("PAGAMENTO", "Pagamento aprovado para o pedido {}", evento.pedidoId());
        } catch (PedidoNaoEncontradoException e) {
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        } catch (OperacaoInvalidaException e) {
            // Ex.: pedido cancelado antes da cobrança ser processada. Não é erro da mensagem.
            atividades.aviso("PAGAMENTO", "Cobrança ignorada: {}", e.getMessage());
        }
    }
}
