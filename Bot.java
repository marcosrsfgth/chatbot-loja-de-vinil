import java.text.Normalizer;
import java.util.*;

public class Bot {

    private static final String NOME_LOJA = "Vinil";

    private static final List<String> NAO_ENTENDI = Arrays.asList(
            "Desculpe, não entendi sua pergunta. Pode reformular?",
            "Hmm, essa eu não consegui entender. Pode perguntar de outro jeito?",
            "Não tenho certeza do que você quis dizer. Posso ajudar com horários, preços, tipos de disco, endereço, entrega e formas de pagamento.");

    private static final List<String> MENSAGEM_VAZIA = Arrays.asList(
            "Pode mandar a sua pergunta, estou por aqui.",
            "Não recebi nada por aqui. Em que posso ajudar?");

    private static final double CONFIANCA_MINIMA = 0.5;

    private static final int TAMANHO_MINIMO_PARA_TOLERAR_ERRO = 7;

    private static final int MAXIMO_DE_MENSAGENS_NO_HISTORICO = 10;

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
        final List<String> respostas;
        final boolean listarNaAjuda;
        final List<String> palavrasChave = new ArrayList<>();

        Entrada(String pergunta, List<String> respostas, boolean listarNaAjuda, String[] palavrasChave) {
            this.pergunta = pergunta;
            this.respostas = respostas;
            this.listarNaAjuda = listarNaAjuda;
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
    private final List<String[]> historico = new ArrayList<>();
    private final Random sorteio = new Random();
    private List<String> respostasSaudacao = NAO_ENTENDI;
    private String ultimaResposta = "";
    private final AssistenteIA ia;
    private final boolean iaPrimeiro;

    public Bot() {
        cadastrarPerguntas();
        this.ia = new AssistenteIA(montarInstrucoesDaIA());
        String modo = System.getenv("BOT_MODO");
        boolean classico = modo != null && normalizar(modo).equals("classico");
        this.iaPrimeiro = ia.ativa() && !classico;
    }

    public String statusIA() {
        if (!ia.ativa()) {
            return ia.status();
        }
        return ia.status() + (iaPrimeiro
                ? " Modo natural: a IA responde primeiro e as respostas cadastradas são o plano B."
                : " Modo clássico: a IA só é usada quando nenhuma pergunta cadastrada casa.");
    }

    public void novaConversa() {
        historico.clear();
        ultimaResposta = "";
    }

    public Resposta responder(String mensagem) {
        if (mensagem.trim().isEmpty()) {
            return new Resposta(escolher(MENSAGEM_VAZIA), "mensagem vazia");
        }

        Resposta resposta = decidir(mensagem);

        historico.add(new String[] {"user", mensagem});
        historico.add(new String[] {"assistant", resposta.texto});
        while (historico.size() > MAXIMO_DE_MENSAGENS_NO_HISTORICO) {
            historico.remove(0);
            historico.remove(0);
        }
        return resposta;
    }

    private Resposta decidir(String mensagem) {
        String chave = normalizar(mensagem);

        if (chave.equals("ajuda")) {
            return new Resposta(textoDaAjuda(), "ajuda");
        }

        boolean iaTentada = false;
        if (iaPrimeiro) {
            iaTentada = true;
            String daIA = ia.perguntar(mensagem, historico);
            if (daIA != null) {
                return new Resposta(daIA, "IA");
            }
        }

        Entrada exata = porPergunta.get(chave);
        if (exata != null) {
            return new Resposta(escolher(exata.respostas), "pergunta cadastrada");
        }

        if (soSaudacao(chave)) {
            return new Resposta(escolher(respostasSaudacao), "saudação");
        }

        Entrada parecida = buscarPorPalavrasChave(chave);
        if (parecida != null) {
            return new Resposta(escolher(parecida.respostas), "palavras-chave -> \"" + parecida.pergunta + "\"");
        }

        if (!iaTentada) {
            String daIA = ia.perguntar(mensagem, historico);
            if (daIA != null) {
                return new Resposta(daIA, "IA");
            }
        }

        return new Resposta(escolher(NAO_ENTENDI), "não entendi");
    }

    private String escolher(List<String> opcoes) {
        List<String> candidatas = new ArrayList<>(opcoes);
        candidatas.remove(ultimaResposta);
        if (candidatas.isEmpty()) {
            candidatas = opcoes;
        }
        String escolhida = candidatas.get(sorteio.nextInt(candidatas.size()));
        ultimaResposta = escolhida;
        return escolhida;
    }

    private String textoDaAjuda() {
        List<String> perguntas = new ArrayList<>();
        for (Entrada e : entradas) {
            if (e.listarNaAjuda) {
                perguntas.add(e.pergunta);
            }
        }
        return "Para encerrar, digite Sair. Você pode perguntar: " + String.join(" | ", perguntas);
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

    private static List<String> variantes(String... textos) {
        return Arrays.asList(textos);
    }

    private void cadastrar(String pergunta, String resposta, String... palavrasChave) {
        cadastrar(pergunta, variantes(resposta), palavrasChave);
    }

    private void cadastrar(String pergunta, List<String> respostas, String... palavrasChave) {
        adicionarEntrada(new Entrada(pergunta, respostas, true, palavrasChave));
    }

    private void cadastrarConversa(String pergunta, List<String> respostas, String... palavrasChave) {
        adicionarEntrada(new Entrada(pergunta, respostas, false, palavrasChave));
    }

    private void adicionarEntrada(Entrada e) {
        entradas.add(e);
        porPergunta.put(normalizar(e.pergunta), e);
    }

    private void cadastrarSaudacao(List<String> respostas) {
        cadastrar("Oi", respostas);
        respostasSaudacao = respostas;
    }

    private void cadastrarPerguntas() {
        cadastrarSaudacao(variantes(
                "Olá! Bem-vindo à Vinil, sua loja de discos. Em que posso ajudar?",
                "Oi, tudo bem? Aqui é da Vinil! Como posso te ajudar hoje?",
                "E aí! Seja bem-vindo à Vinil. O que você está procurando?"));

        cadastrar("Tudo bem?",
                variantes("Sim, tudo ótimo! E com você?",
                        "Tudo certo por aqui! E você, como está?",
                        "Tudo bem sim, obrigado por perguntar! Em que posso ajudar?"),
                "tudo", "bem", "vai");

        cadastrar("Qual o horário de funcionamento?",
                variantes("Abrimos de segunda a sábado, das 10h às 19h.",
                        "Nosso horário é de segunda a sábado, das 10h às 19h. Esperamos você por lá!",
                        "Ficamos abertos de segunda a sábado, das 10h às 19h."),
                "hora*", "horário", "abre*", "abri*", "abert*", "fecha*", "funcion*", "expediente",
                "domingo*", "sábado*", "feriado*");

        cadastrar("Vocês compram discos usados?",
                variantes("Sim! Avaliamos a sua coleção e pagamos em dinheiro ou em crédito na loja.",
                        "Compramos sim! A gente avalia a sua coleção e paga em dinheiro ou em crédito na loja."),
                "compram", "compra", "usado*", "vender", "vendo", "troca*", "coleção", "avali*");

        cadastrar("Quais tipos de disco vocês vendem?",
                variantes("Vendemos vinis, CDs e fitas cassete, novos e usados.",
                        "Trabalhamos com vinis, CDs e fitas cassete, tanto novos quanto usados."),
                "tipo*", "vende", "vendem", "vendemos", "produto*", "trabalham", "vinil", "vinis",
                "cd", "cds", "lp", "lps", "fita*", "cassete*", "usado*", "catálogo", "acervo");

        cadastrar("Quais gêneros musicais vocês têm?",
                variantes("Temos rock, MPB, samba, jazz, blues, soul e música eletrônica.",
                        "Nosso acervo tem rock, MPB, samba, jazz, blues, soul e música eletrônica. Tem algum gênero preferido?"),
                "gênero*", "estilo*", "música*", "rock", "mpb", "samba", "jazz", "blues", "soul",
                "eletrônica");

        cadastrar("Quanto custa um disco?",
                variantes("Os vinis usados custam a partir de R$ 40,00 e os novos, a partir de R$ 120,00. Os CDs, a partir de R$ 25,00.",
                        "Depende do formato: vinis usados a partir de R$ 40,00, vinis novos a partir de R$ 120,00 e CDs a partir de R$ 25,00."),
                "quanto", "cust*", "preço*", "valor*", "barato*", "caro", "cobram",
                "vinil", "vinis", "cd", "cds", "lp", "lps");

        cadastrar("Posso ouvir o disco antes de comprar?",
                variantes("Pode sim! Temos uma vitrola na loja para você testar antes de levar.",
                        "Claro! Temos uma vitrola na loja justamente para você ouvir o disco antes de levar."),
                "ouvir", "escut*", "testar", "teste*", "vitrola*", "provar");

        cadastrar("Vocês fazem encomenda?",
                variantes("Sim, encomendamos lançamentos e importados. O prazo é de 15 a 30 dias.",
                        "Fazemos sim! Encomendamos lançamentos e importados, com prazo de 15 a 30 dias."),
                "encomend*", "importad*", "importação", "lançamento*", "novidade*", "reserv*");

        cadastrar("Vocês fazem entrega?",
                variantes("Sim! Enviamos para todo o Brasil pelos Correios, com embalagem reforçada para proteger os discos.",
                        "Fazemos entrega sim: enviamos para todo o Brasil pelos Correios, com embalagem reforçada para proteger os discos."),
                "entreg*", "envi*", "frete*", "correio*", "sedex", "mandam", "mandar", "prazo*");

        cadastrar("Qual o endereço?",
                variantes("Estamos na Rua da Música, 45, Centro.",
                        "Ficamos na Rua da Música, 45, no Centro. Passa lá!"),
                "enderec*", "onde", "fica", "ficam", "localiz*", "local", "chegar", "rua", "bairro",
                "cidade", "mapa", "perto");

        cadastrar("Quais as formas de pagamento?",
                variantes("Aceitamos dinheiro, cartões de crédito e débito e Pix.",
                        "Dá para pagar em dinheiro, no cartão de crédito ou débito e também por Pix."),
                "formas", "pagament*", "pagar", "pago", "paga", "pix", "cartão", "cartões", "crédito",
                "débito", "dinheiro", "parcel*", "boleto*", "aceit*");

        cadastrarConversa("Obrigado!",
                variantes("Imagina! Se precisar de mais alguma coisa, é só falar.",
                        "Por nada! Qualquer dúvida, estou por aqui.",
                        "Disponha! Foi um prazer ajudar."),
                "obrigad*", "brigad*", "valeu", "agradec*", "grato", "grata");

        cadastrarConversa("Tchau!",
                variantes("Até mais! Se quiser encerrar a conversa, é só digitar Sair.",
                        "Tchau! Quando quiser voltar, é só chamar. Para encerrar, digite Sair.",
                        "Foi um prazer ajudar! Se quiser encerrar, digite Sair."),
                "tchau", "adeus", "flw", "xau");

        cadastrarConversa("Quem é você?",
                variantes("Sou o atendente virtual da Vinil! Posso falar de horários, preços, tipos de disco, gêneros, entrega e muito mais.",
                        "Eu sou o assistente virtual da Vinil, a sua loja de discos. Pode perguntar à vontade!"));
    }

    private String montarInstrucoesDaIA() {
        StringBuilder sb = new StringBuilder();
        sb.append("Você é o atendente virtual da loja de discos ").append(NOME_LOJA)
                .append(", conversando por um chat de texto.\n\n");
        sb.append("Como conversar:\n");
        sb.append("- Fale de forma natural, simpática e descontraída, como uma pessoa de verdade, com frases curtas (em geral de 1 a 3).\n");
        sb.append("- Reescreva as informações com as suas próprias palavras, sem copiar o texto oficial, e varie a forma de começar as respostas.\n");
        sb.append("- Use o histórico da conversa: entenda perguntas curtas como \"e os CDs?\" pelo assunto anterior e não repita o que já foi dito.\n");
        sb.append("- Quando fizer sentido, termine com uma pergunta curta ou um convite para continuar a conversa.\n");
        sb.append("- Escreva apenas texto puro: sem markdown, sem listas, sem emojis e sem quebras de linha.\n\n");
        sb.append("Regras:\n");
        sb.append("- Use SOMENTE as informações da loja listadas abaixo. Se a resposta não estiver nelas, ");
        sb.append("diga com naturalidade que não tem essa informação e sugira ir até a loja. ");
        sb.append("Nunca invente preços, títulos em estoque, prazos, promoções ou políticas.\n");
        sb.append("- Se a mensagem não tiver relação com a loja, diga educadamente que só pode ajudar com ");
        sb.append("assuntos da ").append(NOME_LOJA).append(".\n\n");
        sb.append("Informações da loja (pergunta e resposta oficial):\n");
        for (Entrada e : entradas) {
            if (e.listarNaAjuda) {
                sb.append("- P: ").append(e.pergunta).append(" R: ").append(e.respostas.get(0)).append("\n");
            }
        }
        return sb.toString();
    }
}
