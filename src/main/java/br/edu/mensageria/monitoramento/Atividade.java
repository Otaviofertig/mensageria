package br.edu.mensageria.monitoramento;

import java.time.Instant;

/** Algo que aconteceu no sistema (publicação, consumo, falha), exibido na linha do tempo da interface. */
public record Atividade(long id, Instant instante, String origem, Nivel nivel, String mensagem) {

    public enum Nivel {
        INFO,
        AVISO,
        ERRO
    }
}
