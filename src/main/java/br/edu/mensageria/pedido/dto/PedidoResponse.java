package br.edu.mensageria.pedido.dto;

import java.math.BigDecimal;
import java.time.Instant;

import br.edu.mensageria.pedido.Pedido;
import br.edu.mensageria.pedido.StatusPedido;

public record PedidoResponse(
        Long id,
        String cliente,
        String produto,
        Integer quantidade,
        BigDecimal valor,
        StatusPedido status,
        Instant dataCriacao) {

    public static PedidoResponse de(Pedido pedido) {
        return new PedidoResponse(
                pedido.getId(),
                pedido.getCliente(),
                pedido.getProduto(),
                pedido.getQuantidade(),
                pedido.getValor(),
                pedido.getStatus(),
                pedido.getDataCriacao());
    }
}
