# Painel do treinador

Página web onde o treinador monta e ajusta o treino da atleta: exercícios, ordem, séries, repetições, descanso,
observações e os dias da semana. As mudanças chegam sozinhas no app, e o painel mostra o que ela fez (treinos,
cargas, comentários e, se ela quiser, o peso).

```
 Navegador (treinador)            Firebase (plano gratuito)           Celular (atleta)
 web/ (React + Vite)    ⇄   Auth + Firestore + Hosting   ⇄   app: data/sync/ → Room
```

- O **Room continua sendo a fonte da verdade** no celular. O app funciona igual sem internet e sem o painel.
- O **plano** vai e volta: os dois lados podem mudar. Em cada treino, exercício próprio e dia da semana, **vale a
  mudança mais recente** (`PlanMerge.kt` no app e `web/src/sync/merge.ts` no painel, com a mesma regra e os mesmos
  testes). Apagar deixa um registro por 90 dias para o outro lado não trazer de volta.
- O **histórico** só sobe do celular (um documento por dia, últimos 120 dias e a semana atual). O treinador só lê.
- O **peso** só sobe se a atleta ligar "Compartilhar meu peso".

## Como a atleta conecta

1. No app: **Ajustes › Painel do treinador**, digita o nome e toca em **Conectar**. O app cria uma conta anônima
   (sem e-mail nem senha), sobe o plano e mostra um **código de 8 letras**, válido por 30 minutos.
2. O treinador abre o painel, entra com a conta Google e digita o código.
3. Pronto. O celular sincroniza a cada 15 minutos, logo depois de qualquer mudança no app e, com o app aberto, na
   hora em que o treinador salva. Quando uma mudança do treinador chega, aparece a notificação "Seu treino foi
   atualizado".

A atleta vê e remove treinadores na mesma tela. **Desconectar e apagar da nuvem** apaga tudo do Firebase (o celular
fica como está). Reinstalar o app cria outra conta anônima: é só conectar e parear de novo; o plano sobe do celular.

## Onde cada coisa fica no Firestore

| Caminho | Quem escreve | Quem lê | O quê |
| --- | --- | --- | --- |
| `athletes/{uid}` | atleta (treinador só se põe/tira com código) | atleta e treinadores | nome, treinadores, último sync |
| `athletes/{uid}/plan/current` | atleta e treinadores | atleta e treinadores | o plano (formato abaixo) |
| `athletes/{uid}/days/{aaaa-mm-dd}` | atleta | atleta e treinadores | um dia de treino |
| `athletes/{uid}/private/weights` | atleta | atleta e treinadores | pesagens (só se compartilhar) |
| `invites/{código}` | atleta cria | quem tem o código (sem listar) | código de pareamento |
| `users/{uid}` | o próprio treinador | o próprio treinador | atletas que ele acompanha |

As regras estão em [`firebase/firestore.rules`](../firebase/firestore.rules) e têm testes contra o emulador em
[`web/tests/rules.test.ts`](../web/tests/rules.test.ts).

### Formato do plano (`schema: 2`)

Exemplo completo em [`docs/sync/plano-exemplo.json`](sync/plano-exemplo.json), lido pelos testes do app e do painel.
O formato 2 (app 0.12) trouxe a academia fechada e as datas; as regras só aceitam o 2, então um app antigo para de
sincronizar (sem apagar as datas) até atualizar.

```jsonc
{
  "schema": 2,
  "updatedBy": "web",                 // ou "app": quem gravou por último
  "templates": {                      // remoteId (32 hex) → treino
    "f00d…": { "code": "A", "name": "Peito + Tríceps", "updatedAt": 1730000000000,
               "items": [{ "exerciseId": "supino_reto", "sets": 4, "reps": 8, "restSec": 120, "note": "" }] }
  },
  "week": { "1": { "templateId": "f00d…", "updatedAt": 1730000000001, "closed": false } },   // 1 = segunda … 7 = domingo
  "exercises": { "custom_…": { "name": "…", "equipment": "Polia", "primary": ["GLUTES"], "secondary": [], "updatedAt": 5 } },
  "deleted": { "template:…": 1730000000002 },
  "dates": {                          // aba Calendário
    "2026-11-02": { "closed": true, "label": "Finados", "override": false, "templateId": null, "updatedAt": 10 },
    "2026-11-05": { "closed": false, "label": "", "override": true, "templateId": "f00d…", "updatedAt": 11 }
  }
}
```

- `week[d].closed`: a academia não abre nesse dia da semana. O recálculo nunca põe treino nele, e treino que cair
  ali (pelo plano base) vai direto para o recálculo.
- `dates[d].closed`: feriado ou dia fechado só naquela data, com nome opcional. Mesmo efeito.
- `dates[d].override`: a data segue `templateId` (`null` = descanso) em vez do plano base, como uma mudança "só nesta
  semana" feita no celular. Desfazer grava `override: false` com hora nova, e o celular volta o dia ao plano base.
