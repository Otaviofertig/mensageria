package br.edu.mensageria.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import br.edu.mensageria.pedido.Pedido;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

/** Evento publicado na exchange pedido.events, serializado como JSON. */
public record PedidoEvent(Long pedidoId, TipoEvento tipo, DadosPedido dados, Instant timestamp) {

    public record DadosPedido(String cliente, String produto, Integer quantidade, BigDecimal valor) {
    }

    public static PedidoEvent de(Pedido pedido, TipoEvento tipo) {
        DadosPedido dados = new DadosPedido(
                pedido.getCliente(), pedido.getProduto(), pedido.getQuantidade(), pedido.getValor());
        return new PedidoEvent(pedido.getId(), tipo, dados, Instant.now());
    }

    /**
     * Garante que o evento recebido tem os dados mínimos para ser processado.
     * Um evento inválido é rejeitado sem requeue e segue direto para a DLQ.
     */
    public void validar() {
        List<String> erros = new ArrayList<>();
        if (pedidoId == null) {
            erros.add("pedidoId ausente");
        }
        if (tipo == null) {
            erros.add("tipo ausente");
        }
        if (dados == null) {
            erros.add("dados ausentes");
        } else {
            if (dados.cliente() == null || dados.cliente().isBlank()) {
                erros.add("cliente ausente");
            }
            if (dados.produto() == null || dados.produto().isBlank()) {
                erros.add("produto ausente");
            }
            if (dados.quantidade() == null || dados.quantidade() <= 0) {
                erros.add("quantidade deve ser maior que zero");
            }
            if (dados.valor() == null || dados.valor().signum() <= 0) {
                erros.add("valor deve ser maior que zero");
            }
        }
        if (!erros.isEmpty()) {
            throw new AmqpRejectAndDontRequeueException("Evento inválido: " + String.join(", ", erros));
        }
    }
}
