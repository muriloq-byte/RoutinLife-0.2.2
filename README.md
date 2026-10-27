# RoutinLife 0.2.2

Aplicativo Android que funciona como **organizador de rotina diária com alarmes**. O usuário
monta a rotina (horário + nome da atividade) e o app toca um alarme sonoro em cada horário,
inclusive com o **app fechado** e com a **tela bloqueada**. No dia seguinte a rotina se repete
automaticamente.

![Ícone do RoutinLife](docs/icon.svg)

Exemplo de rotina suportada pelo app:

| Horário | Atividade |
|---------|-----------|
| 09:00   | Estudos   |
| 12:00   | Almoço    |
| 14:00   | Academia  |
| 17:00   | Cochilo   |
| 19:00   | Estudos   |
| 21:00   | Dormir    |

## As duas abas

1. **Montar Rotina** — seletor de hora/minuto (24h), campo de nome da atividade e o botão
   *Adicionar e agendar alarme*. Cada item criado **já sai com o alarme agendado**.
2. **Minha Rotina** — a rotina em formato de planilha (Horário | Atividade | Alarme | Ações),
   em ordem cronológica, com **editar** (horário e nome), **excluir** e **ativar/desativar o
   alarme de cada item sem apagar o item**. Reativar volta a agendar o alarme.

Tratamento de **dois itens no mesmo horário**: ao tentar salvar (ou editar para) um horário já
ocupado, o app mostra um diálogo informando quais itens já existem naquele horário e avisa que
os alarmes vão tocar juntos; o usuário decide entre *Adicionar mesmo assim* ou *Cancelar*. A
linha duplicada também aparece marcada na planilha (`mesmo horário — os alarmes tocam juntos`) e,
quando os alarmes disparam juntos, o serviço agrupa os dois em um único aviso com um só som.

## Como compilar o APK

### Opção A — Android Studio (mais simples)

1. Abra a pasta do projeto no Android Studio (Giraffe/Hedgehog ou mais novo).
2. Aguarde o *Gradle Sync* (o Android Studio cria o `local.properties` com o caminho do SDK).
3. `Build ▸ Build Bundle(s) / APK(s) ▸ Build APK(s)`.
4. O APK fica em `app/build/outputs/apk/debug/app-debug.apk`.

### Opção B — Linha de comando

Requisitos: **JDK 17**, **Android SDK** com `platforms;android-34` e `build-tools;34.0.0` e
**Gradle 8.7** instalado. Se o Gradle Wrapper não estiver na sua cópia (o repositório Git não
versiona o `gradle-wrapper.jar`), gere-o uma vez com `gradle wrapper --gradle-version 8.7` — ou
abra o projeto no Android Studio, que cria o `gradlew` sozinho na primeira sincronização.

```bash
export ANDROID_HOME=/caminho/do/Android/Sdk      # ou crie local.properties com sdk.dir=...
export JAVA_HOME=/caminho/do/jdk-17

gradle wrapper --gradle-version 8.7   # gera o ./gradlew (uma vez)
./gradlew assembleDebug        # APK de teste  -> app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # APK de release -> app/build/outputs/apk/release/app-release-unsigned.apk
```

