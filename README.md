# CC7261 – Sistemas Distribuídos — Projeto Parte 1

## Introdução

Este projeto é uma versão simplificada de um sistema de troca de mensagens
instantâneas inspirado no Bulletin Board System (BBS) e Internet Relay Chat (IRC).
O sistema permite que usuários (bots) realizem login, criem e listem canais
de mensagens públicas.

## Tecnologias utilizadas

| Componente    | Linguagem   | Tecnologia          |
|---------------|-------------|---------------------|
| Servidor      | Python 3.12 | ZeroMQ + SQLite     |
| Cliente (Bot) | Java 17     | ZeroMQ              |

## Escolhas técnicas

### Serialização — MessagePack
Todas as mensagens trocadas entre cliente e servidor são serializadas em
binário usando **MessagePack**. Essa escolha foi feita por ser um formato
compacto, sem necessidade de definir schema, e com suporte em Python
(`msgpack`) e Java (`msgpack-core`).

Toda mensagem segue a estrutura:
```
{
  "type":      string,  // tipo da operação
  "timestamp": float,   // epoch em segundos (obrigatório em todas as mensagens)
  "payload":   map      // conteúdo específico da operação
}
```

### Transporte — ZeroMQ REQ/REP
A comunicação usa **ZeroMQ** no padrão REQ/REP, conforme definido no enunciado,
onde o cliente envia uma requisição e o servidor responde.

### Persistência — SQLite
O servidor armazena os dados em um banco **SQLite** local. Cada servidor
mantém seu próprio arquivo, não compartilhado com outros servidores:
- Histórico de logins (username + timestamp)
- Canais criados (nome + criador + timestamp)

## Arquitetura
```
[Bot Java]                     [Servidor Python]
    │                                 │
    │── login ────────────────────────►│
    │◄─ login_response ───────────────│
    │                                 │
    │── list_channels ───────────────►│
    │◄─ list_channels_response ───────│
    │                                 │
    │── create_channel ──────────────►│
    │◄─ create_channel_response ──────│
    │                                 │
    │── list_channels ───────────────►│
    │◄─ list_channels_response ───────│
                                      │
                                 [SQLite DB]
```

## Como rodar o projeto

### Pré-requisitos
- Docker
- Docker Compose

### Executar
```bash
cd projeto
docker compose up --build
```

Esse comando irá:
1. Compilar a imagem do servidor Python
2. Compilar a imagem do cliente Java
3. Subir 2 servidores e 2 clientes simultaneamente
4. Cada cliente realiza login, cria um canal e lista os canais do seu servidor

### Parar
```bash
docker compose down
```

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
├── docker-compose.yaml
└── README.md
```

## Erros tratados

| Código                    | Descrição                              |
|---------------------------|----------------------------------------|
| `invalid_username`        | Nome vazio ou com caracteres inválidos |
| `user_already_logged_in`  | Usuário já possui sessão ativa         |
| `not_logged_in`           | Operação sem login prévio              |
| `invalid_channel_name`    | Nome de canal inválido                 |
| `channel_already_exists`  | Canal duplicado                        |
