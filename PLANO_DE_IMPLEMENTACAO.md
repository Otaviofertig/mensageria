# Plano de Implementação — Sistema de Pedidos com RabbitMQ

Sistema de pedidos utilizando **Spring Boot**, **REST**, **RabbitMQ** e **processamento assíncrono**.

## Requisitos do projeto

1. Cadastro de pedido
2. Publicação do evento
3. Consumidor de estoque
4. Consumidor de pagamento
5. Consumidor de notificação
6. Exchange `TOPIC`
7. Routing keys
8. Pelo menos três filas
9. Monitoramento pelo RabbitMQ Management
10. Tratamento de mensagens inválidas

## Ambiente

| Ferramenta | Versão / observação |
|---|---|
| Java | 17 |
| Spring Boot | 3.5.x |
| Build | Maven via wrapper (`mvnw`) — não é necessário instalar Maven |
| RabbitMQ | `rabbitmq:4-management` via Docker |
| Portas | `5672` (AMQP) · `15672` (Management UI) · `8080` (API) |

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

   Mensagem inválida / falha após N tentativas ──► [pedido.dlx] ──► pedido.dlq ──► DlqConsumer (log)
```

A topologia demonstra os dois estilos de binding do TOPIC:

- **Chave exata**: `estoque.queue` e `pagamento.queue` recebem apenas eventos específicos.
- **Curinga**: `notificacao.queue` usa `pedido.#` e recebe todos os eventos de pedido.

O consumidor de pagamento consome `pedido.criado` e publica `pedido.pago`, caracterizando um **encadeamento de eventos (coreografia)**.

### Topologia

| Recurso | Tipo | Detalhe |
|---|---|---|
| `pedido.events` | Exchange TOPIC | Durável |
| `estoque.queue` | Queue | Bindings: `pedido.criado`, `pedido.cancelado` |
| `pagamento.queue` | Queue | Binding: `pedido.criado` |
| `notificacao.queue` | Queue | Binding: `pedido.#` |
| `pedido.dlx` | Exchange (dead letter) | Recebe mensagens rejeitadas de todas as filas |
| `pedido.dlq` | Queue | Armazena mensagens inválidas / com falha |

### Eventos (routing keys)

| Routing key | Publicado por | Quando |
|---|---|---|
| `pedido.criado` | Pedido API | Após salvar um novo pedido |
| `pedido.pago` | PagamentoConsumer | Após processar a cobrança |
| `pedido.cancelado` | Pedido API | Ao cancelar um pedido |

## Mapeamento requisito → solução

| # | Requisito | Solução |
|---|---|---|
| 1 | Cadastro de pedido | `POST /pedidos` com `@Valid`, `GET /pedidos`, `GET /pedidos/{id}`, `POST /pedidos/{id}/cancelar` |
| 2 | Publicação do evento | `PedidoEventPublisher` com `RabbitTemplate.convertAndSend(exchange, routingKey, evento)` em JSON |
| 3 | Consumidor de estoque | `@RabbitListener(queues = "estoque.queue")` — reserva no `criado`, devolve no `cancelado` |
| 4 | Consumidor de pagamento | `@RabbitListener(queues = "pagamento.queue")` — simula cobrança e publica `pedido.pago` |
| 5 | Consumidor de notificação | `@RabbitListener(queues = "notificacao.queue")` — loga "e-mail enviado" para qualquer evento |
| 6 | Exchange TOPIC | `TopicExchange("pedido.events")` |
| 7 | Routing keys | `pedido.criado`, `pedido.pago`, `pedido.cancelado` como constantes em `RabbitMQConfig` |
| 8 | ≥ 3 filas | `estoque.queue`, `pagamento.queue`, `notificacao.queue` (+ `pedido.dlq`) |
| 9 | Monitoramento | `docker-compose` com `rabbitmq:4-management` + roteiro de demonstração com prints |
| 10 | Mensagens inválidas | DLX/DLQ + retry + rejeição sem requeue (ver Fase 5) |

## Estrutura de pacotes

```
br.com.intracommerce.mensageria
├── config/          RabbitMQConfig
├── pedido/          Pedido, PedidoController, PedidoService, PedidoRepository, dto/
├── messaging/       PedidoEvent, PedidoEventPublisher
├── estoque/         EstoqueConsumer
├── pagamento/       PagamentoConsumer
├── notificacao/     NotificacaoConsumer
└── dlq/             DlqConsumer
```

## Fases de implementação

### Fase 0 — Ambiente

