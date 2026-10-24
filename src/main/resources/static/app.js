"use strict";

const INTERVALO_MS = 1000;
const MAX_ATIVIDADES = 200;

const CORES_FILA = {
    "estoque.queue": "var(--ambar)",
    "pagamento.queue": "var(--verde)",
    "notificacao.queue": "var(--violeta)",
    "pedido.dlq": "var(--vermelho)",
};

const CLIENTES = ["Maria", "João", "Ana", "Pedro", "Carla", "Lucas", "Beatriz", "Rafael"];
const PRODUTOS = ["Notebook", "Monitor", "Teclado", "Mouse", "Headset", "Webcam", "Cadeira", "SSD 1TB"];

const moeda = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const hora = new Intl.DateTimeFormat("pt-BR", { hour: "2-digit", minute: "2-digit", second: "2-digit" });

const estado = {
    ultimaAtividade: 0,
    pedidosJson: "",
    filasJson: "",
    statusPorPedido: new Map(),
};

const $ = (seletor) => document.querySelector(seletor);

// ---------- Utilitários ----------

function el(tag, atributos = {}, ...filhos) {
    const elemento = document.createElement(tag);
    for (const [chave, valor] of Object.entries(atributos)) {
        if (chave === "class") {
            elemento.className = valor;
        } else if (chave === "style") {
            elemento.style.cssText = valor;
        } else if (chave.startsWith("on")) {
            elemento.addEventListener(chave.slice(2), valor);
        } else if (valor !== false && valor != null) {
            elemento.setAttribute(chave, valor);
        }
    }
    for (const filho of filhos.flat()) {
        if (filho != null) {
            elemento.append(filho instanceof Node ? filho : document.createTextNode(String(filho)));
        }
    }
    return elemento;
}

async function api(metodo, caminho, corpo) {
    let resposta;
    try {
        resposta = await fetch(caminho, {
            method: metodo,
            headers: corpo ? { "Content-Type": "application/json" } : undefined,
            body: corpo ? JSON.stringify(corpo) : undefined,
        });
    } catch {
        throw new Error("Não foi possível conectar à API. Verifique se a aplicação está no ar.");
    }

    const texto = await resposta.text();
    let dados = null;
    try {
        dados = texto ? JSON.parse(texto) : null;
    } catch {
        // Resposta que não é JSON (ex.: página de erro de um proxy): segue só com o status.
    }

    if (!resposta.ok) {
        const erro = new Error(dados?.detail || mensagemPorStatus(resposta.status));
        erro.dados = dados;
        throw erro;
    }
    return dados;
}

function mensagemPorStatus(status) {
    if (status === 404) return "Recurso não encontrado.";
    if (status === 409) return "Operação não permitida no estado atual.";
    if (status >= 500) return "Erro interno na API. Consulte o log da aplicação.";
    return `A API recusou a requisição (erro ${status}).`;
}

let temporizadorAviso;

function avisar(mensagem, tipo = "ok") {
    const aviso = $("#aviso");
    aviso.textContent = mensagem;
    aviso.className = `aviso aviso--visivel${tipo === "erro" ? " aviso--erro" : ""}`;
    clearTimeout(temporizadorAviso);
    temporizadorAviso = setTimeout(() => aviso.classList.remove("aviso--visivel"), 3000);
}

function sortear(lista) {
    return lista[Math.floor(Math.random() * lista.length)];
}

function pedidoAleatorio(valor) {
    return {
        cliente: sortear(CLIENTES),
        produto: sortear(PRODUTOS),
        quantidade: 1 + Math.floor(Math.random() * 3),
        valor: valor ?? Math.round(100 + Math.random() * 4900),
    };
}

// ---------- Filas ----------

