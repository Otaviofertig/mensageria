package br.edu.mensageria.pedido.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PedidoRequest(
        @NotBlank(message = "é obrigatório") String cliente,
        @NotBlank(message = "é obrigatório") String produto,
        @NotNull(message = "é obrigatório") @Positive(message = "deve ser maior que zero") Integer quantidade,
        @NotNull(message = "é obrigatório") @Positive(message = "deve ser maior que zero") BigDecimal valor) {
}
