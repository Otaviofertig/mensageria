package br.edu.mensageria.messaging;

import br.edu.mensageria.config.RabbitMQConfig;

public enum TipoEvento {
    CRIADO(RabbitMQConfig.RK_PEDIDO_CRIADO),
    PAGO(RabbitMQConfig.RK_PEDIDO_PAGO),
    CANCELADO(RabbitMQConfig.RK_PEDIDO_CANCELADO);

    private final String routingKey;

    TipoEvento(String routingKey) {
        this.routingKey = routingKey;
    }

    public String routingKey() {
        return routingKey;
    }
}
