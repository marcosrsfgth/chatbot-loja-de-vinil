import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class Servidor {

    private static final int PORTA = 5000;
    private static final String DESPEDIDA = "Obrigado por utilizar nosso sistema!";

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
                saida.println(DESPEDIDA);
                System.out.println("Bot: " + DESPEDIDA);
                break;
            }

            Bot.Resposta resposta = bot.responder(mensagem);
            saida.println(resposta.texto);
            System.out.println("Bot [" + resposta.origem + "]: " + resposta.texto);
        }
    }
}