function renderizarFilas(filas) {
    const json = JSON.stringify(filas);
    if (json === estado.filasJson) {
        return;
    }
    estado.filasJson = json;

    const escala = Math.max(20, ...filas.map((fila) => fila.mensagens));
    $("#filas").replaceChildren(...filas.map((fila) => {
        const pausado = !fila.consumidorAtivo;
        const botao = el("button", {
            class: "botao botao--fantasma botao--pequeno",
            type: "button",
            onclick: () => alternarConsumidor(fila.consumidor, pausado, botao),
        }, pausado ? "Retomar" : "Pausar");

        return el("article", {
            class: `fila${fila.nome === "pedido.dlq" ? " fila--dlq" : ""}`,
            style: `--cor: ${CORES_FILA[fila.nome] ?? "var(--cinza)"}`,
        },
            el("div", { class: "fila-nome" }, fila.nome),
            el("div", { class: "bindings" }, fila.bindings.map((binding) => el("span", { class: "binding" }, binding))),
            el("div", { class: "fila-contagem" },
                el("strong", {}, fila.mensagens),
                el("span", {}, fila.mensagens === 1 ? "mensagem na fila" : "mensagens na fila")),
            el("div", { class: "barra" },
                el("div", { style: `width: ${(fila.mensagens / escala) * 100}%` })),
            el("div", { class: "fila-rodape" },
                el("span", { class: `estado${pausado ? " estado--pausado" : ""}` },
                    pausado ? "pausado" : "consumindo"),
                botao),
        );
    }));
}

async function alternarConsumidor(id, pausado, botao) {
    botao.disabled = true;
    botao.textContent = pausado ? "Retomando…" : "Pausando…";
    try {
        await api("POST", `/monitoramento/consumidores/${id}/${pausado ? "retomar" : "pausar"}`);
        estado.filasJson = "";
        await atualizarFilas();
    } catch (erro) {
        avisar(erro.message, "erro");
        botao.disabled = false;
    }
}

// ---------- Pedidos ----------

function renderizarPedidos(pedidos) {
    const json = JSON.stringify(pedidos);
    if (json === estado.pedidosJson) {
        return;
    }
    estado.pedidosJson = json;

    const ordenados = [...pedidos].sort((a, b) => b.id - a.id);
    const contagem = { CRIADO: 0, PAGO: 0, CANCELADO: 0 };
    ordenados.forEach((pedido) => contagem[pedido.status]++);

    $("#pedidos").replaceChildren(...ordenados.map((pedido) => {
        const anterior = estado.statusPorPedido.get(pedido.id);
        const mudou = estado.statusPorPedido.size > 0 && anterior !== pedido.status;
        estado.statusPorPedido.set(pedido.id, pedido.status);

        const cancelar = el("button", {
            class: "botao botao--fantasma botao--pequeno",
            type: "button",
            disabled: pedido.status === "CANCELADO",
            onclick: () => cancelarPedido(pedido.id, cancelar),
        }, "Cancelar");

        return el("tr", { class: mudou ? "destaque" : "" },
            el("td", {}, pedido.id),
            el("td", {}, pedido.cliente),
            el("td", {}, pedido.produto),
            el("td", { class: "num" }, pedido.quantidade),
            el("td", { class: "num" }, moeda.format(pedido.valor)),
            el("td", {}, el("span", { class: `status status--${pedido.status}` }, pedido.status)),
            el("td", { class: "num" }, cancelar),
        );
    }));

    $("#pedidos-vazio").hidden = pedidos.length > 0;
    $("#resumo-pedidos").textContent = pedidos.length
        ? `${pedidos.length} no total · ${contagem.CRIADO} aguardando · ${contagem.PAGO} pagos · ${contagem.CANCELADO} cancelados`
        : "";
}

async function cancelarPedido(id, botao) {
    botao.disabled = true;
    try {
        await api("POST", `/pedidos/${id}/cancelar`);
        avisar(`Pedido #${id} cancelado: pedido.cancelado publicado`);
        await atualizarPedidos();
    } catch (erro) {
        avisar(erro.message, "erro");
        botao.disabled = false;
    }
}

async function criarPedido(pedido) {
    return api("POST", "/pedidos", pedido);
}

// ---------- Atividade ----------

function renderizarAtividades(atividades) {
    if (atividades.length === 0) {
        return;
    }
    estado.ultimaAtividade = atividades[atividades.length - 1].id;

    const lista = $("#atividades");
    for (const atividade of atividades) {
        lista.prepend(el("li", { class: `atividade atividade--${atividade.nivel}` },
            el("span", { class: `origem origem--${atividade.origem}` }, atividade.origem),
            el("time", { datetime: atividade.instante }, hora.format(new Date(atividade.instante))),
            el("p", {}, atividade.mensagem),
        ));
    }
    while (lista.children.length > MAX_ATIVIDADES) {
        lista.lastElementChild.remove();
    }
    $("#atividades-vazio").hidden = lista.children.length > 0;
}

