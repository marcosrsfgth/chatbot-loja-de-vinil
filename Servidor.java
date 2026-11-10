import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class Servidor {

    private static final int PORTA = 5000;
    private static final String DESPEDIDA = "Obrigado por utilizar nosso sistema!";

    private static final boolean SIMULAR_DIGITACAO = Boolean.parseBoolean(System.getProperty("bot.digitacao", "true"));
    private static final int ATRASO_BASE_MS = 500;
    private static final int ATRASO_POR_CARACTERE_MS = 22;
    private static final int ATRASO_MAXIMO_MS = 3000;

    public static void main(String[] args) {
        Bot bot = new Bot();

        try (ServerSocket servidor = new ServerSocket(PORTA)) {
            System.out.println("Chatbot no ar na porta " + PORTA
                    + ". Aguardando clientes (Ctrl+C para encerrar)...");
            System.out.println(bot.statusIA());

            while (true) {
                try (Socket cliente = servidor.accept()) {
                    System.out.println("Cliente conectado: "
                            + cliente.getInetAddress().getHostAddress());
                    bot.novaConversa();
                    atender(cliente, bot);
                } catch (IOException e) {
                    System.out.println("Conversa interrompida: " + e.getMessage());
                }
                System.out.println("Cliente desconectado. Aguardando o próximo...");
            }
        } catch (IOException e) {
            System.out.println("Não foi possível abrir a porta " + PORTA + ": " + e.getMessage());
        }
    }

    private static void atender(Socket cliente, Bot bot) throws IOException {
        BufferedReader entrada = new BufferedReader(
                new InputStreamReader(cliente.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter saida = new PrintWriter(
                new OutputStreamWriter(cliente.getOutputStream(), StandardCharsets.UTF_8),
                true);

        String mensagem;
        while ((mensagem = entrada.readLine()) != null) {
            System.out.println("Cliente: " + mensagem);

            if (mensagem.trim().equalsIgnoreCase("sair")) {
                simularDigitacao(DESPEDIDA, System.currentTimeMillis());
                saida.println(DESPEDIDA);
                System.out.println("Bot: " + DESPEDIDA);
                break;
            }

            long inicio = System.currentTimeMillis();
            Bot.Resposta resposta = bot.responder(mensagem);
            simularDigitacao(resposta.texto, inicio);
            saida.println(resposta.texto);
            System.out.println("Bot [" + resposta.origem + "]: " + resposta.texto);
        }
    }

    private static void simularDigitacao(String texto, long inicio) {
        if (!SIMULAR_DIGITACAO) {
            return;
        }
        double variacao = 0.8 + Math.random() * 0.4;
        long alvo = (long) ((ATRASO_BASE_MS + ATRASO_POR_CARACTERE_MS * texto.length()) * variacao);
        alvo = Math.min(alvo, ATRASO_MAXIMO_MS);
        long falta = alvo - (System.currentTimeMillis() - inicio);
        if (falta > 0) {
            try {
                Thread.sleep(falta);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