Instalar no aparelho:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Ou copie o `.apk` para o celular e abra (é preciso permitir "instalar apps de fontes
desconhecidas"). O APK de teste já compilado é entregue junto com o projeto (`RoutinLife-0.2.2-debug.apk`).

### APK de release assinado

```bash
keytool -genkeypair -v -keystore routinlife.jks -alias routinlife \
        -keyalg RSA -keysize 2048 -validity 10000
```

Depois adicione em `app/build.gradle`:

```groovy
android {
    signingConfigs {
        release {
            storeFile file("../routinlife.jks")
            storePassword "SUA_SENHA"
            keyAlias "routinlife"
            keyPassword "SUA_SENHA"
        }
    }
    buildTypes {
        release { signingConfig signingConfigs.release }
    }
}
```

> Não versione o `.jks` nem as senhas. O build de debug usa a chave de debug do Android e serve
> para testar no aparelho sem nenhuma configuração extra.

## Permissões declaradas

| Permissão | Para que serve |
|-----------|----------------|
| `SCHEDULE_EXACT_ALARM` | Agendar o alarme exato. No Android 12+ pode ser concedida/revogada pelo usuário ("Alarmes e lembretes") — o app explica e abre a tela de configuração no primeiro uso. |
| `USE_EXACT_ALARM` | Alternativa concedida automaticamente no Android 13+ para apps cuja função principal é alarme/relógio. |
| `POST_NOTIFICATIONS` | Mostrar o aviso do alarme e o botão PARAR (Android 13+ pede permissão em tempo de execução). |
| `USE_FULL_SCREEN_INTENT` | Abrir a tela do alarme por cima da tela de bloqueio. No Android 14+ o usuário pode revogar; o app verifica e oferece a tela de configuração. |
| `RECEIVE_BOOT_COMPLETED` | Reagendar TODOS os alarmes depois de reiniciar o aparelho. |
| `VIBRATE` | Vibrar enquanto o alarme toca. |
| `WAKE_LOCK` | Manter o som tocando com a tela apagada. |
| `FOREGROUND_SERVICE` | Manter o serviço que toca o alarme em primeiro plano. |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Tipo do serviço em primeiro plano (Android 14+ exige a permissão correspondente ao tipo `mediaPlayback`). |
| `com.routinlife.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Adicionada automaticamente pela biblioteca AndroidX/AppCompat (`androidx.core`) para proteger receptores registrados em tempo de execução. Não é pedida ao usuário. |

O app **não** pede localização, contatos, câmera, internet ou armazenamento: a rotina fica só no
aparelho e o app não acessa a rede.

### Primeiro uso (explicação ao usuário)

Na primeira abertura o app mostra, em ordem, dois diálogos explicando **por que** cada permissão
é necessária: notificações (avisar quando chega a hora e mostrar o botão de parar) e alarmes
exatos (tocar no minuto certo, mesmo com o app fechado). Se o usuário recusar, aparece um cartão
de aviso na aba *Minha Rotina* com o botão *Liberar agora*, que leva direto à configuração certa
(incluindo a permissão de tela cheia no Android 14+).

## Decisões de arquitetura

### Alarmes

- **`AlarmManager` + um `PendingIntent` por item** (`requestCode` derivado do id do item, ação
  própria). Isso permite cancelar, reagendar e desativar item por item sem afetar os outros.
- **Rotina diária sem `setRepeating`**: no Android 4.4+ alarmes repetitivos deixam de ser exatos.
  O app agenda sempre a **próxima ocorrência exata** (`setExactAndAllowWhileIdle`) e, no momento
  em que o alarme toca, o `AlarmReceiver` **reagenda o dia seguinte antes de tocar**. Se o
  processo morrer no meio, a rotina já está agendada para amanhã.
- **Degraus de exatidão**: `setExactAndAllowWhileIdle` (quando há permissão de alarme exato) →
  `setAlarmClock` (exato e não depende da permissão; aparece o ícone de alarme na barra de
  status) → `set()` (último recurso, pode atrasar). Assim o alarme nunca deixa de existir por
  falta de permissão.
- **Reagendamento defensivo**: no `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`,
  `TIMEZONE_CHANGED` (BootReceiver) e também a cada abertura do app (`RoutinLifeApp`), o que
  cobre reinício, troca de horário/fuso e atualização do app.
- **Tocar com o app fechado e tela bloqueada**: `AlarmReceiver` → `AlarmService` (serviço em
  primeiro plano, tipo `mediaPlayback`) que toca o som de alarme do sistema em loop com vibração
  e publica uma notificação de alta prioridade com **full-screen intent** → `AlarmActivity`
  (`showWhenLocked` / `turnScreenOn`), que aparece por cima da tela de bloqueio e tem o botão
  PARAR. Um disparo de alarme exato é uma das exceções documentadas do Android 12+ para iniciar
  serviços em primeiro plano a partir do segundo plano; se ainda assim a promoção falhar, o
  `AlarmNotifier.notifyFallback` publica na hora a mesma notificação (som do canal + tela cheia),
  então o alarme nunca passa em branco. O som para ao tocar em PARAR ou sozinho após 1 minuto.
- **Som escolhido**: o som de alarme padrão do sistema (`RingtoneManager.TYPE_ALARM`), no canal
  *Alarmes da rotina* (importância alta); o usuário pode trocá-lo nas configurações do Android.

### Armazenamento

- **`SharedPreferences` com um JSONArray** (`RoutineStore`) em vez de banco de dados: a rotina é
  uma lista curtíssima de itens simples; JSON evita Room/migrations/dependências de anotação e
  mantém o app leve. A gravação é *write-through* (a lista inteira é reescrita a cada mudança) e
  a leitura é tolerante a dados corrompidos (devolve lista vazia em vez de quebrar o app).
- **`RoutineRepository`** expõe a lista como `LiveData`, então as duas abas se atualizam sozinhas
  depois de adicionar/editar/excluir/ativar, e as telas de alarme usam acesso direto (sem UI).
- A persistência é **local**: nada é enviado para servidores.

### Interface

- `MainActivity` + `BottomNavigationView` com dois `Fragment`s (Montar Rotina / Minha Rotina).
- Material 3, tema claro, tudo em português (`strings.xml`), com *view binding*.
- Planilha: cabeçalho fixo e linhas com as **mesmas larguras proporcionais** por coluna
  (`layout_weight` iguais no cabeçalho e na linha), divisor de 1dp entre linhas e linha esmaecida
  quando o alarme está desativado.

### Estrutura

```
app/src/main/java/com/routinlife/app/
├── RoutinLifeApp.kt              # Application: armazenamento, canais e reagendamento
├── data/
│   ├── RoutineItem.kt            # modelo (id, hora, minuto, título, ativo)
│   ├── RoutineStore.kt           # SharedPreferences + JSON
│   └── RoutineRepository.kt      # acesso + LiveData
├── alarm/
│   ├── AlarmScheduler.kt         # agendamento/cancelamento e próxima ocorrência
│   ├── AlarmReceiver.kt          # disparo: reagenda amanhã + inicia o serviço
│   ├── BootReceiver.kt           # reagenda no boot / troca de horário / atualização
│   ├── AlarmService.kt           # serviço em primeiro plano com som e vibração em loop
│   ├── AlarmNotifier.kt          # canais e notificação de alarme (tela cheia + PARAR)
│   └── AlarmActivity.kt          # tela cheia do alarme sobre a tela de bloqueio
└── ui/
    ├── MainActivity.kt           # abas + fluxo de permissões explicado
    ├── MontarRotinaFragment.kt   # aba 1
    ├── MinhaRotinaFragment.kt    # aba 2
    ├── RoutineAdapter.kt         # linhas da planilha
    └── TimeFormat.kt             # formatação hh:mm
```

### Ícone

O ícone é **vetorial** (VectorDrawable), gerado por script
([`tools/generate_vector_icons.py`](tools/generate_vector_icons.py), sem dependências externas):
fatia de pizza cartunesca em traço preto grosso com preenchimento branco, levantando uma barra
de peso, com rosto sorridente, tênis, linha de chão e estrelinhas de esforço — fundo creme, sem
sombreamento. O script escreve:

- `drawable/ic_launcher_foreground.xml` — camada de frente do ícone adaptativo (Android 8+), com
  a arte dentro da zona segura de 66/108;
- `mipmap-anydpi/ic_launcher.xml` e `ic_launcher_round.xml` — ícone legado em vetor (Android 7),
  quadrado e redondo, com fundo creme;
- `mipmap-anydpi-v26/ic_launcher.xml` e `ic_launcher_round.xml` — declaração do ícone adaptativo;
- `docs/icon.svg` — prévia do desenho.

Por ser vetor, o ícone fica nítido em qualquer densidade de tela e o APK não carrega bitmaps de
ícone. Os XMLs gerados já estão versionados; o script serve para reajustar o desenho:

```bash
python3 tools/generate_vector_icons.py
```

### Versões

| Item | Versão |
|------|--------|
| Gradle | 8.7 |
| Android Gradle Plugin | 8.5.2 |
| Kotlin | 1.9.24 |
| compileSdk / targetSdk | 34 (Android 14) |
| minSdk | 24 (Android 7.0) |
| Material Components | 1.12.0 |
| versionName / versionCode | 0.2.2 / 3 |

## Limitações conhecidas

- **Gerenciamento agressivo de bateria** (Xiaomi/MIUI, Huawei/EMUI, Samsung, Oppo, Vivo…) pode
  atrasar ou bloquear alarmes mesmo com todas as permissões. Nesses aparelhos, libere o app em
  *Configurações › Bateria › Sem restrições* (e "Iniciar automaticamente", quando existir).
- O alarme usa o som padrão de alarme do sistema; tons personalizados por item não fazem parte
  desta versão.
- O APK entregue é de **debug** (assinado com a chave de debug), adequado para instalar e
  testar; para distribuir, gere um release assinado como descrito acima.
- Nesta versão não há snooze, nem rotinas por dia da semana: a rotina é a mesma todos os dias,
  como especificado.

## Estado de verificação

- `./gradlew assembleDebug` **compila com sucesso** (APK gerado em
  `app/build/outputs/apk/debug/app-debug.apk`).
- O app **não foi executado em um aparelho/emulador** neste ambiente (sem emulador disponível):
  os fluxos de alarme (disparo real, tela de bloqueio, reagendamento no boot) precisam ser
  validados em um celular real.
