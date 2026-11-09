import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import javax.swing.*;
import javax.swing.text.*;

public class ClienteGrafico extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final String HOST = "localhost";
    private static final int PORTA = 5000;

    private final Socket socket;
    private final BufferedReader entrada;
    private final PrintWriter saida;

    private final JTextPane conversa = new JTextPane();
    private final JTextField campo = new JTextField();
    private final JButton botaoEnviar = new JButton("Enviar");

    private final Style estiloVoce = criarEstilo("voce", new Color(0x1565C0), true, false);
    private final Style estiloBot = criarEstilo("bot", new Color(0x2E7D32), true, false);
    private final Style estiloTexto = criarEstilo("texto", new Color(0x212121), false, false);
    private final Style estiloAviso = criarEstilo("aviso", Color.GRAY, false, true);

    private volatile boolean encerrando = false;

    public ClienteGrafico(Socket socket) throws IOException {
        super("Chatbot - Loja de Discos Vinil");
        this.socket = socket;
        this.entrada = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.saida = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                true);

        montarJanela();
        adicionarAviso("Conectado ao chatbot! Digite sua pergunta (ou Sair para encerrar).\n"
                + "Dica: digite Ajuda para ver o que o bot sabe responder.");

        Thread leitor = new Thread(this::escutarServidor);
        leitor.setDaemon(true);
        leitor.start();
    }

    private void montarJanela() {
        conversa.setEditable(false);
        conversa.setBackground(Color.WHITE);
        conversa.setMargin(new Insets(8, 8, 8, 8));
        campo.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));

        JPanel rodape = new JPanel(new BorderLayout(8, 0));
        rodape.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        rodape.add(campo, BorderLayout.CENTER);
        rodape.add(botaoEnviar, BorderLayout.EAST);

        add(new JScrollPane(conversa), BorderLayout.CENTER);
        add(rodape, BorderLayout.SOUTH);

        campo.addActionListener(e -> enviar());
        botaoEnviar.addActionListener(e -> enviar());

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(520, 640);
        setLocationRelativeTo(null);
    }

    private Style criarEstilo(String nome, Color cor, boolean negrito, boolean italico) {
        Style estilo = conversa.addStyle(nome, null);
        StyleConstants.setFontFamily(estilo, Font.SANS_SERIF);
        StyleConstants.setFontSize(estilo, 15);
        StyleConstants.setForeground(estilo, cor);
        StyleConstants.setBold(estilo, negrito);
        StyleConstants.setItalic(estilo, italico);
        return estilo;
    }

    private void enviar() {
        String texto = campo.getText().trim();
        if (texto.isEmpty()) {
            return;
        }
        campo.setText("");
        adicionar("Você", estiloVoce, texto);

        if (texto.equalsIgnoreCase("sair")) {
            encerrando = true;
        }
        saida.println(texto);
    }

    private void escutarServidor() {
        try {
            String linha;
            while ((linha = entrada.readLine()) != null) {
                String resposta = linha;
                SwingUtilities.invokeLater(() -> adicionar("Bot", estiloBot, resposta));
                if (encerrando) {
                    break;
                }
            }
        } catch (IOException e) {
        }
        SwingUtilities.invokeLater(this::finalizarConversa);
    }

    private void finalizarConversa() {
        campo.setEnabled(false);
        botaoEnviar.setEnabled(false);
        adicionarAviso(encerrando ? "Conversa encerrada. Pode fechar a janela."
                                  : "A conexão com o servidor foi encerrada.");
        try {
            socket.close();
        } catch (IOException e) {
        }
    }

    private void adicionar(String autor, Style estiloAutor, String texto) {
        try {
            StyledDocument doc = conversa.getStyledDocument();
            doc.insertString(doc.getLength(), autor + ": ", estiloAutor);
            doc.insertString(doc.getLength(), texto + "\n\n", estiloTexto);
            conversa.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) {
        }
    }

    private void adicionarAviso(String texto) {
        try {
            StyledDocument doc = conversa.getStyledDocument();
            doc.insertString(doc.getLength(), texto + "\n\n", estiloAviso);
            conversa.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) {
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
        }

        try {
            Socket socket = new Socket(HOST, PORTA);
            SwingUtilities.invokeLater(() -> {
                try {
                    new ClienteGrafico(socket).setVisible(true);
                } catch (IOException e) {
                    mostrarErro("Erro ao preparar a conversa: " + e.getMessage());
                }
            });
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> mostrarErro(
                    "Não consegui conectar ao servidor (" + HOST + ":" + PORTA + ").\n"
                            + "O servidor está rodando?"));
        }
    }

    private static void mostrarErro(String mensagem) {
        JOptionPane.showMessageDialog(null, mensagem, "Chatbot", JOptionPane.ERROR_MESSAGE);
        System.exit(1);
    }
}
