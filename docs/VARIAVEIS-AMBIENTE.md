# Variáveis de ambiente — deploy em servidor próprio

> ⚠️ **Todos os valores deste documento são FICTÍCIOS.** Servem só para mostrar o formato esperado.
> Nunca use nenhum deles em produção e nunca versione o `.env` real (o `.gitignore` já ignora `.env`).

A imagem Docker sobe com `--spring.profiles.active=prod`, que lê `src/main/resources/application-prod.properties`.
Nesse perfil, toda variável marcada como **obrigatória** sem valor faz a API falhar na subida.

## `.env` de exemplo (valores fictícios)

```bash
# ===== Banco de dados (MySQL 8) =====
MYSQLHOST=10.0.0.15
MYSQLPORT=3306
MYSQLDATABASE=saudeconecta_prod
MYSQLUSER=saudeconecta_app
MYSQLPASSWORD=Ex3mpl0-Senha-Banco-NaoUsar

# ===== Segurança (JWT) =====
JWT_SECRET=ZmljdGljaW8tbmFvLXVzZS1lbS1wcm9kdWNhby1nZXJlLWNvbS1vcGVuc3NsLXJhbmQtYmFzZTY0LTY0LWV4ZW1wbG8tZmljdGljaW8tc2F1ZGVjb25lY3Rh

# ===== ROOT (super admin) =====
ROOT_LOGIN=11144477735
ROOT_SENHA_HASH=$2a$10$/ErwhWvxCdlm.6JEhXVdp.RHXQKAnAgfhXwg9UWYTRZF8kMfZ/o1u
ROOT_EMAIL=root@example.com

# ===== E-mail (Gmail com senha de app) =====
MAIL_USERNAME=contato.exemplo@gmail.com
MAIL_PASSWORD=abcd efgh ijkl mnop

# ===== Mercado Pago (assinaturas) =====
MP_ACCESS_TOKEN=APP_USR-0000000000000000-000000-ficticio0000000000000000000000-000000000
MP_PUBLIC_KEY=APP_USR-00000000-0000-0000-0000-ficticio0000
MP_WEBHOOK_SECRET=ficticio0000000000000000000000000000000000000000000000000000000
MP_BACK_URL=https://app.exemplo.com.br

# ===== Opcionais =====
PORT=8080
CORS_ALLOWED_ORIGINS=https://app.exemplo.com.br,https://www.exemplo.com.br
HTTP_CONNECT_TIMEOUT=5s
HTTP_READ_TIMEOUT=15s
```

## O que é cada variável

### Banco de dados — obrigatórias

| Variável | Para que serve | Como obter o valor real |
|---|---|---|
| `MYSQLHOST` | Endereço do MySQL | IP ou host do servidor do banco |
| `MYSQLPORT` | Porta do MySQL | Normalmente `3306` |
| `MYSQLDATABASE` | Nome do banco | O Liquibase cria as tabelas na primeira subida; o banco (schema) precisa existir |
| `MYSQLUSER` | Usuário do banco | Crie um usuário só para a API, sem usar o `root` |
| `MYSQLPASSWORD` | Senha do banco | Senha forte |

### Segurança — obrigatória

| Variável | Para que serve | Como obter o valor real |
|---|---|---|
| `JWT_SECRET` | Chave que assina o token de login | `openssl rand -base64 64`. Se trocar, todos os usuários logados precisam entrar de novo |

### ROOT (super admin) — obrigatórias no perfil prod

Usadas pelos changesets do Liquibase 031 e 046 **só quando o banco é criado do zero**. Num banco que já
tem o ROOT elas não alteram nada, mas precisam estar definidas para a API subir.

| Variável | Para que serve | Como obter o valor real |
|---|---|---|
| `ROOT_LOGIN` | Login do ROOT | O login desejado (ex.: um CPF) |
| `ROOT_SENHA_HASH` | Hash BCrypt da **senha master** (nunca a senha em texto) | `python3 -c "import bcrypt;print(bcrypt.hashpw(b'SUA_SENHA', bcrypt.gensalt(10, b'2a')).decode())"` |
| `ROOT_EMAIL` | E-mail do ROOT | Gravado na coluna `usuarios.email` do ROOT |

> O hash tem `$`. No arquivo `.env` (usado com `docker run --env-file`) escreva **sem aspas**.
> Se passar por `-e` no terminal, use aspas simples para o shell não interpretar os `$`.

### E-mail — obrigatórias

| Variável | Para que serve | Como obter o valor real |
|---|---|---|
| `MAIL_USERNAME` | Conta Gmail que envia os e-mails | O endereço da conta |
| `MAIL_PASSWORD` | Senha de app do Gmail (16 letras) | Conta Google → Segurança → Senhas de app (exige verificação em 2 etapas) |

### Mercado Pago — obrigatórias

| Variável | Para que serve | Como obter o valor real |
|---|---|---|
| `MP_ACCESS_TOKEN` | Token privado da API do MP | Painel do desenvolvedor → sua aplicação → Credenciais de produção |
| `MP_PUBLIC_KEY` | Chave pública do MP | Mesmo lugar |
| `MP_WEBHOOK_SECRET` | Valida a assinatura dos webhooks recebidos | Painel → Webhooks, salvo no **Modo de produção** com "Planos e assinaturas" marcado |
| `MP_BACK_URL` | Para onde o MP devolve o cliente depois do pagamento | URL pública do front, com HTTPS (o MP recusa `localhost`) |

Detalhes da integração: [MERCADO-PAGO.md](MERCADO-PAGO.md).

### Opcionais

| Variável | Padrão | Observação |
|---|---|---|
| `PORT` | `8080` | Porta da API dentro do container. Use `PORT`, não `SERVER_PORT`: o Dockerfile passa `-Dserver.port=${PORT}`, que tem prioridade |
| `CORS_ALLOWED_ORIGINS` | `https://saude-conecta-frontend.vercel.app` | URLs do front, separadas por vírgula. Defina se o front não estiver na Vercel |
| `HTTP_CONNECT_TIMEOUT` | `5s` | Tempo máximo para conectar no Mercado Pago |
| `HTTP_READ_TIMEOUT` | `15s` | Tempo máximo de resposta do Mercado Pago |

## Subindo

```bash
docker build -t saudeconecta-api .
docker run -d --name saudeconecta-api --env-file .env -p 8080:8080 --restart unless-stopped saudeconecta-api
```

Requisitos do servidor:

- **HTTPS na frente da API** (Nginx ou Caddy). O webhook do Mercado Pago exige HTTPS, e a API lê o
  IP real do cliente pelo cabeçalho `X-Forwarded-For` que o proxy envia.
- **Fuso horário:** o container já roda em `America/Sao_Paulo`; não é preciso configurar.
