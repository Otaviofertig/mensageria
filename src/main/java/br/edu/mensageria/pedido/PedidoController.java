package br.edu.mensageria.pedido;

import java.net.URI;
import java.util.List;

import br.edu.mensageria.pedido.dto.PedidoRequest;
import br.edu.mensageria.pedido.dto.PedidoResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pedidos")
public class PedidoController {

    private final PedidoService service;

    public PedidoController(PedidoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PedidoResponse> criar(@RequestBody @Valid PedidoRequest request) {
        Pedido pedido = service.criar(request);
        return ResponseEntity
                .created(URI.create("/pedidos/" + pedido.getId()))
                .body(PedidoResponse.de(pedido));
    }

    @GetMapping
    public List<PedidoResponse> listar() {
        return service.listar().stream().map(PedidoResponse::de).toList();
    }

    @GetMapping("/{id}")
    public PedidoResponse buscar(@PathVariable Long id) {
        return PedidoResponse.de(service.buscar(id));
    }

    @PostMapping("/{id}/cancelar")
    public PedidoResponse cancelar(@PathVariable Long id) {
        return PedidoResponse.de(service.cancelar(id));
    }
}
