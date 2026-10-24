# Roteiro do vídeo — Sistema de Pedidos com RabbitMQ

**Duração:** cerca de 15 minutos. Se precisar encurtar, reduza a parte 4 (código) aos itens 4.2, 4.4 e 4.5.

## Antes de gravar

- [ ] Zerar o ambiente. O banco H2 é em memória e o RabbitMQ guarda mensagens antigas:
  ```bash
  cd ~/Projetos/mensageria/mensageria
  docker compose --profile app down
  APP_PORT=8081 docker compose --profile app up -d --build
  ```
- [ ] Deixar abertos:
  1. O editor com o projeto
  2. A interface: http://localhost:8081
  3. O RabbitMQ Management: http://localhost:15672 (já logado com `guest` / `guest`)
  4. Um terminal com o log: `docker compose logs -f app`
  5. O `README.md` em preview, no diagrama de arquitetura
- [ ] Aumentar o zoom do editor, do terminal e do navegador (125% a 150%).
- [ ] Desativar as notificações do sistema.

---

## 1. Abertura: o problema (1 min)

**Mostrar:** o diagrama de arquitetura do `README.md`.

**Falar:**
> "Num sistema de vendas, se a API gravasse o pedido, atualizasse o estoque, cobrasse e mandasse e-mail dentro da mesma requisição, o cliente ficaria esperando tudo terminar, e uma falha no e-mail derrubaria o pedido. Com mensageria, a API grava o pedido, publica um evento e responde na hora. Cada serviço processa no seu tempo."

---

## 2. Stack da aplicação (1 min)

**Mostrar:** o `pom.xml` e o `docker-compose.yml`.

| Camada | Tecnologia | Para que serve |
|---|---|---|
| Linguagem | **Java 17** | Versão LTS |
| Framework | **Spring Boot 3.5.16** | Base da aplicação |
| API | **Spring Web** | Endpoints REST |
| Mensageria | **Spring AMQP** (`spring-boot-starter-amqp`) | `RabbitTemplate` para publicar, `@RabbitListener` para consumir |
| Broker | **RabbitMQ 4** com plugin Management | Exchange, filas, roteamento e DLQ |
| Validação | **Bean Validation** | `@NotBlank`, `@Positive`, `@Digits` |
| Persistência | **Spring Data JPA** e **H2** em memória | Grava os pedidos |
| Serialização | **Jackson** | Mensagens em JSON |
| Interface | HTML, CSS e JavaScript puro | Servida pelo próprio Spring Boot |
| Ambiente | **Docker Compose** | Sobe o RabbitMQ e a aplicação |
| Build | **Maven Wrapper** | Não precisa instalar o Maven |
| Testes | **JUnit 5**, **Testcontainers**, **MockMvc**, **Awaitility** | Testes de integração e da API |

**Falar:**
> "Usei Docker para todo mundo ter o mesmo ambiente sem instalar o Erlang. Escolhi uma aplicação só, com os consumidores em pacotes separados, porque é simples de rodar e cada consumidor só conversa pelo RabbitMQ. Para virar microsserviços, bastaria mover cada pacote para outro projeto."

---

## 3. Arquitetura (1 min 30 s)

**Mostrar:** o diagrama e as tabelas de topologia e de eventos do `README.md`.

**Falar, apontando no diagrama:**
1. > "A API publica na exchange `pedido.events`, do tipo **TOPIC**, e não direto na fila. A exchange decide para onde cada mensagem vai pela routing key."
2. > "Estoque e pagamento usam **chave exata**. A notificação usa o curinga **`pedido.#`** e recebe tudo."
3. > "O pagamento consome `pedido.criado` e publica `pedido.pago`. Um evento gera outro sem a API participar. Isso é **coreografia**."
4. > "Toda fila tem uma **dead letter exchange**. Mensagem inválida ou que falhou várias vezes vai para a `pedido.dlq`."

---

## 4. Código (5 min)

Mostre os arquivos na ordem do caminho da mensagem. Para cada um: **o que apontar** e **por que foi feito assim**.

### 4.1 `src/main/resources/application.properties`

| Configuração | Por quê |
|---|---|
| `retry.max-attempts=3`, `initial-interval=1s`, `multiplier=2` | Falha temporária ganha 3 tentativas, com espera de 1 s e depois 2 s |
| `default-requeue-rejected=false` | Sem isso, a mensagem rejeitada voltaria para a fila e entraria em loop infinito |
| `prefetch=1` | O consumidor pega uma mensagem por vez, e o acúmulo fica visível no Management |
| `accept-float-as-int=false` | Quantidade `1.5` é recusada em vez de virar `1` em silêncio |
| `*.tempo-processamento-ms` | Tempo de trabalho simulado de cada consumidor |

