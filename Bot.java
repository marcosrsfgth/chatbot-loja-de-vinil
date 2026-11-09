import java.text.Normalizer;
import java.util.*;

public class Bot {

    private static final String NOME_LOJA = "Vinil";
    private static final String NAO_ENTENDI = "Desculpe, não entendi sua pergunta. Pode reformular?";

    private static final double CONFIANCA_MINIMA = 0.5;

    private static final int TAMANHO_MINIMO_PARA_TOLERAR_ERRO = 7;

    private static final Set<String> SAUDACOES = palavras(
            "oi", "ola", "opa", "salve", "eai", "hey", "hello", "bom", "boa", "dia", "tarde", "noite");

    private static final Set<String> IRRELEVANTES = palavras(
            "a", "o", "as", "os", "um", "uma", "uns", "umas", "de", "do", "da", "dos", "das",
            "em", "no", "na", "nos", "nas", "ao", "aos", "pelo", "pela", "pelos", "pelas", "num", "numa",
            "por", "para", "pra", "com", "sem", "e", "ou", "que", "qual", "quais", "quem", "como",
            "voce", "voces", "vc", "vcs", "tem", "tenho", "ter", "eu", "me", "meu", "meus", "minha",
            "minhas", "seu", "sua", "seus", "suas", "ai", "la", "ja", "so", "mais", "muito", "favor",
            "gostaria", "queria", "quero", "saber", "pode", "podem", "posso", "poderia",
            "faco", "fazer", "faz", "fazem", "hoje", "antes", "depois", "levar", "comprar",
            "loja", "lojas", "disco", "discos");

    public static class Resposta {
        public final String texto;
        public final String origem;

        Resposta(String texto, String origem) {
            this.texto = texto;
            this.origem = origem;
        }
    }

    private static class Entrada {
        final String pergunta;
        final String resposta;
        final List<String> palavrasChave = new ArrayList<>();

        Entrada(String pergunta, String resposta, String[] palavrasChave) {
            this.pergunta = pergunta;
            this.resposta = resposta;
            for (String p : palavrasChave) {
                boolean radical = p.endsWith("*");
                String base = normalizar(radical ? p.substring(0, p.length() - 1) : p);
                this.palavrasChave.add(radical ? base + "*" : base);
            }
        }

        int pontuar(Set<String> palavrasDaMensagem) {
            int acertos = 0;
            for (String palavra : palavrasDaMensagem) {
                for (String chave : palavrasChave) {
                    if (casa(palavra, chave)) {
                        acertos++;
                        break;
                    }
                }
            }
            return acertos;
        }
    }

    private final List<Entrada> entradas = new ArrayList<>();
    private final Map<String, Entrada> porPergunta = new HashMap<>();
    private String respostaSaudacao = NAO_ENTENDI;
    private final AssistenteIA ia;

    public Bot() {
        cadastrarPerguntas();
        this.ia = new AssistenteIA(montarInstrucoesDaIA());
    }

    public String statusIA() {
        return ia.status();
    }

    public Resposta responder(String mensagem) {
        String chave = normalizar(mensagem);

        if (chave.equals("ajuda")) {
            List<String> perguntas = new ArrayList<>();
            for (Entrada e : entradas) {
                perguntas.add(e.pergunta);
            }
            return new Resposta("Para encerrar, digite Sair. Você pode perguntar: "
                    + String.join(" | ", perguntas), "ajuda");
        }

        Entrada exata = porPergunta.get(chave);
        if (exata != null) {
            return new Resposta(exata.resposta, "pergunta cadastrada");
        }

        if (soSaudacao(chave)) {
            return new Resposta(respostaSaudacao, "saudação");
        }

        Entrada parecida = buscarPorPalavrasChave(chave);
        if (parecida != null) {
            return new Resposta(parecida.resposta, "palavras-chave -> \"" + parecida.pergunta + "\"");
        }

        String daIA = ia.perguntar(mensagem);
        if (daIA != null) {
            return new Resposta(daIA, "IA");
        }

        return new Resposta(NAO_ENTENDI, "não entendi");
    }

    private Entrada buscarPorPalavrasChave(String chave) {
        Set<String> palavras = new LinkedHashSet<>();
        for (String p : chave.split(" ")) {
            if (!p.isEmpty() && !IRRELEVANTES.contains(p) && !SAUDACOES.contains(p)) {
                palavras.add(p);
            }
        }
        if (palavras.isEmpty()) {
            return null;
        }

        Entrada melhor = null;
        int melhorPontos = 0;
        for (Entrada e : entradas) {
            int pontos = e.pontuar(palavras);
            if (pontos > melhorPontos) {
                melhor = e;
                melhorPontos = pontos;
            }
        }

        double confianca = (double) melhorPontos / palavras.size();
        return (melhor != null && confianca >= CONFIANCA_MINIMA) ? melhor : null;
    }

    private boolean soSaudacao(String chave) {
        boolean temSaudacao = false;
        for (String p : chave.split(" ")) {
            if (p.isEmpty()) {
                continue;
            }
            if (SAUDACOES.contains(p)) {
                temSaudacao = true;
            } else if (!IRRELEVANTES.contains(p)) {
                return false;
            }
        }
        return temSaudacao;
    }

    static String normalizar(String texto) {
        String t = Normalizer.normalize(texto, Normalizer.Form.NFD);
        t = t.replaceAll("\\p{M}", "");
        t = t.toLowerCase();
        t = t.replaceAll("[^a-z0-9 ]", " ");
        return t.replaceAll("\\s+", " ").trim();
    }

