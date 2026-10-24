package br.com.intracommerce.mensageria.pedido;

public class PedidoNaoEncontradoException extends RuntimeException {

    public PedidoNaoEncontradoException(Long id) {
        super("Pedido " + id + " não encontrado");
    }
}