### 4.2 `config/RabbitMQConfig.java`
- **Constantes** de nomes e routing keys: tudo num lugar só.
- **`TopicExchange`** e as filas com `.deadLetterExchange(DLX)`.
- **Bindings:** `pedido.criado` e `pedido.cancelado` para o estoque, `pedido.criado` para o pagamento, `pedido.#` para a notificação.
- **`Jackson2JsonMessageConverter`:** converte objeto em JSON e de volta automaticamente.
- **`naoRetentarMensagensInvalidas`:** mensagem inválida não ganha retry, porque nunca vai dar certo.
- **`rejeitarParaDlq`:** registra o motivo da rejeição numa linha e manda para a DLQ.
- **`dlqListenerContainerFactory`:** a DLQ lê a mensagem crua, porque ela pode ser justamente um JSON quebrado.

> "Nada disso é criado à mão no RabbitMQ. A aplicação declara tudo quando sobe."

### 4.3 `pedido/PedidoController.java`, `pedido/dto/PedidoRequest.java` e `pedido/ApiExceptionHandler.java`
- Os 4 endpoints: criar, listar, buscar e cancelar.
- O `@Valid` e as anotações de validação.
- Todo erro sai no formato padrão **ProblemDetail**, com o campo errado indicado. Nenhuma entrada do usuário gera erro 500.

### 4.4 `pedido/PedidoService.java`
- `save()` primeiro, `publicar()` depois.

> "Primeiro o pedido é gravado, e só depois o evento é publicado. Assim nenhum consumidor recebe o evento de um pedido que ainda não existe no banco."

### 4.5 `messaging/PedidoEvent.java` e `messaging/PedidoEventPublisher.java`
- O `record` com `pedidoId`, `tipo`, `dados` e `timestamp`.
- `convertAndSend(EXCHANGE, routingKey, evento)`.

> "Publico na exchange com a routing key. A API não sabe quantos consumidores existem: para criar um serviço novo, basta uma fila e um binding, sem mexer na API. Isso é baixo acoplamento."

- `validar()` lança `AmqpRejectAndDontRequeueException`, o que manda a mensagem direto para a DLQ.

### 4.6 Os consumidores
- `estoque/EstoqueConsumer.java`: `@RabbitListener` e `switch` pelo tipo do evento (reserva ou devolve).
- `pagamento/PagamentoConsumer.java`: acima de R$ 10.000 lança exceção (gateway simulado fora do ar), o que ativa o retry. Se aprovar, chama `registrarPagamento`, que grava `PAGO` e publica `pedido.pago`.
- `notificacao/NotificacaoConsumer.java`: recebe todos os eventos.
- `dlq/DlqConsumer.java`: lê o header **`x-death`**, que o próprio RabbitMQ preenche, para saber de qual fila a mensagem veio.

### 4.7 `monitoramento/MonitoramentoController.java`

> "Apoio para a interface: lê o tamanho das filas e permite pausar um consumidor pelo `RabbitListenerEndpointRegistry`."

---

## 5. Plataforma: a interface (3 min)

**Tela:** http://localhost:8081, com o terminal de log ao lado.

| Ação | O que mostrar | O que falar |
|---|---|---|
| **Criar pedido** pelo formulário | A linha do tempo: PUBLISHER, depois ESTOQUE, NOTIFICACAO e PAGAMENTO, depois `pedido.pago` e NOTIFICACAO de novo. Na tabela, CRIADO vira **PAGO** | "A API respondeu na hora. O pagamento aconteceu depois, de forma assíncrona." |
| Formulário com produto vazio, quantidade `1,5` e valor `10,555` | Todos os erros aparecem juntos, embaixo de cada campo | "A API valida tudo e explica o erro." |
| **Carga: 20 pedidos** | As 3 filas enchem e esvaziam: estoque primeiro, depois notificação e pagamento juntos | "Cada serviço no seu ritmo, a fila segura o excesso. A notificação espera o pagamento porque recebe os `pedido.pago`." |
| **Pausar** o pagamento e criar 3 pedidos | A fila acumula 3 mensagens e os pedidos ficam CRIADO | "É como se o serviço de pagamento tivesse caído." |
| **Retomar** | Tudo é processado e vira PAGO | "Nenhuma mensagem se perdeu: o broker guardou tudo. Isso é tolerância a falhas." |
| **JSON malformado** | Cada consumidor registra "não foi possível converter o JSON" e aparecem 3 linhas DLQ | "Mensagem inválida vai direto para a DLQ, sem tentar de novo." |
| **Evento inválido** | Motivo: "quantidade deve ser maior que zero" e as linhas DLQ | Mesma ideia, mas o JSON é válido e a regra é que está errada |
| **Falha no pagamento** (R$ 15.000) | 3 tentativas espaçadas (1 s e 2 s) e depois a DLQ. O pedido fica CRIADO | "Falha temporária pode se resolver, então ganha 3 tentativas antes de desistir." |
| **Cancelar** um pedido pago | ESTOQUE "Devolvendo..." e NOTIFICACAO "foi cancelado" | "O cancelamento é outro evento, roteado pela routing key." |

O botão de cancelar fica desabilitado para pedidos já cancelados. Para mostrar o erro 409, cancele de novo pelo terminal:
```bash
curl -X POST localhost:8081/pedidos/1/cancelar
```

---

## 6. RabbitMQ Management (2 min)

