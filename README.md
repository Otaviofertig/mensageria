# Sistema de Pedidos com RabbitMQ

Projeto final da disciplina: sistema de pedidos utilizando **Spring Boot**, **REST**, **RabbitMQ** e **processamento assíncrono**.

A API recebe pedidos via HTTP, grava no banco e publica eventos em uma exchange `TOPIC`. Três consumidores independentes (estoque, pagamento e notificação) reagem a esses eventos. Mensagens inválidas ou que falham repetidamente vão para uma *dead letter queue*.

O planejamento completo está em [PLANO_DE_IMPLEMENTACAO.md](PLANO_DE_IMPLEMENTACAO.md).

## Arquitetura

```
Cliente ──POST /pedidos──► Pedido API ──publica──► [pedido.events] TOPIC
                                                        │
               ┌────────────────────┬───────────────────┼────────────────────┐
               │ pedido.criado      │ pedido.criado     │ pedido.#           │
               │ pedido.cancelado   │                   │                    │
               ▼                    ▼                   ▼                    │
         estoque.queue       pagamento.queue     notificacao.queue           │
               │                    │                   │                    │
        EstoqueConsumer     PagamentoConsumer   NotificacaoConsumer          │
                                    │                                        │
                                    └── publica pedido.pago ─────────────────┘

   Mensagem inválida / falha após 3 tentativas ──► [pedido.dlx] ──► pedido.dlq ──► DlqConsumer (log)
```

- **Binding por chave exata**: `estoque.queue` e `pagamento.queue` recebem só os eventos de que precisam.
- **Binding com curinga**: `notificacao.queue` usa `pedido.#` e recebe todos os eventos de pedido.
- **Coreografia**: o `PagamentoConsumer` consome `pedido.criado`, aprova a cobrança e publica `pedido.pago`, que chega à notificação sem a API participar.

| Recurso | Tipo | Detalhe |
|---|---|---|
| `pedido.events` | Exchange TOPIC | Durável |
| `estoque.queue` | Queue | Bindings `pedido.criado` e `pedido.cancelado` |
| `pagamento.queue` | Queue | Binding `pedido.criado` |
| `notificacao.queue` | Queue | Binding `pedido.#` |
| `pedido.dlx` | Exchange FANOUT | Dead letter exchange de todas as filas acima |
| `pedido.dlq` | Queue | Mensagens inválidas ou com falha |

| Routing key | Publicado por | Quando |
|---|---|---|
| `pedido.criado` | Pedido API | Ao cadastrar um pedido |
| `pedido.pago` | PagamentoConsumer | Após aprovar a cobrança |
| `pedido.cancelado` | Pedido API | Ao cancelar um pedido |

Toda a topologia é declarada em [RabbitMQConfig.java](src/main/java/br/edu/mensageria/config/RabbitMQConfig.java) e criada automaticamente no RabbitMQ quando a aplicação sobe.

## Como executar

### Opção A — Java 17 instalado

```bash
docker compose up -d        # sobe só o RabbitMQ
./mvnw spring-boot:run      # sobe a API em http://localhost:8080
```

No Windows, use `mvnw.cmd spring-boot:run`. Não é preciso instalar o Maven: o wrapper baixa sozinho.

### Opção B — só Docker

```bash
docker compose --profile app up --build
```

Sobe o RabbitMQ e a API em containers. Se a porta 8080 estiver ocupada: `APP_PORT=8081 docker compose --profile app up --build`.

### Endereços

