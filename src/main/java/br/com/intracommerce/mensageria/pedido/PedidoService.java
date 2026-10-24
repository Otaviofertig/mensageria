package br.com.intracommerce.mensageria.pedido;

import java.util.List;

import br.com.intracommerce.mensageria.messaging.PedidoEventPublisher;
import br.com.intracommerce.mensageria.messaging.TipoEvento;
import br.com.intracommerce.mensageria.pedido.dto.PedidoRequest;
import org.springframework.stereotype.Service;

/**
 * Os métodos não são @Transactional de propósito: o save() já faz commit, e só então o evento
 * é publicado. Assim um consumidor nunca recebe o evento de um pedido que ainda não está no banco.
 */
@Service
public class PedidoService {

    private final PedidoRepository repository;
    private final PedidoEventPublisher publisher;

    public PedidoService(PedidoRepository repository, PedidoEventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    public Pedido criar(PedidoRequest request) {
        Pedido pedido = repository.save(
                new Pedido(request.cliente(), request.produto(), request.quantidade(), request.valor()));
        publisher.publicar(pedido, TipoEvento.CRIADO);
        return pedido;
    }

    public List<Pedido> listar() {
        return repository.findAll();
    }

    public Pedido buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> new PedidoNaoEncontradoException(id));
    }

    public Pedido cancelar(Long id) {
        Pedido pedido = buscar(id);
        pedido.cancelar();
        pedido = repository.save(pedido);
        publisher.publicar(pedido, TipoEvento.CANCELADO);
        return pedido;
    }

    public Pedido registrarPagamento(Long id) {
        Pedido pedido = buscar(id);
        pedido.marcarComoPago();
        pedido = repository.save(pedido);
        publisher.publicar(pedido, TipoEvento.PAGO);
        return pedido;
    }
}
