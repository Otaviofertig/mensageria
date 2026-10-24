# Roteiro do vídeo — Sistema de Pedidos com RabbitMQ

Duração estimada: **15 a 18 minutos**. Se houver limite de tempo, encurte a parte 4 (código) mostrando só os arquivos marcados com ★.

Cada bloco traz: **tela** (o que deixar aberto), **mostrar** (o que apontar) e **fala** (sugestão do que dizer, adapte ao seu jeito).

---

## Antes de gravar

- [ ] Recomeçar o ambiente do zero (o banco H2 é em memória e o RabbitMQ guarda mensagens antigas):
  ```bash
  cd ~/Projetos/mensageria/mensageria
  docker compose --profile app down
  APP_PORT=8081 docker compose --profile app up -d --build
  ```
- [ ] Abrir as abas, nesta ordem:
  1. VS Code / IntelliJ com o projeto
  2. Interface: http://localhost:8081
  3. RabbitMQ Management: http://localhost:15672 (já logado com `guest` / `guest`)
  4. Terminal com o log: `docker compose logs -f app`
- [ ] Aumentar a fonte do editor e do terminal (zoom de 125% a 150% no navegador).
- [ ] Desativar notificações do sistema.
- [ ] Deixar o diagrama de arquitetura do `README.md` aberto em preview.

---

## 1. Abertura e problema (0:00 – 1:00)

**Tela:** diagrama do README.

**Fala:**
> "Este é o projeto final da disciplina: um sistema de pedidos com Spring Boot, REST e RabbitMQ.
> O problema que ele resolve: se a API de pedidos fizesse estoque, pagamento e e-mail dentro da requisição HTTP, o cliente ficaria esperando tudo terminar, e uma falha no e-mail derrubaria o pedido inteiro.
> Com mensageria, a API grava o pedido, publica um evento e responde na hora. Cada serviço processa o evento no seu tempo, de forma assíncrona."

---

## 2. Arquitetura (1:00 – 3:00)

**Tela:** diagrama do README e as tabelas de topologia e de eventos logo abaixo.

**Mostrar e falar, na ordem do diagrama:**

1. **Exchange TOPIC `pedido.events`**
   > "A API não publica direto na fila. Ela publica numa exchange do tipo TOPIC, e a exchange decide para quais filas a mensagem vai, pela routing key."
2. **Três filas, dois estilos de binding**
   > "Estoque e pagamento usam a chave exata: só recebem os eventos de que precisam. A notificação usa o curinga `pedido.#` e recebe todos os eventos de pedido."
3. **Coreografia**
   > "O pagamento consome `pedido.criado`, aprova a cobrança e publica `pedido.pago`. A API não participa disso: um evento gera outro. Isso é coreografia de eventos."
4. **DLX e DLQ**
   > "Toda fila tem uma dead letter exchange configurada. Uma mensagem inválida, ou que falhou depois de três tentativas, é desviada para a `pedido.dlq` em vez de se perder ou travar a fila."

---

## 3. Decisões do projeto (3:00 – 4:30)

**Tela:** seção "Decisões" do `PLANO_DE_IMPLEMENTACAO.md`.

| Decisão | Por quê |
|---|---|
| **RabbitMQ no Docker** | Ambiente igual em qualquer máquina, sem instalar Erlang. Com `--profile app` dá para rodar até sem Java instalado. |
| **Uma aplicação, consumidores em pacotes separados** | Simples de rodar e demonstrar. Cada consumidor está isolado no seu pacote e só conversa pelo RabbitMQ, então separar em microsserviços depois é mover o pacote para outro projeto. |
| **H2 em memória** | O foco é a mensageria, não o banco. Zero configuração. |
| **Pagamento publica `pedido.pago` sozinho** | Demonstra coreografia: um evento gerando outro sem orquestrador. |
| **Mensagens em JSON** | Legível no Management e independente de linguagem. Um consumidor em Python, por exemplo, conseguiria ler. |
| **Spring Boot 3.5 e Java 17** | Versão LTS do Java e linha estável do Spring Boot com suporte nativo a AMQP (`spring-boot-starter-amqp`). |

