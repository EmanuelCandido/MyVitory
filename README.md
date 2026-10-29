# MyVitory

**App Android de treino semanal que se reorganiza quando você falta, respeitando o descanso de cada
músculo.**

![Kotlin](https://img.shields.io/badge/Kotlin-Android-7f52ff?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285f4?logo=jetpackcompose&logoColor=white)
![Room](https://img.shields.io/badge/Room-SQLite-3ddc84?logo=android&logoColor=white)
![Offline first](https://img.shields.io/badge/offline-primeiro-555)
![Versão](https://img.shields.io/badge/vers%C3%A3o-0.6.0%20beta-orange)

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
dos exercícios usam internet.

## Funcionalidades

### Semana e recálculo

- **Plano semanal editável** com modelos de treino, exercícios, séries, repetições e carga.
- **Recálculo da semana** quando um treino é perdido, com uma proposta principal e uma alternativa de
  outro tipo (por exemplo, sem usar um dia que era de descanso).
- **Dias indisponíveis:** marque um dia como "Não vou poder treinar neste dia" e o recálculo deixa
  esse dia de fora.
- **Treino fundido:** quando não sobra dia bom, o treino perdido pode ser juntado a outro do mesmo
  músculo principal (até 10 exercícios), com uma série a menos em cada exercício.

### Recuperação e evolução

- **Painel de recuperação** com o percentual de cada músculo e quando ele fica pronto de novo.
- **Nível iniciante:** soma 24 h de descanso a cada músculo.
- **Progressão de carga:** se você fez todas as séries com a mesma carga e bateu as repetições, o
  próximo treino já vem um degrau acima (+2,5 kg; +2 kg em halteres).
- **Gráfico de evolução** da maior carga por treino em cada exercício, com as últimas sessões.
- **Biblioteca de exercícios** e **alternativas** ordenadas por semelhança, para trocar um exercício
  por outro parecido.

### No dia a dia

- **Treino em andamento** com cronômetro de descanso entre séries, que aparece na tela bloqueada com
  os botões +15 s e Pular e vibra no fim.
- **Widget "Treino de hoje"** na tela inicial, com Começar/Continuar, o próximo treino nos dias de
  descanso e o atalho "Reorganizar a semana".
- **Lembretes opcionais** gerados no próprio celular: o treino do dia no horário escolhido e, às 9h,
  "Você treinou ontem?" com os botões Treinei / Não treinei.
- **Vídeos de execução** dentro do app pelo player oficial do YouTube: dá para fixar um vídeo em cada
  exercício colando o link ou, com uma chave de API, buscar vídeos pelo nome.

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
              progressão, alternativas e lembretes
  data/       Room (entidades, DAOs, migrações), repositórios, preferências (DataStore)
              e o cliente da YouTube Data API (video/)
  ui/         telas em Jetpack Compose, uma pasta por tela, cada uma com seu ViewModel
  notify/     lembretes e notificações com WorkManager
  timer/      serviço em primeiro plano do descanso entre séries
  widget/     widget da tela inicial com Glance
```

- **Camadas separadas:** as regras de negócio ficam em `domain/` e não conhecem o Android, o que
  permite testá-las direto na JVM.
- **MVVM com Compose:** cada tela tem um ViewModel que expõe o estado; a navegação usa Navigation
  Compose.
- **Banco versionado:** o schema do Room está na versão 5, com o JSON de cada versão em
  `app/schemas/` e teste de migração.
- **Chaves fora do código:** a chave da YouTube API vem de `local.properties` ou de variável de
  ambiente, e a assinatura de release vem de `keystore.properties`. Nenhum dos dois vai para o git.

## Testes

São 51 testes unitários na JVM, cobrindo a lógica da semana, o planejador, a recuperação, a
progressão de carga, as alternativas, os lembretes, as migrações do banco, os repositórios, o widget,
a renderização do gráfico de evolução e a acessibilidade da interface.

```bash
./gradlew :app:testDebugUnitTest
```

## Tecnologias

Kotlin, Jetpack Compose com Material 3, Navigation Compose, Room, DataStore, WorkManager, Glance
(widget), Coil, player do YouTube para Android, YouTube Data API v3 e JUnit. Requer Android 8.0
(API 26) ou mais recente.

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

Versão 0.6.0, em preparação para o teste fechado na Play Store. O roteiro de publicação está em
[`docs/play-store.md`](docs/play-store.md) e a política de privacidade em
[`docs/politica-de-privacidade.md`](docs/politica-de-privacidade.md).

---

Desenvolvido por **Emanuel Cândido**, estudante de Engenharia de Software na UNIFSA.
