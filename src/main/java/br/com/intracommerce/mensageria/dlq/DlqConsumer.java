package br.com.intracommerce.mensageria.dlq;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import br.com.intracommerce.mensageria.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Registra as mensagens que chegaram à DLQ. Usa a dlqListenerContainerFactory, que não converte
 * o corpo, para conseguir logar inclusive JSON malformado. A origem da falha vem do header x-death,
 * preenchido pelo RabbitMQ no dead-lettering.
 *
 * Com dlq.consumer.auto-startup=false as mensagens ficam acumuladas na fila para inspeção
 * pelo Management (Queues → pedido.dlq → Get messages).
 */
@Component
public class DlqConsumer {

    private static final Logger log = LoggerFactory.getLogger(DlqConsumer.class);

    @RabbitListener(queues = RabbitMQConfig.DLQ,
            containerFactory = "dlqListenerContainerFactory",
            autoStartup = "${dlq.consumer.auto-startup:true}")
    public void receber(Message mensagem) {
        String corpo = new String(mensagem.getBody(), StandardCharsets.UTF_8);
        List<Map<String, ?>> xDeath = mensagem.getMessageProperties().getXDeathHeader();

        if (xDeath == null || xDeath.isEmpty()) {
            log.error("[DLQ] Mensagem sem header x-death: {}", corpo);
            return;
        }

        Map<String, ?> morte = xDeath.get(0);
        log.error("[DLQ] Mensagem rejeitada na fila {} (motivo: {}, routing key: {}): {}",
                morte.get("queue"), morte.get("reason"), morte.get("routing-keys"), corpo);
    }
}
