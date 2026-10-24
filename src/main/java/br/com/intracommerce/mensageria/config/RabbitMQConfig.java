package br.com.intracommerce.mensageria.config;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.amqp.RabbitRetryTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.SimpleRetryPolicy;

/**
 * Topologia do RabbitMQ:
 *
 * <pre>
 * pedido.events (TOPIC)
 *   ├─ pedido.criado, pedido.cancelado ─► estoque.queue
 *   ├─ pedido.criado                    ─► pagamento.queue
 *   └─ pedido.#                         ─► notificacao.queue
 *
 * mensagens rejeitadas ─► pedido.dlx (FANOUT) ─► pedido.dlq
 * </pre>
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "pedido.events";
    public static final String DLX = "pedido.dlx";

    public static final String ESTOQUE_QUEUE = "estoque.queue";
    public static final String PAGAMENTO_QUEUE = "pagamento.queue";
    public static final String NOTIFICACAO_QUEUE = "notificacao.queue";
    public static final String DLQ = "pedido.dlq";

    public static final String RK_PEDIDO_CRIADO = "pedido.criado";
    public static final String RK_PEDIDO_PAGO = "pedido.pago";
    public static final String RK_PEDIDO_CANCELADO = "pedido.cancelado";
    public static final String RK_PEDIDO_TODOS = "pedido.#";

    @Bean
    public TopicExchange pedidoExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public FanoutExchange deadLetterExchange() {
        return new FanoutExchange(DLX);
    }

    @Bean
    public Queue estoqueQueue() {
        return filaComDeadLetter(ESTOQUE_QUEUE);
    }

    @Bean
    public Queue pagamentoQueue() {
        return filaComDeadLetter(PAGAMENTO_QUEUE);
    }

    @Bean
    public Queue notificacaoQueue() {
        return filaComDeadLetter(NOTIFICACAO_QUEUE);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding estoqueCriadoBinding(Queue estoqueQueue, TopicExchange pedidoExchange) {
        return BindingBuilder.bind(estoqueQueue).to(pedidoExchange).with(RK_PEDIDO_CRIADO);
    }

    @Bean
    public Binding estoqueCanceladoBinding(Queue estoqueQueue, TopicExchange pedidoExchange) {
        return BindingBuilder.bind(estoqueQueue).to(pedidoExchange).with(RK_PEDIDO_CANCELADO);
    }

    @Bean
    public Binding pagamentoBinding(Queue pagamentoQueue, TopicExchange pedidoExchange) {
        return BindingBuilder.bind(pagamentoQueue).to(pedidoExchange).with(RK_PEDIDO_CRIADO);
    }

    @Bean
    public Binding notificacaoBinding(Queue notificacaoQueue, TopicExchange pedidoExchange) {
        return BindingBuilder.bind(notificacaoQueue).to(pedidoExchange).with(RK_PEDIDO_TODOS);
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, FanoutExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange);
    }

    /** Usado automaticamente pelo RabbitTemplate e pelos @RabbitListener para (de)serializar JSON. */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * Factory exclusiva da DLQ: não tenta converter o JSON (que pode estar malformado),
     * entregando a mensagem crua ao DlqConsumer.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory dlqListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(new SimpleMessageConverter());
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    /**
     * Mensagens inválidas não melhoram com novas tentativas: vão direto para a DLQ.
     * As demais falhas (transitórias) seguem o retry configurado em application.properties.
     */
    @Bean
    public RabbitRetryTemplateCustomizer naoRetentarMensagensInvalidas(RabbitProperties properties) {
        return (target, retryTemplate) -> {
            if (target == RabbitRetryTemplateCustomizer.Target.LISTENER) {
                int maxAttempts = properties.getListener().getSimple().getRetry().getMaxAttempts();
                retryTemplate.setRetryPolicy(new SimpleRetryPolicy(maxAttempts, Map.of(
                        AmqpRejectAndDontRequeueException.class, false,
                        MessageConversionException.class, false,
                        org.springframework.messaging.converter.MessageConversionException.class, false
                ), true, true));
            }
        };
    }

    private static Queue filaComDeadLetter(String nome) {
        return QueueBuilder.durable(nome).deadLetterExchange(DLX).build();
    }
}