---

## 4. Código (4:30 – 10:00)

Siga a ordem do caminho de uma mensagem: configuração → quem publica → quem consome → falhas.

### 4.1 ★ `docker-compose.yml` (30 s)
**Mostrar:** a imagem `rabbitmq:4-management`, as portas 5672 (AMQP) e 15672 (Management) e o healthcheck.
> "O healthcheck garante que a API só sobe depois que o RabbitMQ está pronto."

### 4.2 ★ `application.properties` (1 min)
**Mostrar** as configurações principais e o motivo de cada uma:
- `retry.enabled=true`, `max-attempts=3`, `initial-interval=1s`, `multiplier=2`
  > "Se o consumidor falhar, tenta de novo três vezes, esperando 1 s e depois 2 s. Isso cobre falhas temporárias, como um gateway fora do ar."
- `default-requeue-rejected=false`
  > "Sem isso, uma mensagem rejeitada voltaria para a fila e falharia de novo para sempre. Com `false`, ela vai para a DLQ."
- `prefetch=1`
  > "Cada consumidor pega uma mensagem por vez. Sem isso, o Spring puxa até 250 mensagens para a memória e a fila parece vazia no Management, escondendo o backlog."
- `accept-float-as-int=false`
  > "Sem isso, uma quantidade 1.5 virava 1 em silêncio. Agora a API recusa."

### 4.3 ★ `config/RabbitMQConfig.java` (1 min 30 s)
**Mostrar:**
- As constantes de nomes e routing keys
  > "Ficam num lugar só, nada de texto espalhado pelo código."
- `TopicExchange` e as filas criadas com `deadLetterExchange(DLX)`
- Os beans de `Binding`: `pedido.criado`, `pedido.cancelado` e `pedido.#`
- `Jackson2JsonMessageConverter`
  > "Converte o objeto Java em JSON ao publicar e de volta ao consumir, automaticamente."
- `naoRetentarMensagensInvalidas`
  > "Mensagem inválida não melhora tentando de novo, então vai direto para a DLQ. O retry fica só para falhas temporárias."
- `rejeitarParaDlq`
  > "Quando um consumidor desiste de uma mensagem, isto registra o motivo numa linha só e manda a mensagem para a DLQ. Assim o log explica o que aconteceu, em vez de mostrar um stack trace enorme."
- `dlqListenerContainerFactory`
  > "A DLQ recebe a mensagem crua, sem converter, porque a mensagem que chega lá pode ser justamente um JSON quebrado."

### 4.4 `pedido/PedidoController.java` (20 s)
**Mostrar:** os 4 endpoints e o `@Valid`. Depois, o `PedidoRequest` com as validações (`@NotBlank`, `@Positive`, `@Size`, `@Digits`) e o `ApiExceptionHandler`.
> "Todo erro da API sai no mesmo formato, o ProblemDetail, com uma mensagem legível e o campo que está errado. Nenhuma entrada do usuário gera erro 500."


### 4.5 ★ `pedido/PedidoService.java` (40 s)
**Mostrar:** `criar` faz `save` e depois `publicar`. Ler o comentário do topo da classe.
> "Primeiro o pedido é salvo, e só depois o evento é publicado. Assim um consumidor nunca recebe o evento de um pedido que ainda não existe no banco."

### 4.6 ★ `messaging/PedidoEvent.java` e `PedidoEventPublisher.java` (1 min)
**Mostrar:**
- O `record` com `pedidoId`, `tipo`, `dados` e `timestamp`
- `convertAndSend(EXCHANGE, routingKey, evento)`
  > "Publico na exchange com a routing key, e não direto na fila. Por isso a API não precisa saber quantos consumidores existem: para adicionar um consumidor novo, basta criar uma fila e um binding, sem mexer na API. Isso é baixo acoplamento."