| O quê | Endereço |
|---|---|
| **Interface web** | http://localhost:8080 |
| API | http://localhost:8080/pedidos |
| RabbitMQ Management | http://localhost:15672 (usuário `guest`, senha `guest`) |
| Console H2 (só na opção A) | http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:pedidos`, usuário `sa`, sem senha) |

> O banco H2 fica em memória: ao reiniciar só a aplicação, os pedidos somem, mas as mensagens pendentes no RabbitMQ continuam lá e passam a apontar para pedidos que não existem mais. Para recomeçar do zero, reinicie os dois com `docker compose --profile app down` e suba de novo.

## Interface web

A página em http://localhost:8080 é servida pelo próprio Spring Boot (arquivos em `src/main/resources/static`) e se atualiza sozinha a cada segundo:

- **Novo pedido**: formulário que chama `POST /pedidos` e mostra os erros de validação em cada campo.
- **Cenários de demonstração**: pedido normal, falha no pagamento, carga de 20 pedidos, JSON malformado e evento inválido.
- **Filas**: mensagens em cada fila e botão para **pausar ou retomar o consumidor**. Com um consumidor pausado, as mensagens acumulam na fila e voltam a ser processadas ao retomar.
- **Pedidos**: status mudando de `CRIADO` para `PAGO` em tempo real, com botão de cancelar.
- **Atividade**: linha do tempo do que cada parte do sistema fez, colorida por origem (publisher, estoque, pagamento, notificação, DLQ).

## Endpoints

| Método | Caminho | Descrição | Respostas |
|---|---|---|---|
| `POST` | `/pedidos` | Cadastra o pedido e publica `pedido.criado` | 201, 400 |
| `GET` | `/pedidos` | Lista os pedidos | 200 |
| `GET` | `/pedidos/{id}` | Busca um pedido | 200, 404 |
| `POST` | `/pedidos/{id}/cancelar` | Cancela e publica `pedido.cancelado` | 200, 404, 409 |

Endpoints de apoio usados pela interface:

| Método | Caminho | Descrição |
|---|---|---|
| `GET` | `/monitoramento/filas` | Mensagens e consumidores de cada fila |
| `GET` | `/monitoramento/atividades?apos={id}` | Linha do tempo (últimas 300 atividades) |
| `POST` | `/monitoramento/consumidores/{id}/pausar` | Pausa um consumidor (`estoque`, `pagamento`, `notificacao` ou `dlq`) |
| `POST` | `/monitoramento/consumidores/{id}/retomar` | Retoma o consumidor |
| `POST` | `/demo/json-malformado` | Publica um JSON quebrado em `pedido.criado` |
| `POST` | `/demo/evento-invalido` | Publica um evento com quantidade 0 em `pedido.criado` |

Corpo do `POST /pedidos`:

```json
{
  "cliente": "Maria",
  "produto": "Notebook",
  "quantidade": 2,
  "valor": 3500
}
```

O status do pedido evolui assim: `CRIADO` → `PAGO` (quando o pagamento processa, cerca de 1 s depois) e, se cancelado, `CANCELADO`.

Exemplos prontos estão em [pedidos.http](pedidos.http) (IntelliJ ou extensão REST Client do VS Code). Com curl:

```bash
curl -X POST localhost:8080/pedidos -H 'Content-Type: application/json' \
  -d '{"cliente":"Maria","produto":"Notebook","quantidade":2,"valor":3500}'
```

Saída esperada no log da aplicação:

```
[PUBLISHER] pedido.criado publicado para o pedido 1
[ESTOQUE] Reservando 2x Notebook do pedido 1
[NOTIFICACAO] E-mail enviado para Maria: seu pedido 1 (Notebook) foi recebido e está aguardando pagamento
[PAGAMENTO] Processando cobrança de R$ 3500 do pedido 1
[PUBLISHER] pedido.pago publicado para o pedido 1
[PAGAMENTO] Pagamento aprovado para o pedido 1
[NOTIFICACAO] E-mail enviado para Maria: seu pedido 1 (Notebook) teve o pagamento aprovado
```

## Tratamento de mensagens inválidas

Toda fila de consumo tem `x-dead-letter-exchange = pedido.dlx`. Uma mensagem rejeitada sem *requeue* é encaminhada pelo RabbitMQ para `pedido.dlq`, onde o `DlqConsumer` registra no log a fila de origem e o motivo (lidos do header `x-death`).

| Cenário | O que acontece | Tentativas |
|---|---|---|
| JSON malformado | Falha na conversão → rejeitada → DLQ | 1 (sem retry) |
| Payload inválido (`quantidade <= 0`, `pedidoId` nulo, ...) | `PedidoEvent.validar()` lança `AmqpRejectAndDontRequeueException` → DLQ | 1 (sem retry) |
| Pedido inexistente no pagamento | `AmqpRejectAndDontRequeueException` → DLQ | 1 (sem retry) |
| Falha transitória (pedido acima de R$ 10.000 simula o gateway fora do ar) | Retry com backoff de 1 s e 2 s → esgotou → DLQ | 3 |

Mensagens inválidas não são retentadas porque nunca vão dar certo. Quem decide isso é o `RabbitRetryTemplateCustomizer` em `RabbitMQConfig`. Já `default-requeue-rejected=false` evita o loop infinito de uma mensagem que volta para a fila e falha de novo.

Para ver as mensagens paradas na DLQ pelo Management (em vez de consumidas pelo `DlqConsumer`), suba a aplicação com `dlq.consumer.auto-startup=false`:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--dlq.consumer.auto-startup=false
```