// ---------- Atualização periódica ----------

async function atualizarFilas() {
    renderizarFilas(await api("GET", "/monitoramento/filas"));
}

async function atualizarPedidos() {
    renderizarPedidos(await api("GET", "/pedidos"));
}

async function atualizarAtividades() {
    renderizarAtividades(await api("GET", `/monitoramento/atividades?apos=${estado.ultimaAtividade}`));
}

async function atualizar() {
    const conexao = $("#conexao");
    try {
        await Promise.all([atualizarFilas(), atualizarPedidos(), atualizarAtividades()]);
        conexao.textContent = "Conectado";
        conexao.className = "conexao conexao--ok";
    } catch {
        conexao.textContent = "API indisponível";
        conexao.className = "conexao conexao--erro";
    } finally {
        setTimeout(atualizar, INTERVALO_MS);
    }
}

// ---------- Formulário e cenários ----------

function limparErros(form) {
    form.querySelectorAll(".erro-campo").forEach((erro) => (erro.textContent = ""));
    form.querySelectorAll("input").forEach((input) => input.classList.remove("invalido"));
}

$("#form-pedido").addEventListener("submit", async (evento) => {
    evento.preventDefault();
    const form = evento.currentTarget;
    const botao = form.querySelector("button");
    const dados = Object.fromEntries(new FormData(form));
    // Quantidade não inteira é barrada pela API antes das demais validações. Para mostrar todos
    // os erros de uma vez, ela é enviada vazia e o erro dela é preenchido aqui.
    const quantidadeNaoInteira = dados.quantidade !== "" && !Number.isInteger(Number(dados.quantidade));
    const pedido = {
        cliente: dados.cliente.trim(),
        produto: dados.produto.trim(),
        quantidade: dados.quantidade === "" || quantidadeNaoInteira ? null : Number(dados.quantidade),
        valor: dados.valor === "" ? null : Number(dados.valor),
    };

    limparErros(form);
    botao.disabled = true;
    try {
        const criado = await criarPedido(pedido);
        form.reset();
        avisar(`Pedido #${criado.id} criado: pedido.criado publicado`);
        await atualizarPedidos();
    } catch (erro) {
        const erros = { ...(erro.dados?.erros ?? {}) };
        if (quantidadeNaoInteira) {
            erros.quantidade = "deve ser um número inteiro";
        }
        for (const [campo, mensagem] of Object.entries(erros)) {
            form.querySelector(`[data-campo="${campo}"]`)?.replaceChildren(mensagem);
            form.querySelector(`[name="${campo}"]`)?.classList.add("invalido");
        }
        avisar(Object.keys(erros).length ? "Corrija os campos destacados." : erro.message, "erro");
    } finally {
        botao.disabled = false;
    }
});

const CENARIOS = {
    async normal() {
        const pedido = await criarPedido(pedidoAleatorio());
        return `Pedido #${pedido.id} criado: acompanhe o status mudar para PAGO`;
    },
    async falha() {
        const pedido = await criarPedido(pedidoAleatorio(15000));
        return `Pedido #${pedido.id} de R$ 15.000 criado: o pagamento vai falhar 3 vezes`;
    },
    async carga() {
        await Promise.all(Array.from({ length: 20 }, () => criarPedido(pedidoAleatorio())));
        return "20 pedidos criados: observe a pagamento.queue";
    },
    async json() {
        await api("POST", "/demo/json-malformado");
        return "JSON malformado publicado em pedido.criado";
    },
    async invalido() {
        await api("POST", "/demo/evento-invalido");
        return "Evento com quantidade 0 publicado em pedido.criado";
    },
};

document.querySelectorAll("[data-cenario]").forEach((botao) => {
    botao.addEventListener("click", async () => {
        botao.disabled = true;
        try {
            avisar(await CENARIOS[botao.dataset.cenario]());
            await atualizarPedidos();
        } catch (erro) {
            avisar(erro.message, "erro");
        } finally {
            botao.disabled = false;
        }
    });
});

$("#limpar-atividade").addEventListener("click", () => {
    $("#atividades").replaceChildren();
    $("#atividades-vazio").hidden = false;
});

$("#link-management").href = `${location.protocol}//${location.hostname}:15672`;

atualizar();
