# Chatbot com Sockets em Java: Loja de Discos Vinil

Chatbot em que o **servidor é o bot** (fica esperando mensagens) e o **cliente é o usuário** (digita as perguntas). A comunicação usa sockets TCP e atende **um cliente por vez**.

Extras além do enunciado: cliente com interface gráfica, reconhecimento de perguntas por palavras-chave, camada **opcional** de inteligência artificial e manipulação de JSON via **Jackson**.

## Requisitos

- JDK 11 ou superior (confira com `javac -version`).
- Biblioteca **Jackson** (arquivos `.jar` para processamento de JSON) alocados na pasta `lib/`.

## Arquivos

| Arquivo / Pasta | Função |
| --- | --- |
| `Servidor.java` | Rede: abre a porta, espera clientes e repassa cada mensagem ao bot |
| `Bot.java` | Inteligência: guarda as perguntas cadastradas e decide a resposta em camadas |
| `AssistenteIA.java` | Camada opcional: consulta a API do Claude usando Jackson para montar/ler JSON |
| `Cliente.java` | Cliente de console (terminal) |
| `ClienteGrafico.java` | Cliente com janela de chat (Swing) |
| `lib/` | Pasta contendo os arquivos `.jar` do Jackson (`jackson-databind`, `jackson-core`, `jackson-annotations`) |

## Como executar

Na pasta do projeto, compile informando o caminho das dependências da biblioteca Jackson (`-cp`):

**No Windows (PowerShell / CMD):**
```bash
javac -encoding UTF-8 -cp "lib/*;." Servidor.java Bot.java AssistenteIA.java Cliente.java ClienteGrafico.java
```

**No macOS / Linux:**
```bash
javac -encoding UTF-8 -cp "lib/*:." Servidor.java Bot.java AssistenteIA.java Cliente.java ClienteGrafico.java
```

Em um terminal, inicie o servidor (sempre primeiro):

**No Windows:**
```bash
java -cp "lib/*;." Servidor
```
**No macOS / Linux:**
```bash
java -cp "lib/*:." Servidor
```

Em um segundo terminal, inicie um cliente de console:

**No Windows:**
```bash
java -cp "lib/*;." Cliente
```
**No macOS / Linux:**
```bash
java -cp "lib/*:." Cliente
```

Ou, para abrir a janela de chat gráfica:

**No Windows:**
```bash
java -cp "lib/*;." ClienteGrafico
```
**No macOS / Linux:**
```bash
java -cp "lib/*:." ClienteGrafico
```

## Exemplo de conversa

```
Você: Oi
Bot: Olá! Bem-vindo à Vinil Vivo, sua loja de discos. Em que posso ajudar?
Você: que horas vocês abrem?
Bot: Abrimos de segunda a sábado, das 10h às 19h.
Você: Vocês têm o disco Abbey Road?
Bot: Desculpe, não entendi sua pergunta. Pode reformular?
Você: Sair
Bot: Obrigado por utilizar nosso sistema!
```

*(Com a IA ativada, a pergunta sobre o Abbey Road seria respondida pela IA em vez da mensagem padrão).*

## Como o bot decide a resposta

O `Bot` tenta quatro camadas, da mais simples para a mais "esperta", e para na primeira que resolver:

1. **Pergunta cadastrada**: igual a uma pergunta do cadastro, ignorando maiúsculas, acentos e pontuação ("Qual o HORÁRIO de funcionamento?").
2. **Palavras-chave**: reconhece outras formas de perguntar a mesma coisa ("que horas vocês abrem?") e tolera erro de digitação em palavras longas ("horarrio"). É preciso que pelo menos metade das palavras importantes da mensagem case; em caso de empate, vale a pergunta cadastrada primeiro.
3. **Inteligência artificial (opcional)**: só entra se existir a chave de API (veja abaixo). A IA recebe as perguntas e respostas do cadastro como base de conhecimento, para não inventar informações.
4. **Mensagem padrão**: "Desculpe, não entendi sua pergunta. Pode reformular?"

No terminal do servidor aparece qual camada respondeu, por exemplo `Bot [IA]: ...` ou `Bot [palavras-chave -> "Qual o endereço?"]: ...`.

Comandos especiais: `Ajuda` lista as perguntas cadastradas e `Sair` encerra a conversa.

## Personalizando o bot

Edite o método `cadastrarPerguntas()` e a constante `NOME_LOJA` no `Bot.java`. Cada pergunta tem a resposta e as palavras-chave que a identificam:

```java
cadastrar("Qual o endereço?",
        "Estamos na Rua da Música, 45, Centro.",
        "enderec*", "onde", "fica");
```

Uma palavra-chave terminada em `*` vale como radical: `enderec*` casa com endereço, endereços... A IA usa o mesmo cadastro, então não precisa configurar mais nada nela. Se mudar o nome da loja, troque também o título da janela no `ClienteGrafico.java`.

## Ativando a IA (opcional)

Sem chave, o bot funciona normalmente só com as camadas 1, 2 e 4. Para ativar a camada 3 (usa a API do Claude, modelo `claude-haiku-5-5`):

1. No Claude Console (platform.claude.com), abra **API keys** e crie uma chave. Ao criar, escolha um workspace específico (por exemplo, o Default). O Console mostra a chave completa uma única vez.
2. A API é cobrada à parte da assinatura do Claude.ai, por créditos pré-pagos no Console (os planos Max e Team incluem créditos mensais de API). Cada pergunta custa uma fração mínima de centavo, mas confira os preços atuais no Console.
3. No mesmo terminal em que vai rodar o servidor, defina a variável de ambiente:
   - PowerShell (Windows): `$env:ANTHROPIC_API_KEY = "cole-sua-chave-aqui"`
   - Prompt de comando (Windows): `set ANTHROPIC_API_KEY=cole-sua-chave-aqui`
   - macOS e Linux: `export ANTHROPIC_API_KEY="cole-sua-chave-aqui"`
4. Rode o servidor com o classpath do Jackson. Ele deve mostrar `IA: ativada (modelo claude-haiku-5-5)`.

Cuidados:

- **Nunca** escreva a chave no código, no README nem dentro do .zip. Ela é lida só do ambiente. Se vazar, apague a chave no Console.
- As mensagens que chegam à camada 3 são enviadas à API da Anthropic.
- Se a chave faltar, estiver errada, ou a internet cair, o bot não quebra: responde com a mensagem padrão e o servidor mostra o motivo (por exemplo, `IA indisponível (HTTP 401)`).
- Variáveis opcionais: `ANTHROPIC_MODEL` (trocar o modelo) e `ANTHROPIC_WORKSPACE_ID` (só se a API reclamar que a chave não pertence a um workspace).

## Observações

- Se o terminal (principalmente no Windows) não reconhecer acentos digitados, escreva sem acento: o bot ignora acentos. A janela gráfica não tem esse problema.
- Se aparecer "Address already in use", já existe outro servidor aberto: feche-o com Ctrl+C ou troque `PORTA` nos arquivos.
- Para conectar de outro computador, troque `HOST` no `Cliente.java` e no `ClienteGrafico.java` pelo IP da máquina do servidor.
- Se um segundo cliente conectar enquanto o primeiro está em atendimento, ele aguarda a sua vez.