## Roteiro de demonstração

Deixe a interface web e o RabbitMQ Management abertos lado a lado.

1. **Topologia** — No Management, em *Exchanges → pedido.events → Bindings*, mostrar as quatro ligações e as routing keys. Em *Queues and Streams*, mostrar as quatro filas e o argumento `DLX` nelas.
2. **Fluxo feliz** — Na interface, clicar em *Pedido normal*. Na linha do tempo aparecem o publisher, o estoque, a notificação e o pagamento reagindo, seguidos do `pedido.pago` encadeado. Na tabela, o status muda de `CRIADO` para `PAGO`.
3. **Filas enchendo e esvaziando** — Clicar em *Carga: 20 pedidos*. O pagamento leva 1 s por pedido, então `pagamento.queue` acumula mensagens enquanto estoque e notificação esvaziam na hora.
4. **Taxas** — No Management, em *Overview*, acompanhar *Publish rate* e *Deliver rate* durante o passo anterior.
5. **Consumidor parado** — Clicar em *Pausar* na `pagamento.queue` e criar alguns pedidos. As mensagens acumulam na fila e os pedidos ficam `CRIADO`. Ao clicar em *Retomar*, tudo é processado. Nada se perde porque quem guarda as mensagens é o broker, não a aplicação.
6. **Mensagem inválida** — Clicar em *JSON malformado*. A linha do tempo mostra três registros `DLQ`, um por fila ligada a `pedido.criado`, sem nenhuma nova tentativa. Repetir com *Evento inválido*. Para ver as mensagens paradas na DLQ, pausar o consumidor da `pedido.dlq` antes e depois inspecionar pelo Management (*Queues → pedido.dlq → Get messages*).
7. **Falha transitória** — Clicar em *Falha no pagamento*. A linha do tempo mostra três tentativas de cobrança espaçadas por 1 s e 2 s e, em seguida, a mensagem na DLQ. O pedido permanece `CRIADO`.

Também é possível publicar a mensagem inválida pelo próprio Management, em *Exchanges → pedido.events → Publish message*, com routing key `pedido.criado`, a property `content_type` = `application/json` e o payload `{ isto não é json`.

## Testes

```bash
./mvnw verify
```

Os testes de integração usam [Testcontainers](https://testcontainers.com) para subir um RabbitMQ real, por isso precisam do Docker rodando. Eles cobrem o fluxo criado → pago, a pausa e retomada de um consumidor, o retry seguido de DLQ, o JSON malformado, o payload inválido e o log do `DlqConsumer`.

## Estrutura

```
src/main/java/br/edu/mensageria
├── config/        RabbitMQConfig (exchanges, filas, bindings, DLX, conversor JSON, retry)
├── pedido/        Pedido, PedidoController, PedidoService, PedidoRepository, dto/, tratamento de erros da API
├── messaging/     PedidoEvent, TipoEvento, PedidoEventPublisher
├── estoque/       EstoqueConsumer
├── pagamento/     PagamentoConsumer
├── notificacao/   NotificacaoConsumer
├── dlq/           DlqConsumer
└── monitoramento/ RegistroDeAtividades e endpoints de apoio à interface web

src/main/resources/static   interface web (index.html, styles.css, app.js)
```

## Configurações (`application.properties`)

| Propriedade | Padrão | Uso |
|---|---|---|
| `spring.rabbitmq.host` | `localhost` | Host do broker |
| `spring.rabbitmq.listener.simple.retry.max-attempts` | `3` | Tentativas antes da DLQ |
| `pagamento.tempo-processamento-ms` | `1000` | Tempo simulado de cada cobrança |
| `pagamento.limite-gateway` | `10000` | Acima desse valor o gateway simula falha |
| `dlq.consumer.auto-startup` | `true` | `false` deixa as mensagens acumuladas na DLQ |

## Observação sobre alterações na topologia

O RabbitMQ não permite redeclarar uma fila existente com argumentos diferentes (por exemplo, adicionar ou remover a DLX). Se isso acontecer, a aplicação falha com `PRECONDITION_FAILED`. Nesse caso, apague a fila pelo Management (*Queues → fila → Delete*) ou recrie o container com `docker compose down && docker compose up -d`.

## Referências

- [Documentação RabbitMQ](https://www.rabbitmq.com/docs)
- [Tutoriais RabbitMQ](https://www.rabbitmq.com/tutorials)
- [Spring Boot — AMQP/RabbitMQ](https://docs.spring.io/spring-boot/reference/messaging/amqp.html)