- Datas que passaram há mais de 60 dias saem do plano nos dois lados.

Limites (o app ajusta o que vier fora): séries 1–10, repetições 1–50, descanso 0–600 s, observação até 300
caracteres. Os exercícios do catálogo vêm de `web/src/data/catalog.json`, gerado a partir do app (abaixo).

## Configurar o Firebase (uma vez)

1. Em <https://console.firebase.google.com>, crie um projeto (o plano gratuito **Spark** basta; não precisa de
   cartão).
2. **Authentication › Método de login**: ative **Anônimo** (a atleta) e **Google** (o treinador).
3. **Firestore Database › Criar banco**, no modo de produção, região `southamerica-east1` (São Paulo).
4. **Configurações do projeto › Seus apps**:
   - **Android**, pacote `com.myvitory.app`. Não precisa baixar o `google-services.json`: copie os valores para o
     `local.properties` do projeto (fora do git):

     ```properties
     firebase.projectId=SEU_PROJETO
     firebase.appId=1:000000000000:android:abc123
     firebase.apiKey=AIza...
     ```

     Sem essas linhas o app é gerado sem o painel (a opção nem aparece nos Ajustes).
   - **Web** (apelido "painel"): copie a configuração para `web/.env.local` (modelo em `web/.env.example`).
5. Publique as regras e o painel (precisa do Node 20.19+):

   ```bash
   cd web
   npm install
   npx firebase login
   npx firebase use --add --config ../firebase.json   # escolha o projeto (grava o .firebaserc na raiz)
   npm run deploy                  # build + regras do Firestore + Hosting
   ```

   O painel fica em `https://SEU_PROJETO.web.app` (o app mostra esse endereço na tela de pareamento).
6. Gere o app de novo (`./gradlew :app:assembleRelease`) e instale no celular da atleta.
7. Para conferir o projeto sem deixar rastro (login anônimo, regras e limpeza): `node scripts/verificar-producao.mjs`.

> O nome do projeto, o endereço do painel e as chaves ficam só na sua máquina (`.firebaserc`, `local.properties` e
> `web/.env.local`, todos fora do git).

## Desenvolver e testar sem projeto de verdade

```bash
cd web
npm install
npm test                        # mesclagem, formato e edições do plano
npm run test:regras             # regras do Firestore no emulador
npm run emuladores              # Auth + Firestore locais (UI em http://localhost:4000)
```

Em outro terminal:

```bash
cd web
node scripts/simular-celular.mjs   # faz o papel do celular: ficha, plano, 4 semanas de histórico e um código
npm run dev:emulador               # painel em http://localhost:5173, com "Entrar como treinador de teste"
```

O app também fala com o emulador no build debug: `firebase.emulatorHost=10.0.2.2` no `local.properties` (emulador do
Android) ou o IP do PC na rede (celular de verdade, com os emuladores ouvindo em `0.0.0.0`).

### Catálogo do painel

`web/src/data/catalog.json` sai do app (nomes, músculos, GIF, vídeo, pontos-chave e a divisão de exemplo). O teste
`CatalogExportTest` falha quando o catálogo do app muda; para gerar de novo:

```bash
UPDATE_CATALOG=1 ./gradlew :app:testDebugUnitTest --tests '*CatalogExportTest*'
```

## Custos

Duas pessoas ficam muito abaixo do plano gratuito (50 mil leituras e 20 mil escritas por dia no Firestore). Cada
sync do celular faz algumas leituras e só escreve o que mudou.

## Atualizar o app sem cabo

O app procura versão nova ao abrir e uma vez por dia, em `https://SEU_PROJETO.web.app/app/version.json`. Quando
acha, mostra "Atualizar" na tela Hoje (e uma notificação); ele baixa, confere o SHA-256 e instala. Na primeira vez
o Android pede para permitir que o MyVitory instale apps.

Para publicar uma versão nova:

1. Suba `versionCode` e `versionName` em `app/build.gradle.kts`.
2. Rode, de dentro de `web/`:

   ```bash
   npm run publicar-app -- "O que mudou (aparece no aviso do app)"
   ```

   O script gera o APK release, cria a Release `vX.Y.Z` no GitHub com o `myvitory.apk` (o Hosting gratuito do
   Firebase não aceita `.apk`) e publica o `version.json` no Hosting. Precisa do GitHub CLI logado (`gh auth login`).

Primeira instalação pelo navegador do celular (sem cabo):
<https://github.com/EmanuelCandido/MyVitory/releases/latest/download/myvitory.apk>

**Guarde `keystore/myvitory-release.keystore` e `keystore.properties` (fora do git).** É a mesma chave que assinou
as versões já instaladas; sem ela o Android não instala por cima e seria preciso desinstalar (perdendo os dados).
