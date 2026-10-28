# MyVitory

App Android de treino semanal que se reorganiza quando você falta, respeitando o descanso de cada músculo.
Kotlin + Jetpack Compose, offline primeiro (Room). Só os vídeos usam internet.

## Rodar

Abra a pasta no Android Studio, ou:

```bash
./gradlew :app:assembleDebug
```

O APK sai em `app/build/outputs/apk/debug/app-debug.apk`. Testes:

```bash
./gradlew :app:testDebugUnitTest
```

## Vídeos (YouTube)

Os vídeos tocam dentro do app pelo player oficial do YouTube. Sem configurar nada, dá para **fixar** um vídeo
em cada exercício colando o link. A **busca** de vídeos precisa de uma chave da YouTube Data API v3:

1. No Google Cloud Console, crie um projeto, ative a *YouTube Data API v3* e crie uma chave de API.
2. Restrinja a chave a apps Android: pacote `com.myvitory.app` e o SHA-1 do seu certificado
   (`./gradlew :app:signingReport` mostra o de debug).
3. Coloque em `local.properties` (esse arquivo não vai para o git):

   ```properties
   youtube.apiKey=SUA_CHAVE
   ```

A cota gratuita é de 10.000 unidades por dia e cada busca custa 100 (cerca de 100 buscas por dia).
O app guarda cada busca por 7 dias e só busca de novo quando você pede.

## Evolução

- **Progressão de carga:** se você fez todas as séries com a mesma carga e bateu as repetições, o próximo treino
  já vem um degrau acima (+2,5 kg; +2 kg em halteres) e o card avisa.
- **Sua evolução:** na tela de cada exercício, gráfico da maior carga por treino e a lista das últimas sessões.
- **Dias indisponíveis:** em Semana, toque num dia e escolha "Não vou poder treinar neste dia". O recálculo deixa
  esse dia de fora e, se havia treino nele, propõe onde encaixar.
- **Nível iniciante:** em Ajustes, soma 24 h de descanso a cada músculo.

## Notificações

Tudo opcional e gerado no próprio celular (WorkManager), ligado em **Hoje › Ajustes**: lembrete do treino do dia
no horário escolhido e, às 9h, "Você treinou ontem?" com os botões **Treinei** / **Não treinei**. O descanso entre
séries aparece na tela bloqueada com +15 s e Pular, e vibra no fim.

## Publicar

Passo a passo da Play Store, textos da ficha e formulários em [docs/play-store.md](docs/play-store.md).
Política de privacidade em [docs/politica-de-privacidade.md](docs/politica-de-privacidade.md).

## Onde está cada coisa

- `domain/` — regras puras, testadas na JVM: semana, recálculo (`planner/Planner.kt`), recuperação, alternativas.
- `data/` — Room, repositórios e o cliente da YouTube API (`data/video/`).
- `ui/` — telas em Compose, uma pasta por tela.
- `notify/` e `timer/` — lembretes, notificações e o serviço do descanso.