- `validar()` lançando `AmqpRejectAndDontRequeueException`

### 4.7 ★ Consumidores (1 min)
- `EstoqueConsumer`: o `@RabbitListener` e o `switch` por tipo de evento
- `PagamentoConsumer`:
  > "Simula a cobrança. Acima de R$ 10.000 lança uma exceção para simular o gateway fora do ar, o que ativa o retry e depois a DLQ. Se aprovar, chama `registrarPagamento`, que salva o status `PAGO` e publica `pedido.pago`."
- `NotificacaoConsumer`: recebe tudo por causa do `pedido.#`
- `DlqConsumer`:
  > "Lê o header `x-death`, que o próprio RabbitMQ preenche, para saber de qual fila a mensagem veio e por quê."

### 4.8 `monitoramento/` (30 s)
**Mostrar:** `MonitoramentoController.pausar`, que usa o `RabbitListenerEndpointRegistry`.
> "Isso é apoio para a interface: permite pausar um consumidor para mostrar mensagens acumulando na fila."

---

## 5. Demonstração (10:00 – 15:30)

**Tela:** interface (8081), Management (15672) e terminal com log, alternando entre eles.

### 5.1 Topologia no Management (1 min)
- *Exchanges → pedido.events → Bindings*: as 4 ligações e as routing keys
- *Queues and Streams*: as 4 filas com a marcação **DLX**
> "Nada disso foi criado à mão. A aplicação declarou tudo ao subir."

### 5.2 Fluxo feliz (1 min)
- Interface: preencher o formulário e clicar em **Criar pedido**
- Mostrar a linha do tempo: PUBLISHER → ESTOQUE → NOTIFICACAO → PAGAMENTO → PUBLISHER (`pedido.pago`) → NOTIFICACAO
- A tabela muda de **CRIADO** para **PAGO** sozinha
- Mostrar o mesmo no terminal
> "A resposta da API saiu na hora, com status CRIADO. O pagamento aconteceu um segundo depois, de forma assíncrona."

### 5.3 Validação da API (20 s)
- Deixar o produto vazio, preencher quantidade `1,5` e valor `10,555`, e clicar em **Criar pedido**: todos os erros aparecem juntos, cada um embaixo do seu campo (400)

### 5.4 Carga e fila acumulando (1 min)
- Clicar em **Carga: 20 pedidos**
- Na interface, as três filas sobem e descem em ritmos diferentes: o estoque esvazia primeiro (0,3 s por mensagem), depois a notificação (0,5 s) e por último o pagamento (1 s)
- No Management, em *Overview*, mostrar o pico de *Publish rate* e *Deliver rate*
> "Cada serviço trabalha no seu ritmo, e a fila segura o excesso. A API respondeu os 20 pedidos na hora, nenhum pedido se perde e o serviço mais lento não atrasa os outros. A notificação recebe o dobro de mensagens porque também recebe o `pedido.pago`."

### 5.5 Consumidor fora do ar (1 min)
- Clicar em **Pausar** na `pagamento.queue`
- Criar 3 pedidos: eles ficam **CRIADO** e a fila mostra 3 mensagens
- Clicar em **Retomar**: tudo é processado e vira **PAGO**
> "É como se o serviço de pagamento tivesse caído. As mensagens esperam no broker e são processadas quando ele volta. Isso é tolerância a falhas."

### 5.6 Mensagens inválidas (1 min 30 s)
- **JSON malformado**: cada consumidor registra "Mensagem rejeitada e enviada para a DLQ: não foi possível converter o JSON", seguido de três linhas DLQ, sem nenhuma nova tentativa
- **Evento inválido** (quantidade 0): mesma coisa, agora com o motivo "Evento inválido: quantidade deve ser maior que zero"
- **Pausar a `pedido.dlq`** e enviar um JSON malformado de novo. No Management, abrir *Queues → pedido.dlq → Get messages* e mostrar o header `x-death`. Depois clicar em **Retomar**.
> "Mensagem inválida nunca vai dar certo, então não faz sentido tentar de novo: vai direto para a DLQ, onde dá para investigar."