    private static boolean casa(String palavra, String chave) {
        boolean radical = chave.endsWith("*");
        String base = radical ? chave.substring(0, chave.length() - 1) : chave;

        if (radical ? palavra.startsWith(base) : palavra.equals(base)) {
            return true;
        }
        if (base.length() >= TAMANHO_MINIMO_PARA_TOLERAR_ERRO && palavra.length() >= TAMANHO_MINIMO_PARA_TOLERAR_ERRO) {
            String trecho = radical ? palavra.substring(0, Math.min(palavra.length(), base.length())) : palavra;
            return distancia(trecho, base) <= 1;
        }
        return false;
    }

    private static int distancia(String a, String b) {
        int[] anterior = new int[b.length() + 1];
        int[] atual = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            anterior[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            atual[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int custo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                atual[j] = Math.min(Math.min(atual[j - 1] + 1, anterior[j] + 1), anterior[j - 1] + custo);
            }
            int[] troca = anterior;
            anterior = atual;
            atual = troca;
        }
        return anterior[b.length()];
    }

    private static Set<String> palavras(String... lista) {
        return new HashSet<>(Arrays.asList(lista));
    }

    private void cadastrar(String pergunta, String resposta, String... palavrasChave) {
        Entrada e = new Entrada(pergunta, resposta, palavrasChave);
        entradas.add(e);
        porPergunta.put(normalizar(pergunta), e);
    }

    private void cadastrarSaudacao(String resposta) {
        cadastrar("Oi", resposta);
        respostaSaudacao = resposta;
    }

    private void cadastrarPerguntas() {
        cadastrarSaudacao("Olá! Bem-vindo à Vinil, sua loja de discos. Em que posso ajudar?");

        cadastrar("Tudo bem?",
                "Sim, tudo ótimo! E com você?",
                "tudo", "bem", "vai");

        cadastrar("Qual o horário de funcionamento?",
                "Abrimos de segunda a sábado, das 10h às 19h.",
                "hora*", "horário", "abre*", "abri*", "abert*", "fecha*", "funcion*", "expediente",
                "domingo*", "sábado*", "feriado*");

        cadastrar("Vocês compram discos usados?",
                "Sim! Avaliamos a sua coleção e pagamos em dinheiro ou em crédito na loja.",
                "compram", "compra", "usado*", "vender", "vendo", "troca*", "coleção", "avali*");

        cadastrar("Quais tipos de disco vocês vendem?",
                "Vendemos vinis, CDs e fitas cassete, novos e usados.",
                "tipo*", "vende", "vendem", "vendemos", "produto*", "trabalham", "vinil", "vinis",
                "cd", "cds", "lp", "lps", "fita*", "cassete*", "usado*", "catálogo", "acervo");

        cadastrar("Quais gêneros musicais vocês têm?",
                "Temos rock, MPB, samba, jazz, blues, soul e música eletrônica.",
                "gênero*", "estilo*", "música*", "rock", "mpb", "samba", "jazz", "blues", "soul",
                "eletrônica");

        cadastrar("Quanto custa um disco?",
                "Os vinis usados custam a partir de R$ 40,00 e os novos, a partir de R$ 120,00. Os CDs, a partir de R$ 25,00.",
                "quanto", "cust*", "preço*", "valor*", "barato*", "caro", "cobram",
                "vinil", "vinis", "cd", "cds", "lp", "lps");

        cadastrar("Posso ouvir o disco antes de comprar?",
                "Pode sim! Temos uma vitrola na loja para você testar antes de levar.",
                "ouvir", "escut*", "testar", "teste*", "vitrola*", "provar");

        cadastrar("Vocês fazem encomenda?",
                "Sim, encomendamos lançamentos e importados. O prazo é de 15 a 30 dias.",
                "encomend*", "importad*", "importação", "lançamento*", "novidade*", "reserv*");

        cadastrar("Vocês fazem entrega?",
                "Sim! Enviamos para todo o Brasil pelos Correios, com embalagem reforçada para proteger os discos.",
                "entreg*", "envi*", "frete*", "correio*", "sedex", "mandam", "mandar", "prazo*");

        cadastrar("Qual o endereço?",
                "Estamos na Rua da Música, 45, Centro.",
                "enderec*", "onde", "fica", "ficam", "localiz*", "local", "chegar", "rua", "bairro",
                "cidade", "mapa", "perto");

        cadastrar("Quais as formas de pagamento?",
                "Aceitamos dinheiro, cartões de crédito e débito e Pix.",
                "formas", "pagament*", "pagar", "pago", "paga", "pix", "cartão", "cartões", "crédito",
                "débito", "dinheiro", "parcel*", "boleto*", "aceit*");
    }

    private String montarInstrucoesDaIA() {
        StringBuilder sb = new StringBuilder();
        sb.append("Você é o atendente virtual da loja de discos ").append(NOME_LOJA)
                .append(", conversando por um chat simples de texto.\n\n");
        sb.append("Regras:\n");
        sb.append("- Responda em português do Brasil, com simpatia, em no máximo duas frases curtas.\n");
        sb.append("- Escreva apenas texto puro: sem markdown, sem listas, sem emojis e sem quebras de linha.\n");
        sb.append("- Use SOMENTE as informações da loja listadas abaixo. Se a resposta não estiver nelas, ");
        sb.append("diga que não tem essa informação e sugira ir até a loja. ");
        sb.append("Nunca invente preços, títulos em estoque, prazos, promoções ou políticas.\n");
        sb.append("- Se a mensagem não tiver relação com a loja, diga educadamente que só pode ajudar com ");
        sb.append("assuntos da ").append(NOME_LOJA).append(".\n\n");
        sb.append("Informações da loja (pergunta e resposta oficial):\n");
        for (Entrada e : entradas) {
            sb.append("- P: ").append(e.pergunta).append(" R: ").append(e.resposta).append("\n");
        }
        return sb.toString();
    }
}
