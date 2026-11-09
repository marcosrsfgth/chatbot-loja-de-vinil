import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AssistenteIA {

    private static final String ENV_CHAVE = "ANTHROPIC_API_KEY";
    private static final String ENV_MODELO = "ANTHROPIC_MODEL";
    private static final String ENV_VERSAO = "ANTHROPIC_VERSION";
    private static final String ENV_WORKSPACE = "ANTHROPIC_WORKSPACE_ID";
    private static final String ENV_URL = "ANTHROPIC_BASE_URL";

    private static final String MODELO_PADRAO = "claude-haiku-5-5";
    private static final String VERSAO_PADRAO = "2026-10-21";
    private static final String URL_PADRAO = "https://api.anthropic.com";

    private static final int MAX_TOKENS = 400;
    private static final int SEGUNDOS_CONEXAO = 5;
    private static final int SEGUNDOS_RESPOSTA = 15;

    private final String chave;
    private final String modelo;
    private final String versao;
    private final String workspace;
    private final String urlBase;
    private final String instrucoes;
    private final HttpClient http;

    public AssistenteIA(String instrucoes) {
        this.instrucoes = instrucoes;
        this.chave = ambiente(ENV_CHAVE, null);
        this.modelo = ambiente(ENV_MODELO, MODELO_PADRAO);
        this.versao = ambiente(ENV_VERSAO, VERSAO_PADRAO);
        this.workspace = ambiente(ENV_WORKSPACE, null);
        this.urlBase = ambiente(ENV_URL, URL_PADRAO).replaceAll("/+$", "");
        this.http = (chave == null) ? null
                : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(SEGUNDOS_CONEXAO)).build();
    }

    public String status() {
        if (chave == null) {
            return "IA: desativada (defina a variável " + ENV_CHAVE + " para ativar). "
                    + "O bot usa só as respostas cadastradas e as palavras-chave.";
        }
        return "IA: ativada (modelo " + modelo + "). Ela só é usada quando nenhuma pergunta cadastrada casa.";
    }

    public String perguntar(String mensagem) {
        if (chave == null) {
            return null;
        }
        try {
            HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create(urlBase + "/v1/messages"))
                    .timeout(Duration.ofSeconds(SEGUNDOS_RESPOSTA))
                    .header("content-type", "application/json")
                    .header("authorization", "Bearer " + chave)
                    .header("anthropic-version", versao);
            if (workspace != null) {
                pedido.header("anthropic-workspace-id", workspace);
            }

            HttpResponse<String> resposta = http.send(
                    pedido.POST(HttpRequest.BodyPublishers.ofString(montarCorpo(mensagem), StandardCharsets.UTF_8)).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (resposta.statusCode() != 200) {
                System.out.println("IA indisponível (HTTP " + resposta.statusCode() + "): " + resumir(resposta.body()));
                return null;
            }

            String texto = extrairTexto(resposta.body());
            if (texto == null) {
                System.out.println("IA indisponível: a resposta veio sem texto.");
                return null;
            }
            texto = texto.replaceAll("\\s+", " ").trim();
            return texto.isEmpty() ? null : texto;

        } catch (IOException e) {
            String detalhe = (e.getMessage() == null) ? "sem internet ou API fora do ar?" : e.getMessage();
            System.out.println("IA indisponível: " + e.getClass().getSimpleName() + " (" + detalhe + ")");
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (RuntimeException e) {
            System.out.println("IA indisponível: " + e);
            return null;
        }
    }

    private String montarCorpo(String mensagem) {
        return "{"
                + "\"model\":\"" + escapar(modelo) + "\","
                + "\"max_tokens\":" + MAX_TOKENS + ","
                + "\"thinking\":{\"type\":\"disabled\"},"
                + "\"output_config\":{\"effort\":\"low\"},"
                + "\"system\":\"" + escapar(instrucoes) + "\","
                + "\"messages\":[{\"role\":\"user\",\"content\":\"" + escapar(mensagem) + "\"}]"
                + "}";
    }

    private static String escapar(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    static String extrairTexto(String json) {
        Matcher m = Pattern.compile("\"text\"\\s*:\\s*\"").matcher(json);
        while (m.find()) {
            StringBuilder sb = new StringBuilder();
            boolean fechou = false;
            for (int i = m.end(); i < json.length() && !fechou; i++) {
                char c = json.charAt(i);
                if (c == '"') {
                    fechou = true;
                } else if (c == '\\' && i + 1 < json.length()) {
                    char n = json.charAt(++i);
                    switch (n) {
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u':
                            sb.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                            i += 4;
                            break;
                        default: sb.append(n);
                    }
                } else {
                    sb.append(c);
                }
            }
            if (fechou && sb.length() > 0) {
                return sb.toString();
            }
        }
        return null;
    }

    private static String resumir(String corpo) {
        String s = corpo.replaceAll("\\s+", " ").trim();
        return s.length() > 220 ? s.substring(0, 220) + "..." : s;
    }

    private static String ambiente(String nome, String padrao) {
        String v = System.getenv(nome);
        return (v == null || v.isBlank()) ? padrao : v.trim();
    }
}