**Tela:** http://localhost:15672

| Aba | O que mostrar |
|---|---|
| **Overview** | Gráficos de *Publish rate* e *Deliver rate* subindo durante a carga de 20 pedidos |
| **Exchanges → `pedido.events`** | Tipo **topic** e, em **Bindings**, as 4 ligações com as routing keys |
| **Exchanges → `pedido.dlx`** | Tipo **fanout**, ligada à `pedido.dlq` |
| **Queues and Streams** | As 4 filas, a marcação **DLX** nas 3 de consumo e a coluna *Ready* subindo durante a carga |
| **`pedido.dlq` → Get messages** | Pause o consumidor da DLQ na interface, clique em *JSON malformado* e mostre a mensagem parada com o header **`x-death`** (fila de origem e motivo). Depois clique em *Retomar* |
| **Connections / Channels** | A aplicação conectada, com um canal por consumidor |

> "O Management mostra que tudo acontece de verdade no broker: as filas, os bindings e as mensagens."

---

## 7. O que é real e o que é simulado (1 min)

**Real:** o RabbitMQ (broker, exchange, filas, roteamento, retry, DLQ), a API, as validações, o banco H2 e a mudança de status.

**Simulado** (o consumidor existe e roda; só a ação final é simplificada):

| Serviço | No projeto | Num sistema real |
|---|---|---|
| Estoque | Escreve no log, espera 0,3 s | Baixaria a quantidade numa tabela |
| Pagamento | Espera 1 s e aprova; acima de R$ 10.000 simula queda do gateway | Chamaria um gateway de pagamento |
| Notificação | Escreve "e-mail enviado" no log, espera 0,5 s | Enviaria um e-mail de verdade |

**O único mock** está no `PedidoControllerTest`: o `PedidoService` é substituído para testar só como a API responde a erros, sem precisar do RabbitMQ.

> "O foco do trabalho é a comunicação entre os serviços, não a regra de negócio de cada um."

---

## 8. O que comprova que funciona (1 min 30 s)

**Testes automatizados:** mostre o `PedidoFluxoIntegrationTest.java`, com o `RabbitMQContainer`, e rode (sem Java instalado, roda dentro do Docker):
```bash
docker run --rm -v "$PWD":/app -v mensageria-m2:/root/.m2 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -w /app eclipse-temurin:17-jdk ./mvnw -B verify
```
Leva cerca de 1 minuto. Corte a espera no vídeo e mostre o final: **`Tests run: 14, Failures: 0`** e **`BUILD SUCCESS`**.

| Teste | O que prova |
|---|---|
| Pedido criado vira pago | Fluxo completo: API → RabbitMQ → pagamento → `pedido.pago` |
| Consumidor pausado acumula mensagens | As mensagens esperam na fila e são processadas ao retomar |
| Falha transitória vai para a DLQ | Retry com 3 tentativas e depois a DLQ |
| JSON malformado vai para a DLQ | Rejeição sem retry |
| Payload inválido vai para a DLQ | Validação do evento |
| DLQ registra a fila de origem | Leitura do `x-death` |
| 8 testes da API | Toda entrada inválida vira 400 com mensagem clara |

> "Os testes de integração sobem um RabbitMQ de verdade com Testcontainers. Não é mock."

**Os 10 requisitos:** mostre a tabela "Mapeamento requisito → solução" do `PLANO_DE_IMPLEMENTACAO.md` e passe por ela rapidamente.

---

## 9. Fechamento (30 s)

> "O sistema cumpre os dez requisitos e demonstra os conceitos principais de mensageria: processamento assíncrono, baixo acoplamento, roteamento por eventos, tolerância a falhas e tratamento de mensagens inválidas. Como próximos passos, daria para separar cada consumidor em um microsserviço, trocar o H2 por PostgreSQL e integrar um envio de e-mail real."

---

## Perguntas que o professor pode fazer

| Pergunta | Resposta curta |
|---|---|
| Por que TOPIC e não DIRECT ou FANOUT? | DIRECT só aceita chave exata e FANOUT manda tudo para todos. TOPIC permite as duas coisas: chave exata no estoque e no pagamento, curinga na notificação. |
| E se o RabbitMQ cair? | As filas são duráveis e as mensagens persistentes, então sobrevivem ao reinício do broker. A API devolveria erro ao publicar, o que poderia ser tratado com o padrão *outbox*. |
| E se o mesmo evento chegar duas vezes? | O pagamento só aprova pedidos com status `CRIADO`. Uma segunda entrega encontra o pedido `PAGO` e é ignorada (idempotência). |
| Por que não usar `@Transactional` no service? | Para publicar só depois do commit. Com `@Transactional`, o consumidor poderia receber o evento antes de o pedido estar visível no banco. |
| Por que mensagens inválidas não têm retry? | Porque o erro é da mensagem, não do ambiente. Tentar de novo só atrasaria a fila. |
| Por que a notificação esvazia junto com o pagamento? | Porque ela também recebe cada `pedido.pago`, que o pagamento publica um por segundo. |
