# Chatbot com sockets em Java

Chatbot de uma loja de discos fictícia, a Vinil, feito para uma atividade da faculdade. O servidor é o bot e fica esperando mensagens; os clientes (terminal ou janela) são os usuários. O servidor atende um cliente por vez.

Sem chave de API, o bot responde só com perguntas cadastradas e palavras-chave. Com uma chave, ele também usa a API do Claude para conversar de forma mais livre. Se a IA falhar, ele volta para as respostas cadastradas.
<img width="500" height="626" alt="image" src="https://github.com/user-attachments/assets/93a9ed58-8856-4069-b3c1-56d7ec5160fb" />


## Como executar

Precisa de JDK 11 ou superior e dos três `.jar` do Jackson 2.17.2 (`jackson-databind`, `jackson-core` e `jackson-annotations`) na pasta `lib/`. Rode os comandos na pasta que contém os `.java` e a `lib/`.

Compilar (Windows):

```
javac -encoding UTF-8 -cp "lib/*;." Servidor.java Bot.java AssistenteIA.java Cliente.java ClienteGrafico.java
```

Em um terminal, iniciar o servidor:

```
java -cp "lib/*;." Servidor
```

Em outro terminal, iniciar um cliente (`Cliente` para o terminal ou `ClienteGrafico` para a janela):

```
java -cp "lib/*;." Cliente
```

No macOS e no Linux, troque `;` por `:` nos comandos. Se aparecer `Address already in use`, já existe um servidor rodando: feche-o com Ctrl+C ou mude `PORTA` nos arquivos.

## Exemplo

```
Você: Oi
Bot: E aí! Seja bem-vindo à Vinil. O que você está procurando?
Você: que horas vocês abrem?
Bot: Nosso horário é de segunda a sábado, das 10h às 19h. Esperamos você por lá!
Você: Sair
Bot: Obrigado por utilizar nosso sistema!
```

`Ajuda` lista as perguntas cadastradas e `Sair` encerra a conversa. As respostas variam de uma conversa para outra.

## Estrutura

| Arquivo | Função |
| --- | --- |
| `Servidor.java` | Sockets: espera clientes, repassa as mensagens ao bot e envia as respostas |
| `Bot.java` | Decide a resposta e guarda as perguntas cadastradas |
| `AssistenteIA.java` | Chama a API do Claude (opcional), usando Jackson para o JSON |
| `Cliente.java` | Cliente de terminal |
| `ClienteGrafico.java` | Cliente com janela (Swing) |
| `lib/` | Bibliotecas do Jackson |

## Como o bot responde

1. Pergunta cadastrada, comparando sem maiúsculas, acentos e pontuação.
2. Palavras-chave: reconhece outras formas de perguntar a mesma coisa e tolera um erro de digitação em palavras longas. Pelo menos metade das palavras importantes da mensagem precisa casar.
3. IA, se houver chave de API.
4. Mensagem padrão ("não entendi"), com algumas versões.

Com chave de API a ordem muda: a IA responde primeiro, recebendo as últimas 10 mensagens da conversa como contexto, e as camadas 1, 2 e 4 viram plano B. Para usar a ordem acima mesmo com chave, defina `BOT_MODO=classico`.

Cada pergunta cadastrada tem mais de uma versão de resposta, e o bot evita repetir a mesma duas vezes seguidas. Os clientes mostram "Bot está digitando..." e o servidor espera de cerca de 0,5 a 3 segundos, conforme o tamanho da resposta (constantes `ATRASO_*` no `Servidor.java`; `-Dbot.digitacao=false` desliga a pausa).

## Personalizando

As perguntas ficam no método `cadastrarPerguntas()` do `Bot.java`, junto com `NOME_LOJA`:

```java
cadastrar("Qual o endereço?",
        variantes("Estamos na Rua da Música, 45, Centro.",
                  "Ficamos na Rua da Música, 45, no Centro. Passa lá!"),
        "enderec*", "onde", "fica");
```

Palavra-chave terminada em `*` vale como radical (`enderec*` casa com endereço e endereços). A primeira versão da resposta é a que a IA recebe como informação da loja, e todas as versões devem dizer a mesma coisa.

## Usando a IA (opcional)

1. Crie uma chave de API no Claude Console (platform.claude.com), escolhendo um workspace específico. A API é cobrada à parte da assinatura do Claude.ai, com créditos pré-pagos.
2. Defina a chave na variável de ambiente `ANTHROPIC_API_KEY`, no terminal em que vai rodar o servidor (PowerShell: `$env:ANTHROPIC_API_KEY = "sua-chave"`; macOS e Linux: `export ANTHROPIC_API_KEY="sua-chave"`).
3. Inicie o servidor. Ele mostra se a IA está ativada e em qual modo.

A chave nunca deve ir para o código nem para o repositório. Se ela faltar, estiver errada ou a internet cair, o servidor mostra o motivo e o bot usa as respostas cadastradas. As mensagens enviadas à IA vão para a API da Anthropic. Variáveis opcionais: `ANTHROPIC_MODEL` (padrão `claude-haiku-5-5`) e `ANTHROPIC_WORKSPACE_ID`.

## Limitações

- Um cliente por vez, como pedia a atividade; atender vários ao mesmo tempo exigiria threads.
- As palavras-chave são uma heurística e perguntas ambíguas podem receber a resposta errada.
- Se o terminal do Windows não reconhecer acentos digitados, escreva sem acento.
