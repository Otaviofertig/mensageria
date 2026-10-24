package br.edu.mensageria.monitoramento;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import br.edu.mensageria.monitoramento.Atividade.Nivel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.stereotype.Component;

/**
 * Escreve no log no formato "[ORIGEM] mensagem" e guarda as últimas atividades em memória
 * para a interface web acompanhar o processamento em tempo real.
 * As mensagens aceitam placeholders {} como no SLF4J.
 */
@Component
public class RegistroDeAtividades {

    private static final Logger log = LoggerFactory.getLogger(RegistroDeAtividades.class);
    private static final int LIMITE = 300;

    private final AtomicLong sequencia = new AtomicLong();
    private final Deque<Atividade> atividades = new ArrayDeque<>();

    public void info(String origem, String mensagem, Object... args) {
        registrar(Nivel.INFO, origem, mensagem, args);
    }

    public void aviso(String origem, String mensagem, Object... args) {
        registrar(Nivel.AVISO, origem, mensagem, args);
    }

    public void erro(String origem, String mensagem, Object... args) {
        registrar(Nivel.ERRO, origem, mensagem, args);
    }

    /** Atividades com id maior que o informado, da mais antiga para a mais recente. */
    public synchronized List<Atividade> listarApos(long id) {
        return atividades.stream().filter(atividade -> atividade.id() > id).toList();
    }

    private void registrar(Nivel nivel, String origem, String mensagem, Object... args) {
        String texto = MessageFormatter.arrayFormat(mensagem, args).getMessage();
        switch (nivel) {
            case INFO -> log.info("[{}] {}", origem, texto);
            case AVISO -> log.warn("[{}] {}", origem, texto);
            case ERRO -> log.error("[{}] {}", origem, texto);
        }
        synchronized (this) {
            atividades.addLast(new Atividade(sequencia.incrementAndGet(), Instant.now(), origem, nivel, texto));
            if (atividades.size() > LIMITE) {
                atividades.removeFirst();
            }
        }
    }
}
