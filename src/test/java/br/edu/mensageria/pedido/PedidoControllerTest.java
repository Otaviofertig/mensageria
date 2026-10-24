package br.edu.mensageria.pedido;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Garante que toda entrada inválida vira um 400 com mensagem legível, e não um 500. */
@WebMvcTest(PedidoController.class)
class PedidoControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PedidoService pedidoService;

    @Test
    void quantidadeDecimalEhRecusada() throws Exception {
        criar("{\"cliente\":\"Maria\",\"produto\":\"Notebook\",\"quantidade\":1.5,\"valor\":10}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.quantidade").value("deve ser um número inteiro"));
    }

    @Test
    void quantidadeComTextoEhRecusada() throws Exception {
        criar("{\"cliente\":\"Maria\",\"produto\":\"Notebook\",\"quantidade\":\"abc\",\"valor\":10}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.quantidade").value("deve ser um número inteiro"));
    }

    @Test
    void valorAcimaDoLimiteDoBancoEhRecusado() throws Exception {
        criar("{\"cliente\":\"Maria\",\"produto\":\"Notebook\",\"quantidade\":1,\"valor\":1e15}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.valor").value("deve ter no máximo 10 dígitos e 2 casas decimais"));
    }

    @Test
    void valorComTresCasasDecimaisEhRecusado() throws Exception {
        criar("{\"cliente\":\"Maria\",\"produto\":\"Notebook\",\"quantidade\":1,\"valor\":10.555}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.valor").exists());
    }

    @Test
    void clienteMuitoLongoEhRecusado() throws Exception {
        criar("{\"cliente\":\"" + "x".repeat(101) + "\",\"produto\":\"Notebook\",\"quantidade\":1,\"valor\":10}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.cliente").value("deve ter no máximo 100 caracteres"));
    }

    @Test
    void jsonQuebradoTemMensagemLegivel() throws Exception {
        criar("{\"cliente\":")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Corpo da requisição inválido: envie um JSON com cliente, produto, quantidade e valor"));
    }

    @Test
    void idNaoNumericoTemMensagemLegivel() throws Exception {
        mvc.perform(get("/pedidos/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Valor inválido para 'id': abc"));
    }

    @Test
    void erroInesperadoViraProblemDetail() throws Exception {
        when(pedidoService.criar(any())).thenThrow(new IllegalStateException("falha qualquer"));

        criar("{\"cliente\":\"Maria\",\"produto\":\"Notebook\",\"quantidade\":1,\"valor\":10}")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Erro interno inesperado. Consulte o log da aplicação."));
    }

    private ResultActions criar(String json) throws Exception {
        return mvc.perform(post("/pedidos").contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