### 5.7 Falha temporária com retry (1 min)
- Clicar em **Falha no pagamento** (R$ 15.000)
- Linha do tempo: 3 tentativas de cobrança com 1 s e 2 s de espera entre elas e, em seguida, DLQ
- O pedido continua **CRIADO**
> "Aqui é diferente: uma falha temporária pode se resolver, então o sistema tenta três vezes antes de desistir."

### 5.8 Cancelamento (30 s)
- Clicar em **Cancelar** num pedido PAGO: estoque devolve o produto e a notificação avisa o cliente
- O botão fica desabilitado para pedidos já cancelados. Para mostrar a regra, cancele de novo pelo terminal e mostre o erro 409:
  ```bash
  curl -X POST localhost:8081/pedidos/1/cancelar
  ```
> "O cancelamento publica `pedido.cancelado`. O estoque recebe esse evento pela chave exata, e a notificação pelo curinga."

---

## 6. Testes automatizados (15:30 – 16:30)

**Tela:** `src/test/.../PedidoFluxoIntegrationTest.java` e o terminal.

**Mostrar:** o `RabbitMQContainer` com `@ServiceConnection`.
> "Os testes de integração sobem um RabbitMQ de verdade em container com Testcontainers. Não é mock: testam o fluxo criado → pago, a pausa de consumidor, o retry e os três casos de DLQ. Os testes da API garantem que toda entrada inválida vira um erro 400 com mensagem clara."

**Comando** (sem Java instalado, roda dentro do Docker):
```bash
docker run --rm -v "$PWD":/app -v mensageria-m2:/root/.m2 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -w /app eclipse-temurin:17-jdk ./mvnw -B verify
```
Leva cerca de 1 minuto: grave e corte a espera, mostrando só o final `Tests run: 14, Failures: 0` e `BUILD SUCCESS`.

---

## 7. Fechamento (16:30 – 17:30)

**Tela:** a tabela de requisitos do `PLANO_DE_IMPLEMENTACAO.md` ("Mapeamento requisito → solução").

**Fala:**
> "Os dez requisitos foram atendidos: cadastro, publicação, os três consumidores, exchange TOPIC, routing keys, três filas, monitoramento pelo Management e tratamento de mensagens inválidas.
> A mensageria é real: broker RabbitMQ, roteamento, retry e DLQ. O que é simulado é só o efeito final de cada serviço: o estoque não tem tabela, o pagamento não chama um banco e o e-mail aparece no log, porque o foco é a comunicação entre os serviços.
> Como próximos passos, daria para separar cada consumidor em um microsserviço, trocar o H2 por PostgreSQL e integrar um envio de e-mail real."

---

## Perguntas que o professor pode fazer

| Pergunta | Resposta curta |
|---|---|
| Por que TOPIC e não DIRECT ou FANOUT? | DIRECT só aceita chave exata e FANOUT manda tudo para todos. TOPIC permite as duas coisas: chave exata no estoque e no pagamento, curinga na notificação. |
| E se o RabbitMQ cair? | As filas são duráveis e as mensagens persistentes, então sobrevivem ao reinício do broker. A API devolveria erro ao publicar, o que poderia ser tratado com o padrão *outbox*. |
| E se o mesmo evento chegar duas vezes? | O pagamento só aprova pedidos com status `CRIADO`. Uma segunda entrega encontra o pedido `PAGO` e é ignorada (idempotência). |
| Por que não usar `@Transactional` no service? | Para publicar só depois do commit. Com `@Transactional`, o consumidor poderia receber o evento antes de o pedido estar visível no banco. |
| Por que mensagens inválidas não têm retry? | Porque o erro é da mensagem, não do ambiente. Tentar de novo só atrasaria a fila. |
