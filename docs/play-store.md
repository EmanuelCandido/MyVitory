# Publicar o beta na Play Store

Roteiro para colocar o MyVitory em **teste interno** e depois em **teste fechado**. Os textos abaixo podem ser
copiados para o Play Console. Itens marcados com [ ] dependem de você.

## 1. Chave de upload (uma vez só)

A Play Store guarda a chave que assina o app para os usuários (Play App Signing). Você só precisa de uma
**chave de upload**. Gere com o `keytool` do JDK e **guarde o arquivo e as senhas fora do projeto**, com backup.
Sem eles você não consegue enviar atualizações.

```bash
keytool -genkeypair -v -keystore myvitory-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

Crie `keystore.properties` na raiz do projeto (já está no `.gitignore`):

```properties
storeFile=../caminho/para/myvitory-upload.jks
storePassword=SENHA_DO_ARQUIVO
keyAlias=upload
keyPassword=SENHA_DA_CHAVE
```

## 2. Gerar o pacote

```bash
./gradlew :app:bundleRelease
```

Sai em `app/build/outputs/bundle/release/app-release.aab`. Antes de cada novo envio, aumente `versionCode`
em `app/build.gradle.kts` (hoje: 5, versão 0.5.0).

Se a busca de vídeos deve funcionar no beta, gere o pacote com `youtube.apiKey` em `local.properties` e, no Google Cloud,
adicione à restrição da chave o **SHA-1 da chave de assinatura do app** (Play Console › Configuração › Integridade do app),
além do SHA-1 de debug.

## 3. Criar o app no Play Console

- [ ] Conta de desenvolvedor Google Play (taxa única).
- [ ] Criar app: nome **MyVitory**, idioma padrão **Português (Brasil)**, app gratuito.
- [ ] Publicar a política de privacidade (`docs/politica-de-privacidade.md`) numa URL pública
      (GitHub Pages, Google Sites) e preencher o e-mail de contato nela.

### Ficha da loja

**Nome (até 30):** MyVitory: treino semanal

**Descrição curta (até 80):** Seu treino da semana se reorganiza quando você falta, respeitando o descanso.

**Descrição completa:**

> Faltou um treino? O MyVitory reorganiza o resto da semana para você não treinar um músculo cansado nem deixar
> outro sem estímulo.
>
> • Hoje: abra o app e veja na hora o que treinar. Um toque para começar.
> • Treino em andamento: marque cada série com um toque, com a carga da última vez já preenchida.
>   O descanso conta sozinho, aparece na tela bloqueada e vibra no fim.
> • Nova semana: quando você falta, o app propõe onde encaixar o treino perdido e explica cada mudança.
>   Você aplica, escolhe a alternativa ou pula.
> • Recuperação: veja quais músculos ainda estão descansando e quando ficam prontos.
> • Como fazer: vídeos do YouTube tocam dentro do app, com os pontos-chave de cada exercício.
> • Aparelho ocupado? Troque por um exercício do mesmo músculo, só hoje ou no plano todo.
> • Lembretes opcionais do treino do dia.
>
> Funciona sem internet (menos os vídeos). Sem cadastro, sem anúncios: seus dados ficam no seu celular.

**Categoria:** Saúde e fitness · **Tags:** treino, academia

**Imagens:**
- [x] Ícone 512×512: `docs/play/icone-512.png`
- [x] Imagem de destaque 1024×500: `docs/play/destaque-1024x500.png`
- [ ] Capturas de tela do celular (mínimo 2; sugestão: Hoje, Treino em andamento, Nova semana, Recuperação,
      Exercício com vídeo). Tire no aparelho com dados de exemplo.

## 4. Formulários do Console

**Segurança dos dados** (revise com o que o app faz na sua versão):
- Dados de treino: **não coletados** (ficam só no aparelho; o backup do Android é do sistema, não do app).
- Termos de busca de vídeo: enviados à YouTube Data API para buscar vídeos. Declare em
  *Atividade no app › Histórico de pesquisa no app* como **coletado**, finalidade **funcionalidade do app**,
  **não compartilhado**, **não vinculado à identidade**, e marque que a coleta é **opcional**
  (só quando a pessoa busca).
- Dados criptografados em trânsito: **sim** (HTTPS). Exclusão de dados: desinstalar ou limpar dados do app.

**Permissões e serviços:**
- `POST_NOTIFICATIONS`: lembretes opcionais e aviso de fim do descanso. Só é pedida quando a pessoa liga um lembrete.
- Serviço em primeiro plano do tipo `shortService`: contagem do descanso entre séries (até ~3 min).
  Confira no Console se a seção de serviços em primeiro plano pede declaração para esse tipo.

**Outros:** classificação de conteúdo (questionário; sem conteúdo sensível), público-alvo (sugestão: 18+, evita
as exigências de apps para crianças, já que há vídeos do YouTube), anúncios: **não**, app de saúde: declarar que é
de condicionamento físico e não oferece diagnóstico.

## 5. Testes

1. **Teste interno** (até 100 pessoas por e-mail, disponível em minutos): envie o `.aab`, adicione a lista de
   testadores e compartilhe o link de participação.
2. **Teste fechado:** contas pessoais novas precisam de um teste fechado com um número mínimo de testadores por
   um período antes de liberar a produção (hoje, 12 pessoas por 14 dias; confirme a regra atual no Console).
3. Produção, depois do teste fechado.

## 6. Checklist antes de cada envio

- [ ] `./gradlew :app:testDebugUnitTest` passando
- [ ] `versionCode` maior que o do último envio
- [ ] Testado em um aparelho real: começar treino, descanso com a tela bloqueada, notificação "Treinei",
      recálculo, vídeo tocando, TalkBack ligado, fonte do sistema no máximo
