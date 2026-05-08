# CC7261 – Sistemas Distribuídos

# Sistema Distribuído de Mensagens Instantâneas

## Introdução

Este projeto implementa um sistema distribuído de troca de mensagens inspirado
em BBS/IRC, utilizando arquitetura cliente-servidor com múltiplos servidores,
replicação de dados, comunicação assíncrona e sincronização distribuída.

O sistema permite:

- Login de usuários
- Criação de canais
- Listagem de canais
- Publicação de mensagens
- Assinatura de canais via Pub/Sub
- Replicação automática entre servidores
- Balanceamento de carga
- Eleição de coordenador
- Sincronização de relógios

---

# Tecnologias utilizadas

| Componente | Linguagem | Tecnologia |
|---|---|---|
| Servidor | Python 3.12 | ZeroMQ + SQLite |
| Cliente/Bot | Java 17 | ZeroMQ |
| Proxy Pub/Sub | Python | ZeroMQ XPUB/XSUB |
| Broker Req/Rep | Python | ZeroMQ |
| Persistência | SQLite | Banco local |

---

# Arquitetura Geral

```text
                +-------------------+
                | Serviço de        |
                | Referência        |
                | (Ranks/Heartbeat) |
                +---------+---------+
                          |
                          |
            +-------------+-------------+
            |                           |
            v                           v

      +-----------+             +-----------+
      | Server 1  |<----------->| Server 2  |
      +-----------+  Replicação +-----------+
            ^                           ^
            |                           |
            +-------------+-------------+
                          |
                    Broker Req/Rep
                    (Round-Robin)
                          |
             +------------+------------+
             |                         |
             v                         v
        +----------+             +----------+
        | Client 1 |             | Client 2 |
        +----------+             +----------+

                          |
                    Proxy Pub/Sub
                    (XPUB / XSUB)
                          |
                 Mensagens em canais
```

---

# Serialização — MessagePack

Todas as mensagens utilizam serialização binária com MessagePack.

Formato padrão:

```json
{
  "type": "publish",
  "timestamp": 1746650000.0,
  "logical_clock": 15,
  "payload": {}
}
```

Campos:

| Campo | Descrição |
|---|---|
| type | Tipo da mensagem |
| timestamp | Relógio físico |
| logical_clock | Relógio lógico de Lamport |
| payload | Conteúdo específico |

---

# Comunicação Req/Rep

A comunicação entre clientes e servidores utiliza ZeroMQ no padrão REQ/REP.

Operações implementadas:

- login
- create_channel
- list_channels
- publish

---

# Broker Req/Rep

Foi implementado um broker intermediário responsável pelo balanceamento
de carga entre os servidores.

O broker:

- recebe requisições dos clientes;
- distribui utilizando round-robin;
- alterna entre server1 e server2;
- encaminha a resposta ao cliente.

Isso evita que todos os clientes utilizem sempre o mesmo servidor.

---

# Proxy Pub/Sub

O sistema utiliza um proxy XPUB/XSUB para distribuição de mensagens
dos canais.

- publishers conectam em tcp://proxy:5557
- subscribers conectam em tcp://proxy:5558

Isso desacopla publishers e subscribers.

---

# Persistência — SQLite

Cada servidor possui seu próprio banco SQLite local.

Dados persistidos:

## Logins
- username
- timestamp

## Canais
- nome
- criador
- timestamp

## Publicações
- canal
- usuário
- mensagem
- timestamp de envio
- timestamp de publicação
- relógio lógico

---

# Serviço de Referência

O serviço de referência mantém:

- lista de servidores ativos;
- ranks únicos;
- heartbeat;
- detecção de falha por timeout.

Mensagens implementadas:

- get_rank
- list_servers
- heartbeat

---

# Relógio Lógico de Lamport

Todas as mensagens trocadas possuem relógio lógico.

Regras implementadas:

## Envio
```python
logical_clock += 1
```

## Recebimento
```python
logical_clock = max(local, recebido) + 1
```

Isso garante ordenação lógica parcial dos eventos distribuídos.

---

# Eleição de Coordenador

Foi implementado algoritmo de eleição baseado em ranks.

Funcionamento:

1. servidores recebem ranks do serviço de referência;
2. o maior rank possui prioridade;
3. em caso de falha do coordenador:
   - inicia-se eleição;
   - servidores de rank maior assumem;
   - novo coordenador é publicado via Pub/Sub.

---

# Sincronização de Relógios — Berkeley

O coordenador fornece horário de referência para os demais servidores.

Periodicamente:

1. servidor consulta coordenador;
2. recebe horário atual;
3. calcula offset local;
4. ajusta relógio lógico/físico.

---

# Replicação de Dados

Foi implementada replicação ativa entre os servidores.

Como o broker distribui requisições em round-robin, diferentes operações
podem chegar em servidores distintos.

Para evitar inconsistências, toda operação importante é replicada:

## Replicações implementadas

- replicate_login
- replicate_channel
- replicate_publication

## Funcionamento

1. servidor recebe operação;
2. salva localmente;
3. consulta lista de servidores ativos;
4. envia replicação para os demais servidores.

Assim:

- todos os servidores possuem os mesmos canais;
- todos os servidores possuem os mesmos logins;
- todos os servidores possuem todas as mensagens.

---

# Como executar

## Pré-requisitos

- Docker
- Docker Compose

---

## Subir o sistema

```bash
docker compose up --build
```

O sistema iniciará:

- serviço de referência
- proxy Pub/Sub
- broker Req/Rep
- 2 servidores
- 2 bots clientes

---

## Encerrar

```bash
docker compose down -v
```

---

# Estrutura do projeto

```text
projeto/
├── broker/
│   ├── broker.py
│   └── Dockerfile
│
├── proxy/
│   ├── proxy.py
│   └── Dockerfile
│
├── reference/
│   ├── reference.py
│   └── Dockerfile
│
├── server/
│   ├── server.py
│   └── Dockerfile
│
├── client/
│   ├── src/main/java/
│   │   └── BotCliente.java
│   ├── pom.xml
│   └── Dockerfile
│
├── docker-compose.yaml
└── README.md
```
