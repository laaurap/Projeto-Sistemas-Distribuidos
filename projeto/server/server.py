import zmq
import msgpack
import sqlite3
import time
import re
import os
import logging

logging.basicConfig(level=logging.INFO, format="%(asctime)s [SERVER] %(message)s")
log = logging.getLogger(__name__)

DB_PATH = os.environ.get("DB_PATH", "server_data.db")
BIND_ADDR = os.environ.get("BIND_ADDR", "tcp://*:5555")

def init_db():
    conn = sqlite3.connect(DB_PATH)
    conn.execute("""
        CREATE TABLE IF NOT EXISTS logins (
            id        INTEGER PRIMARY KEY AUTOINCREMENT,
            username  TEXT NOT NULL,
            timestamp REAL NOT NULL
        )
    """)
    conn.execute("""
        CREATE TABLE IF NOT EXISTS channels (
            name      TEXT PRIMARY KEY,
            created_by TEXT NOT NULL,
            timestamp  REAL NOT NULL
        )
    """)
    conn.commit()
    return conn

def save_login(conn, username, ts):
    conn.execute("INSERT INTO logins (username, timestamp) VALUES (?, ?)", (username, ts))
    conn.commit()

def channel_exists(conn, name):
    row = conn.execute("SELECT 1 FROM channels WHERE name = ?", (name,)).fetchone()
    return row is not None

def save_channel(conn, name, created_by, ts):
    conn.execute("INSERT INTO channels (name, created_by, timestamp) VALUES (?, ?, ?)", (name, created_by, ts))
    conn.commit()

def list_channels(conn):
    rows = conn.execute("SELECT name FROM channels ORDER BY name").fetchall()
    return [r[0] for r in rows]

def now():
    return time.time()

def make_response(msg_type, payload):
    return msgpack.packb({"type": msg_type, "timestamp": now(), "payload": payload})

def is_valid_name(name):
    return bool(name and re.fullmatch(r"[a-zA-Z0-9_\-]{1,32}", name))

def handle_login(conn, payload, sessions):
    username = payload.get("username", "").strip()
    if not is_valid_name(username):
        return make_response("login_response", {"success": False, "error": "invalid_username"})
    if username in sessions:
        return make_response("login_response", {"success": False, "error": "user_already_logged_in"})
    sessions.add(username)
    save_login(conn, username, now())
    log.info(f"Login OK: {username}")
    return make_response("login_response", {"success": True, "error": None})

def handle_create_channel(conn, payload, sessions):
    username = payload.get("username", "")
    channel  = payload.get("channel", "").strip()
    if username not in sessions:
        return make_response("create_channel_response", {"success": False, "error": "not_logged_in"})
    if not is_valid_name(channel):
        return make_response("create_channel_response", {"success": False, "error": "invalid_channel_name"})
    if channel_exists(conn, channel):
        return make_response("create_channel_response", {"success": False, "error": "channel_already_exists"})
    save_channel(conn, channel, username, now())
    log.info(f"Canal criado: {channel} por {username}")
    return make_response("create_channel_response", {"success": True, "error": None})

def handle_list_channels(conn, payload, sessions):
    username = payload.get("username", "")
    if username not in sessions:
        return make_response("list_channels_response", {"success": False, "error": "not_logged_in", "channels": []})
    channels = list_channels(conn)
    log.info(f"Listagem para {username}: {channels}")
    return make_response("list_channels_response", {"success": True, "error": None, "channels": channels})

def main():
    conn    = init_db()
    context = zmq.Context()
    socket  = context.socket(zmq.REP)
    socket.bind(BIND_ADDR)
    log.info(f"Servidor rodando em {BIND_ADDR}")

    sessions = set()

    HANDLERS = {
        "login":          handle_login,
        "create_channel": handle_create_channel,
        "list_channels":  handle_list_channels,
    }

    while True:
        raw = socket.recv()
        try:
            msg      = msgpack.unpackb(raw, raw=False)
            msg_type = msg.get("type", "")
            payload  = msg.get("payload", {})
            log.info(f"Recebido: type={msg_type}")
            handler  = HANDLERS.get(msg_type)
            if handler:
                response = handler(conn, payload, sessions)
            else:
                response = make_response("error", {"error": "unknown_message_type"})
        except Exception as e:
            log.error(f"Erro: {e}")
            response = make_response("error", {"error": "internal_server_error"})
        socket.send(response)

if __name__ == "__main__":
    main()