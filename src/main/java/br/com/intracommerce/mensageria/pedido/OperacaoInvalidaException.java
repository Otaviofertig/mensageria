package br.com.intracommerce.mensageria.pedido;

public class OperacaoInvalidaException extends RuntimeException {

    public OperacaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
