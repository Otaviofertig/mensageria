package br.edu.mensageria.monitoramento;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RegistroDeAtividadesTest {

    @Test
    void listaSomenteAsAtividadesPosterioresAoIdInformado() {
        RegistroDeAtividades registro = new RegistroDeAtividades();
        registro.info("ESTOQUE", "primeira");
        registro.info("ESTOQUE", "segunda");

        assertThat(registro.listarApos(1)).extracting(Atividade::mensagem).containsExactly("segunda");
    }

    @Test
    void devolveTudoQuandoAPaginaEstaAFrenteDoServidorReiniciado() {
        RegistroDeAtividades registro = new RegistroDeAtividades();
        registro.info("ESTOQUE", "primeira");
        registro.info("ESTOQUE", "segunda");

        // A página ainda guarda o id 500 da execução anterior da aplicação
        assertThat(registro.listarApos(500)).extracting(Atividade::mensagem).containsExactly("primeira", "segunda");
    }
}
