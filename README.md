# CC7261 – Sistemas Distribuídos

## Introdução

Este projeto implementa um sistema distribuído de troca de mensagens inspirado
em BBS/IRC, utilizando arquitetura cliente-servidor com múltiplos servidores,
replicação de dados, comunicação assíncrona e sincronização distribuída.

O sistema permite:
- Login de usuários
- Criação e listagem de canais
- Publicação de mensagens em canais
- Assinatura de canais via Pub/Sub
- Replicação automática entre servidores
- Eleição de coordenador
- Sincronização de relógios físico e lógico

---

## Tecnologias utilizadas

| Componente       | Linguagem   | Tecnologia           |
|------------------|-------------|----------------------|
| Servidor         | Python 3.12 | ZeroMQ + SQLite      |
| Cliente/Bot      | Java 17     | ZeroMQ + MessagePack |
| Proxy Pub/Sub    | Python 3.12 | ZeroMQ XPUB/XSUB     |
| Serv. Referência | Python 3.12 | ZeroMQ               |

---

## Arquitetura Geral

```
            +-------------------------+
            |  Serviço de Referência  |
            |  (Ranks / Heartbeat)    |
            +----------+--------------+
                       |
           +-----------+-----------+
           |                       |
           v                       v
     +-----------+           +-----------+
     | Server 1  |<--------->| Server 2  |
     +-----------+ Replicação+-----------+
           ^                       ^
           |                       |
           +-----------+-----------+
                       |
                 Proxy Pub/Sub
                (XPUB / XSUB)
                       |
          +------------+------------+
          |                         |
          v                         v
     +----------+             +----------+
     | Client 1 |             | Client 2 |
     +----------+             +----------+
```

---

## Serialização — MessagePack

Todas as mensagens utilizam serialização binária com **MessagePack**.
Essa escolha foi feita por ser um formato compacto, sem necessidade de
definir schema, com suporte nativo em Python (`msgpack`) e Java (`msgpack-core`),
e por ser significativamente mais eficiente que JSON ou XML.

## Comunicação REQ/REP

A comunicação entre clientes e servidores utiliza ZeroMQ no padrão REQ/REP.

Operações implementadas:

| Tipo           | Descrição                       |
|----------------|---------------------------------|
| login          | Autenticação do bot             |
| create_channel | Criação de canal                |
| list_channels  | Listagem de canais disponíveis  |
| publish        | Publicação de mensagem em canal |

---

## Proxy Pub/Sub

O sistema utiliza um proxy XPUB/XSUB para distribuição de mensagens nos canais.

- Publishers (servidores) conectam em `tcp://proxy:5557`
- Subscribers (clientes) conectam em `tcp://proxy:5558`

Isso desacopla publishers e subscribers, permitindo que múltiplos servidores
publiquem e múltiplos clientes recebam sem conhecimento mútuo.

---

## Persistência — SQLite

Cada servidor possui seu próprio banco SQLite local, não compartilhado.

Tabelas persistidas:

**logins** — username + timestamp do login

**channels** — nome, criador e timestamp de criação

**publications** — canal, usuário, mensagem, timestamp de envio,
timestamp de publicação e relógio lógico

## Relógio Lógico de Lamport

Todas as mensagens trocadas possuem o campo `logical_clock`.

Regras implementadas:

**Envio:**
```python
logical_clock += 1
```

**Recebimento:**
```python
logical_clock = max(local, recebido) + 1
```

Isso garante ordenação causal parcial dos eventos no sistema distribuído.
O relógio lógico está presente em todas as mensagens: cliente↔servidor,
servidor↔servidor e servidor↔referência.

---

## Eleição de Coordenador 

Funcionamento:

1. Cada servidor recebe um rank do serviço de referência na inicialização
2. O servidor com **maior rank** tem prioridade para ser coordenador
3. Ao iniciar, cada servidor envia mensagem de eleição para servidores com rank maior
4. Se nenhum responder, o servidor se proclama coordenador
5. O novo coordenador publica no tópico `servers` via proxy para todos saberem

Em caso de falha do coordenador:
- O servidor que detectar a falha (timeout na sincronização) inicia nova eleição
- O processo se repete até um novo coordenador ser eleito

A comunicação entre servidores usa sockets **DEALER/ROUTER** na porta `5560`.

---

### Método escolhido

Foi implementada **replicação ativa com propagação imediata** (eager replication).

### Como resolve o problema

Sem replicação, cada servidor teria apenas uma parte dos dados pois cada
cliente se conecta a um servidor específico. Com a replicação ativa, toda
operação importante é imediatamente propagada para todos os outros servidores.
Assim, todos os servidores possuem todos os dados e a perda de um servidor
não implica perda de histórico.

### Como foi implementado

Após salvar localmente um `publish` ou `create_channel`, o servidor dispara
uma thread em background que:

1. Consulta a lista de servidores ativos no serviço de referência
2. Para cada servidor diferente de si mesmo, envia a mensagem de replicação
   via socket DEALER/ROUTER na porta 5560

O servidor receptor salva o dado localmente apenas se ainda não o possuir
(verificação de duplicatas antes de inserir).

### Adaptações necessárias

A principal adaptação foi reaproveitar o canal de comunicação servidor-servidor
(porta 5560) já criado para eleição e sincronização de relógio, adicionando
os novos tipos de mensagem ao handler existente.

A replicação é feita de forma **assíncrona** (em thread separada) para não
bloquear a resposta ao cliente. O socket foi alterado de REP/REQ para
**ROUTER/DEALER** para suportar múltiplas conexões simultâneas sem conflito
de estado.

---

## Como executar

### Pré-requisitos
- Docker
- Docker Compose

### Subir o sistema
```bash
docker compose up --build
```

### Encerrar
```bash
docker compose down
```

### Demonstrar eleição

Para demonstrar a eleição após queda do coordenador:

```bash
# Terminal 1 — subir tudo
docker compose up --build

# Terminal 2 — observar server2
docker compose logs -f server2

# Terminal 3 — verificar quem é o coordenador
docker compose logs | grep COORDENADOR

# Terminal 3 — parar o coordenador
docker stop server1
```

No Terminal 2 será possível observar server2 detectando a falha e
se elegendo novo coordenador.

---

## Estrutura do projeto

```
projeto/
├── server/
│   ├── server.py            # Servidor Python
│   └── Dockerfile
├── client/
│   ├── src/main/java/
│   │   └── BotCliente.java  # Cliente Java
│   ├── pom.xml
│   └── Dockerfile
├── proxy/
│   ├── proxy.py             # Proxy XSUB/XPUB
│   └── Dockerfile
├── reference/
│   ├── reference.py         # Serviço de referência
│   └── Dockerfile
├── docker-compose.yaml
└── README.md
```
