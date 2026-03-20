import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;
import org.msgpack.value.impl.ImmutableStringValueImpl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class BotCliente {

    private static final Logger log = Logger.getLogger(BotCliente.class.getName());

    private final ZMQ.Socket socket;
    private final String     username;

    public BotCliente(ZMQ.Socket socket, String username) {
        this.socket   = socket;
        this.username = username;
    }

    private byte[] pack(String type, java.util.function.Consumer<MessageBufferPacker> payloadWriter)
            throws IOException {
        MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
        packer.packMapHeader(3);
        packer.packString("type");
        packer.packString(type);
        packer.packString("timestamp");
        packer.packDouble((double) System.currentTimeMillis() / 1000.0);
        packer.packString("payload");
        payloadWriter.accept(packer);
        packer.close();
        return packer.toByteArray();
    }

    private Map<Value, Value> unpack(byte[] data) throws IOException {
        MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(data);
        Value value = unpacker.unpackValue();
        unpacker.close();
        return value.asMapValue().map();
    }

    private Value key(String k) {
        return new ImmutableStringValueImpl(k);
    }

    public boolean login() throws IOException {
        int attempts = 0;
        while (true) {
            attempts++;
            log.info("Tentativa de login " + attempts + " para: " + username);

            byte[] req = pack("login", p -> {
                try {
                    p.packMapHeader(1);
                    p.packString("username");
                    p.packString(username);
                } catch (IOException e) { throw new RuntimeException(e); }
            });

            socket.send(req);
            byte[] raw = socket.recv();

            Map<Value, Value> resp    = unpack(raw);
            Map<Value, Value> payload = resp.get(key("payload")).asMapValue().map();

            boolean success = payload.get(key("success")).asBooleanValue().getBoolean();
            if (success) {
                log.info("Login bem-sucedido: " + username);
                return true;
            }

            String error = payload.get(key("error")).asStringValue().asString();
            log.warning("Erro no login: " + error);

            if ("user_already_logged_in".equals(error)) {
                log.severe("Usuário já logado — abortando.");
                return false;
            }

            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        }
    }

    public List<String> listChannels() throws IOException {
        byte[] req = pack("list_channels", p -> {
            try {
                p.packMapHeader(1);
                p.packString("username");
                p.packString(username);
            } catch (IOException e) { throw new RuntimeException(e); }
        });

        socket.send(req);
        byte[] raw = socket.recv();

        Map<Value, Value> resp    = unpack(raw);
        Map<Value, Value> payload = resp.get(key("payload")).asMapValue().map();

        List<String> channels = new ArrayList<>();
        boolean success = payload.get(key("success")).asBooleanValue().getBoolean();

        if (success) {
            for (Value v : payload.get(key("channels")).asArrayValue()) {
                channels.add(v.asStringValue().asString());
            }
            log.info("Canais disponíveis: " + channels);
        } else {
            String error = payload.get(key("error")).asStringValue().asString();
            log.warning("Erro ao listar canais: " + error);
        }

        return channels;
    }

    public boolean createChannel(String channelName) throws IOException {
        byte[] req = pack("create_channel", p -> {
            try {
                p.packMapHeader(2);
                p.packString("username");
                p.packString(username);
                p.packString("channel");
                p.packString(channelName);
            } catch (IOException e) { throw new RuntimeException(e); }
        });

        socket.send(req);
        byte[] raw = socket.recv();

        Map<Value, Value> resp    = unpack(raw);
        Map<Value, Value> payload = resp.get(key("payload")).asMapValue().map();

        boolean success = payload.get(key("success")).asBooleanValue().getBoolean();
        if (success) {
            log.info("Canal criado: " + channelName);
        } else {
            String error = payload.get(key("error")).asStringValue().asString();
            log.warning("Erro ao criar canal '" + channelName + "': " + error);
        }
        return success;
    }

    public static void main(String[] args) throws Exception {
        String serverAddr  = System.getenv().getOrDefault("SERVER_ADDR",  "tcp://server:5555");
        String botName     = System.getenv().getOrDefault("BOT_NAME",     "bot1");
        String channelName = System.getenv().getOrDefault("CHANNEL_NAME", "geral");

        log.info("Conectando em " + serverAddr + " como " + botName);

        try (ZContext ctx = new ZContext()) {
            ZMQ.Socket socket = ctx.createSocket(SocketType.REQ);
            socket.connect(serverAddr);

            BotCliente bot = new BotCliente(socket, botName);

            if (!bot.login()) {
                log.severe("Não foi possível fazer login. Encerrando.");
                System.exit(1);
            }

            List<String> channels = bot.listChannels();

            if (!channels.contains(channelName)) {
                bot.createChannel(channelName);
            } else {
                log.info("Canal '" + channelName + "' já existe, pulando criação.");
            }

            bot.listChannels();
        }
    }
}