package br.edu.mensageria.pedido.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PedidoRequest(
        @NotBlank(message = "é obrigatório")
        @Size(max = 100, message = "deve ter no máximo 100 caracteres")
        String cliente,

        @NotBlank(message = "é obrigatório")
        @Size(max = 100, message = "deve ter no máximo 100 caracteres")
        String produto,

        @NotNull(message = "é obrigatório")
        @Positive(message = "deve ser maior que zero")
        Integer quantidade,

        @NotNull(message = "é obrigatório")
        @Positive(message = "deve ser maior que zero")
        @Digits(integer = 10, fraction = 2, message = "deve ter no máximo 10 dígitos e 2 casas decimais")
        BigDecimal valor) {
}
