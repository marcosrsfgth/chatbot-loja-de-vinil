import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

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
    private final ObjectMapper objectMapper;

    public AssistenteIA(String instrucoes) {
        this.instrucoes = instrucoes;
        this.chave = ambiente(ENV_CHAVE, null);
        this.modelo = ambiente(ENV_MODELO, MODELO_PADRAO);
        this.versao = ambiente(ENV_VERSAO, VERSAO_PADRAO);
        this.workspace = ambiente(ENV_WORKSPACE, null);
        this.urlBase = ambiente(ENV_URL, URL_PADRAO).replaceAll("/+$", "");
        this.http = (chave == null) ? null
                : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(SEGUNDOS_CONEXAO)).build();
        this.objectMapper = new ObjectMapper();
    }

    public String status() {
        if (chave == null) {
            return "IA: desativada (defina a variável " + ENV_CHAVE + " para ativar). "
                    + "O bot usa só as respostas cadastradas e as palavras-chave.";
        }
        return "IA: ativada (modelo " + modelo + ").";
    }

    public boolean ativa() {
        return chave != null;
    }

    public String perguntar(String mensagem, List<String[]> historico) {
        if (chave == null) {
            return null;
        }
        try {
            String jsonCorpo = montarCorpo(mensagem, historico);

            HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create(urlBase + "/v1/messages"))
                    .timeout(Duration.ofSeconds(SEGUNDOS_RESPOSTA))
                    .header("content-type", "application/json")
                    .header("authorization", "Bearer " + chave)
                    .header("anthropic-version", versao);
            if (workspace != null) {
                pedido.header("anthropic-workspace-id", workspace);
            }

            HttpResponse<String> resposta = http.send(
                    pedido.POST(HttpRequest.BodyPublishers.ofString(jsonCorpo, StandardCharsets.UTF_8)).build(),
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

    private String montarCorpo(String mensagem, List<String[]> historico) throws IOException {
        ObjectNode json = objectMapper.createObjectNode();
        json.put("model", modelo);
        json.put("max_tokens", MAX_TOKENS);

        ObjectNode thinking = json.putObject("thinking");
        thinking.put("type", "disabled");

        ObjectNode outputConfig = json.putObject("output_config");
        outputConfig.put("effort", "low");

        json.put("system", instrucoes);

        ArrayNode messages = json.putArray("messages");
        for (String[] turno : historico) {
            ObjectNode anterior = messages.addObject();
            anterior.put("role", turno[0]);
            anterior.put("content", turno[1]);
        }
        ObjectNode msgUser = messages.addObject();
        msgUser.put("role", "user");
        msgUser.put("content", mensagem);

        return objectMapper.writeValueAsString(json);
    }

    private String extrairTexto(String jsonResposta) {
        try {
            JsonNode root = objectMapper.readTree(jsonResposta);
            JsonNode content = root.path("content");
            if (content.isArray()) {
                for (JsonNode node : content) {
                    if ("text".equals(node.path("type").asText())) {
                        return node.path("text").asText();
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao processar JSON da IA: " + e.getMessage());
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
