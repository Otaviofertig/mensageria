package br.edu.mensageria.monitoramento;

import java.util.List;

import br.edu.mensageria.config.RabbitMQConfig;
import br.edu.mensageria.dlq.DlqConsumer;
import br.edu.mensageria.estoque.EstoqueConsumer;
import br.edu.mensageria.notificacao.NotificacaoConsumer;
import br.edu.mensageria.pagamento.PagamentoConsumer;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Endpoints de apoio à interface web: estado das filas, linha do tempo e pausa de consumidores. */
@RestController
@RequestMapping("/monitoramento")
public class MonitoramentoController {

    private record FilaMonitorada(String nome, String consumidor, List<String> bindings) {
    }

    public record FilaStatus(String nome, String consumidor, List<String> bindings,
                             int mensagens, int consumidores, boolean consumidorAtivo) {
    }

    private static final List<FilaMonitorada> FILAS = List.of(
            new FilaMonitorada(RabbitMQConfig.ESTOQUE_QUEUE, EstoqueConsumer.ID,
                    List.of(RabbitMQConfig.RK_PEDIDO_CRIADO, RabbitMQConfig.RK_PEDIDO_CANCELADO)),
            new FilaMonitorada(RabbitMQConfig.PAGAMENTO_QUEUE, PagamentoConsumer.ID,
                    List.of(RabbitMQConfig.RK_PEDIDO_CRIADO)),
            new FilaMonitorada(RabbitMQConfig.NOTIFICACAO_QUEUE, NotificacaoConsumer.ID,
                    List.of(RabbitMQConfig.RK_PEDIDO_TODOS)),
            new FilaMonitorada(RabbitMQConfig.DLQ, DlqConsumer.ID,
                    List.of(RabbitMQConfig.DLX + " (fanout)")));

    private final AmqpAdmin amqpAdmin;
    private final RabbitListenerEndpointRegistry listenerRegistry;
    private final RegistroDeAtividades atividades;

    public MonitoramentoController(AmqpAdmin amqpAdmin,
                                   RabbitListenerEndpointRegistry listenerRegistry,
                                   RegistroDeAtividades atividades) {
        this.amqpAdmin = amqpAdmin;
        this.listenerRegistry = listenerRegistry;
        this.atividades = atividades;
    }

    @GetMapping("/filas")
    public List<FilaStatus> filas() {
        return FILAS.stream().map(fila -> {
            QueueInformation info = amqpAdmin.getQueueInfo(fila.nome());
            return new FilaStatus(fila.nome(), fila.consumidor(), fila.bindings(),
                    info == null ? 0 : info.getMessageCount(),
                    info == null ? 0 : info.getConsumerCount(),
                    container(fila.consumidor()).isRunning());
        }).toList();
    }

    @GetMapping("/atividades")
    public List<Atividade> atividades(@RequestParam(defaultValue = "0") long apos) {
        return atividades.listarApos(apos);
    }

    @PostMapping("/consumidores/{id}/pausar")
    public void pausar(@PathVariable String id) {
        container(id).stop();
        atividades.aviso("MONITOR", "Consumidor {} pausado: as mensagens vão acumular na fila", id);
    }

    @PostMapping("/consumidores/{id}/retomar")
    public void retomar(@PathVariable String id) {
        container(id).start();
        atividades.info("MONITOR", "Consumidor {} retomado", id);
    }

    private MessageListenerContainer container(String id) {
        MessageListenerContainer container = listenerRegistry.getListenerContainer(id);
        if (container == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Consumidor " + id + " não encontrado");
        }
        return container;
    }
}
