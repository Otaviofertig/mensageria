package br.com.intracommerce.mensageria.pedido;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String cliente;

    @Column(nullable = false)
    private String produto;

    @Column(nullable = false)
    private Integer quantidade;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPedido status;

    @Column(nullable = false)
    private Instant dataCriacao;

    protected Pedido() {
    }

    public Pedido(String cliente, String produto, Integer quantidade, BigDecimal valor) {
        this.cliente = cliente;
        this.produto = produto;
        this.quantidade = quantidade;
        this.valor = valor;
        this.status = StatusPedido.CRIADO;
        this.dataCriacao = Instant.now();
    }

    public void marcarComoPago() {
        if (status != StatusPedido.CRIADO) {
            throw new OperacaoInvalidaException("Pedido " + id + " não pode ser pago no status " + status);
        }
        status = StatusPedido.PAGO;
    }

    public void cancelar() {
        if (status == StatusPedido.CANCELADO) {
            throw new OperacaoInvalidaException("Pedido " + id + " já está cancelado");
        }
        status = StatusPedido.CANCELADO;
    }

    public Long getId() {
        return id;
    }

    public String getCliente() {
        return cliente;
    }

    public String getProduto() {
        return produto;
    }

    public Integer getQuantidade() {
        return quantidade;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public StatusPedido getStatus() {
        return status;
    }

    public Instant getDataCriacao() {
        return dataCriacao;
    }
}
