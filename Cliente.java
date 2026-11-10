import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class Cliente {

    private static final String HOST = "localhost";
    private static final int PORTA = 5000;

    public static void main(String[] args) {
        try (Socket socket = new Socket(HOST, PORTA);
             Scanner teclado = new Scanner(System.in)) {

            BufferedReader entrada = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter saida = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                    true);

            System.out.println("Conectado ao chatbot! Digite sua pergunta (ou Sair para encerrar).");
            System.out.println("Dica: digite Ajuda para ver o que o bot sabe responder.");

            while (true) {
                System.out.print("Você: ");
                if (!teclado.hasNextLine()) {
                    break;
                }
                String pergunta = teclado.nextLine();
                if (pergunta.trim().isEmpty()) {
                    continue;
                }

                saida.println(pergunta);
                System.out.print("Bot está digitando...");
                String resposta = entrada.readLine();
                System.out.print("\r" + " ".repeat(25) + "\r");
                if (resposta == null) {
                    System.out.println("O servidor encerrou a conexão.");
                    break;
                }
                System.out.println("Bot: " + resposta);

                if (pergunta.trim().equalsIgnoreCase("sair")) {
                    break;
                }
            }
        } catch (ConnectException e) {
            System.out.println("Não consegui conectar. O servidor está rodando na porta " + PORTA + "?");
        } catch (IOException e) {
            System.out.println("Erro de comunicação: " + e.getMessage());
        }
    }
}