- [ ] Criar `docker-compose.yml` com `rabbitmq:4-management` (portas 5672 e 15672)
- [ ] Gerar projeto em [start.spring.io](https://start.spring.io): Maven, Java 17, Spring Boot 3.5.x
- [ ] Dependências: Spring Web, Spring for RabbitMQ, Validation (+ Spring Data JPA e H2, conforme decisão 2)
- [ ] Configurar `application.properties` (`spring.rabbitmq.*`)

### Fase 1 — Domínio e API REST

- [ ] Entidade `Pedido`: `id`, `cliente`, `produto`, `quantidade`, `valor`, `status` (`CRIADO`, `PAGO`, `CANCELADO`), `dataCriacao`
- [ ] `PedidoRequest` com validações (`@NotBlank`, `@Positive`, ...)
- [ ] `PedidoController` e `PedidoService`
- [ ] `@RestControllerAdvice` retornando 400 com os erros de validação

### Fase 2 — Topologia RabbitMQ (`RabbitMQConfig`)

- [ ] `TopicExchange` `pedido.events`
- [ ] 3 filas duráveis, cada uma com argumento `x-dead-letter-exchange = pedido.dlx`
- [ ] Bindings conforme tabela de topologia
- [ ] DLX `pedido.dlx` e fila `pedido.dlq`
- [ ] Bean `Jackson2JsonMessageConverter` para serialização JSON automática

> **Atenção:** alterar argumentos de uma fila já existente (ex.: adicionar DLX depois) causa `PRECONDITION_FAILED`. Nesse caso, apagar a fila pelo Management antes de reiniciar a aplicação. Por isso a DLX já é criada nesta fase.

### Fase 3 — Produtor

- [ ] `PedidoEvent` (`record`): `pedidoId`, `tipo`, `dados`, `timestamp`
- [ ] `PedidoEventPublisher` publicando na exchange com a routing key do evento
- [ ] Publicação no `PedidoService` após salvar/cancelar o pedido

### Fase 4 — Consumidores

- [ ] `EstoqueConsumer`
- [ ] `PagamentoConsumer` (publica `pedido.pago`)
- [ ] `NotificacaoConsumer`
- [ ] Logs descritivos, ex.: `[ESTOQUE] Reservando 2x Notebook do pedido 1001`

### Fase 5 — Tratamento de mensagens inválidas

| Cenário | Tratamento |
|---|---|
| JSON malformado | Falha de conversão → rejeitada sem requeue → DLQ |
| Payload inválido (ex.: `quantidade <= 0`, `id` nulo) | Consumidor lança `AmqpRejectAndDontRequeueException` → DLQ |
| Erro transitório (ex.: falha simulada no pagamento) | Retry com backoff (3 tentativas) → esgotou → DLQ |

- [ ] `spring.rabbitmq.listener.simple.retry.enabled=true` (+ `max-attempts`, backoff)
- [ ] `spring.rabbitmq.listener.simple.default-requeue-rejected=false` para evitar loop infinito
- [ ] `DlqConsumer` logando a origem da falha via header `x-death`
- [ ] Demonstração: publicar JSON quebrado pelo Management (Exchanges → Publish message) e observar a chegada na DLQ

### Fase 6 — Monitoramento e documentação

- [ ] `README.md`: como subir o ambiente, arquitetura e endpoints
- [ ] Coleção Postman ou arquivo `.http`
- [ ] Roteiro de demonstração:
  1. Criar pedido e observar as filas enchendo/esvaziando
  2. Acompanhar publish rate / deliver rate no Overview
  3. Parar um consumidor e mostrar mensagens acumulando na fila
  4. Enviar mensagem inválida e mostrar a DLQ
- [ ] Prints da Management UI

## Decisões em aberto

| # | Decisão | Recomendação | Alternativas |
|---|---|---|---|
| 1 | Uma aplicação ou várias? | App única com consumidores em pacotes separados | Multi-módulo com 4 apps (pedido-api, estoque, pagamento, notificacao) |
| 2 | Persistência do pedido | JPA + H2 em memória | Lista em memória · PostgreSQL no Docker |
| 3 | Fluxo do pagamento | `PagamentoConsumer` publica `pedido.pago` automaticamente | Endpoint manual `POST /pedidos/{id}/pagar` · ambos |
| 4 | Formato de entrega | A definir conforme exigência do professor | Relatório · prints · vídeo · repositório Git |

## Referências

- [Documentação RabbitMQ](https://www.rabbitmq.com/docs)
- [Tutoriais RabbitMQ](https://www.rabbitmq.com/tutorials)
- [Spring Boot — AMQP/RabbitMQ](https://docs.spring.io/spring-boot/reference/messaging/amqp.html)
