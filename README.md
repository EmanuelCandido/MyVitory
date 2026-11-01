# MyVitory

**App Android de treino semanal que se reorganiza quando você falta, respeitando o descanso de cada
músculo.**

![Kotlin](https://img.shields.io/badge/Kotlin-Android-7f52ff?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285f4?logo=jetpackcompose&logoColor=white)
![Room](https://img.shields.io/badge/Room-SQLite-3ddc84?logo=android&logoColor=white)
![Offline first](https://img.shields.io/badge/offline-primeiro-555)
![Versão](https://img.shields.io/badge/vers%C3%A3o-0.10.0%20beta-orange)

---

## O problema

Quem treina com uma divisão semanal (peito na segunda, costas na terça e assim por diante) passa
sempre pela mesma situação: falta um dia e a semana inteira desanda. Os apps de treino comuns só
marcam o treino como perdido. Encaixar o treino em outro dia exige pensar no descanso: colocar
pernas no dia seguinte a outro treino de pernas atrapalha a recuperação, e empurrar tudo para frente
acaba com a semana.

## A solução

O MyVitory guarda o plano semanal e, quando você falta, **recalcula a semana**. Ele propõe onde
encaixar o treino perdido levando em conta quanto tempo cada músculo ainda precisa de descanso, quais
dias estão livres e o que você já fez. A proposta vem com o motivo de cada escolha, e você aceita ou
escolhe a alternativa.

Tudo funciona offline. Os dados ficam no próprio celular, num banco Room. Só os vídeos de execução
dos exercícios e o painel do treinador (opcional) usam internet.

## Funcionalidades

### Semana e recálculo

- **Plano semanal editável** com modelos de treino, exercícios (na ordem que você quiser), séries, repetições,
  descanso e uma observação por exercício, que aparece na hora do treino.
- **Recálculo da semana** quando um treino é perdido, com uma proposta principal e uma alternativa de
  outro tipo (por exemplo, sem usar um dia que era de descanso).
- **Dias indisponíveis:** marque um dia como "Não vou poder treinar neste dia" e o recálculo deixa
  esse dia de fora.
- **Treino fundido:** quando não sobra dia bom, o treino perdido pode ser juntado a outro do mesmo
  músculo principal (até 10 exercícios), com uma série a menos em cada exercício.

### Painel do treinador (web)

- **O treinador edita pelo navegador:** exercícios, ordem (arrastando), séries, repetições, descanso, observação de
  cada exercício e o treino de cada dia da semana. As mudanças chegam sozinhas no app, com a notificação "Seu treino
  foi atualizado".
- **Acompanha o que foi feito:** frequência das últimas semanas, cada dia com séries, cargas e o comentário da atleta
  ("senti o joelho"), gráfico de evolução da carga e, se ela quiser, o peso.
- **Avisos de recuperação:** o painel aponta quando um músculo volta antes da janela de descanso.
- **Opcional e sob controle da atleta:** ela conecta em Ajustes com uma conta anônima, pareia com um código de 8
  letras e pode desconectar apagando tudo da nuvem. Firebase no plano gratuito, sem servidor próprio.
- Os dois lados podem mudar o plano: em cada treino e dia vale a mudança mais recente. Detalhes e passo a passo em
  [`docs/painel-web.md`](docs/painel-web.md).

### Recuperação e evolução

- **Painel de recuperação** com o percentual de cada músculo e quando ele fica pronto de novo.
- **Nível iniciante:** soma 24 h de descanso a cada músculo.
- **Progressão de carga:** se você fez todas as séries com a mesma carga e bateu as repetições, o
  próximo treino já vem um degrau acima (+2,5 kg; +2 kg em halteres).
- **Gráfico de evolução** da maior carga por treino em cada exercício, com as últimas sessões.
- **Biblioteca com 82 exercícios** (incluindo cadeira abdutora e adutora) e **alternativas** ordenadas
  por semelhança, para trocar um exercício por outro parecido.
- **Exercícios próprios:** crie, edite e apague os seus, com equipamento e músculos que contam para a
  recuperação e o recálculo.

### Peso

- **Acompanhamento de peso:** registre o peso de hoje ou de qualquer data passada (uma pesagem por
  dia; registrar de novo no mesmo dia corrige). Toque numa pesagem do histórico para editar ou apagar.
- **Uma pesagem por mês:** a tela Hoje avisa quando passa um mês sem pesar e, se você ligar em Ajustes,
  chega uma notificação às 8h no 30º dia (e de semana em semana até registrar).
- **Previsão do próximo mês:** o ritmo vem da reta dos mínimos quadrados das pesagens dos últimos 90
  dias (precisa de duas, com pelo menos uma semana entre elas) e a previsão parte da última pesagem.
- **Metas:** peso alvo com prazo opcional, barra de progresso, quanto falta, quando você chega lá no
  ritmo atual e quantos kg por semana o prazo pede.

### No dia a dia

- **Treino em andamento** com cronômetro de descanso entre séries, que aparece na tela bloqueada com
  os botões +15 s e Pular e vibra no fim.
- **Widget "Treino de hoje"** na tela inicial, com Começar/Continuar, o próximo treino nos dias de
  descanso e o atalho "Reorganizar a semana".
- **Lembretes opcionais** gerados no próprio celular: o treino do dia no horário escolhido e, às 9h,
  "Você treinou ontem?" com os botões Treinei / Não treinei.
- **Vídeos de execução prontos:** cada exercício do catálogo já vem com um vídeo em português escolhido
  no YouTube (toca sem chave de API) e um GIF do movimento do [ExerciseDB](https://exercisedb.dev/)
  (API gratuita, sem chave). Dá para fixar outro vídeo colando o link ou, com uma chave da YouTube API,
  buscar vídeos pelo nome.

## Como o recálculo funciona

O motor fica em `domain/planner/Planner.kt` e é Kotlin puro, sem dependência do Android.

Uma semana tem no máximo 7 dias e 7 treinos, então o planejador **testa todas as distribuições
possíveis**: cada treino pendente vai para um dia livre diferente ou fica fora da semana. Ele também
testa as variações em que um treino perdido é fundido a outro parecido. Cada distribuição recebe um
custo, e a de menor custo vira a proposta.

O custo soma penalidades com pesos explícitos:

| Penalidade            | O que mede                                                              |
| --------------------- | ----------------------------------------------------------------------- |
| Falta de descanso     | Horas que faltaram para o músculo se recuperar desde o treino anterior  |
| Mudança de dia        | Quantos dias o treino foi deslocado da data original                    |
| Dia de descanso usado | Ocupar um dia que no plano original era de descanso                     |
| Sequência longa       | Muitos dias seguidos de treino                                          |
| Treino descartado     | Deixar um treino fora da semana, mais caro quanto mais carga ele tinha  |
| Fusão                 | Juntar dois treinos num dia só                                          |

Falta de descanso é custo alto, mas não regra rígida. Muitas divisões reais já repetem músculos com
folga curta, e o plano original do usuário não pode virar "inválido". Depois de escolher, o
planejador explica por que cada treino ficou naquele dia (o músculo principal e o treino mais recente
dele) e calcula a menor folga que o primeiro treino da semana seguinte vai ter.

A recuperação de cada músculo é calculada como as horas desde o último treino divididas pela janela
de descanso do músculo, multiplicada pela carga que ele recebeu naquele treino.

## Arquitetura

```
app/src/main/kotlin/com/myvitory/app/
  domain/     regras puras, testadas na JVM: semana, recálculo (planner/), recuperação,
              progressão, alternativas, lembretes e a mesclagem do plano com o painel (sync/)
  data/       Room (entidades, DAOs, migrações), repositórios, preferências (DataStore),
              o cliente da YouTube Data API (video/) e o sync com o painel (sync/, Firebase)
  ui/         telas em Jetpack Compose, uma pasta por tela, cada uma com seu ViewModel
  notify/     lembretes e notificações com WorkManager
  timer/      serviço em primeiro plano do descanso entre séries
  widget/     widget da tela inicial com Glance
web/          painel do treinador: React + TypeScript + Vite, Firebase Auth e Firestore
firebase/     regras de segurança do Firestore (testadas no emulador)
```

- **Camadas separadas:** as regras de negócio ficam em `domain/` e não conhecem o Android, o que
  permite testá-las direto na JVM.
- **MVVM com Compose:** cada tela tem um ViewModel que expõe o estado; a navegação usa Navigation
  Compose.
- **Banco versionado:** o schema do Room está na versão 9, com o JSON de cada versão em
  `app/schemas/` e teste de migração.
- **Um formato, dois lados:** o plano trocado com o painel tem a mesma regra de mesclagem em Kotlin e TypeScript,
  com os mesmos casos de teste e um exemplo de documento lido pelos dois.
- **Chaves fora do código:** a chave da YouTube API e a configuração do Firebase vêm de `local.properties`
  (ou variável de ambiente), e a assinatura de release vem de `keystore.properties`. Nenhum dos dois vai para o git.

## Testes

São 105 testes unitários na JVM, cobrindo a lógica da semana, o planejador, a recuperação, a
progressão de carga, a previsão e as metas de peso, as alternativas, os lembretes, as migrações do banco, os repositórios, o widget,
a renderização do gráfico de evolução, a acessibilidade da interface, o editor de treino e o sync com o painel (mesclagem,
formato do Firestore e a ida e volta entre o Room e uma nuvem em memória).

```bash
./gradlew :app:testDebugUnitTest
```

O painel tem testes da mesclagem, do formato e das edições do plano, e das regras do Firestore no emulador:

```bash
cd web && npm test && npm run test:regras
```

## Tecnologias

Kotlin, Jetpack Compose com Material 3, Navigation Compose, Room, DataStore, WorkManager, Glance
(widget), Coil, player do YouTube para Android, YouTube Data API v3, Firebase (Auth anônima e Firestore) e JUnit.
Requer Android 8.0 (API 26) ou mais recente. Painel: React 19, TypeScript, Vite, dnd-kit e Vitest.

## Rodar o projeto

Abra a pasta no Android Studio, ou:

```bash
./gradlew :app:assembleDebug
```

O APK sai em `app/build/outputs/apk/debug/app-debug.apk`.

### Busca de vídeos (opcional)

Sem configurar nada, dá para fixar um vídeo em cada exercício colando o link. A busca precisa de uma
chave da YouTube Data API v3:

1. No Google Cloud Console, crie um projeto, ative a _YouTube Data API v3_ e crie uma chave de API.
2. Restrinja a chave a apps Android: pacote `com.myvitory.app` e o SHA-1 do seu certificado
   (`./gradlew :app:signingReport` mostra o de debug).
3. Coloque em `local.properties`:

   ```properties
   youtube.apiKey=SUA_CHAVE
   ```

A cota gratuita é de 10.000 unidades por dia e cada busca custa 100. O app guarda cada busca por
7 dias e só busca de novo quando você pede.

## Status

Versão 0.10.0, em preparação para o teste fechado na Play Store. O roteiro de publicação está em
[`docs/play-store.md`](docs/play-store.md) e a política de privacidade em
[`docs/politica-de-privacidade.md`](docs/politica-de-privacidade.md).

---

Desenvolvido por **Emanuel Cândido**, estudante de Engenharia de Software na UNIFSA.
