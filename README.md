# RoutinLife 0.3.0

Aplicativo Android que funciona como **organizador de rotina SEMANAL com alarmes**. Cada dia
da semana (Segunda ... Domingo) tem o **seu próprio conjunto de rotinas**: o usuário monta as
atividades de cada dia (horário + nome) e o app toca um alarme sonoro no horário de cada uma,
inclusive com o **app fechado** e com a **tela bloqueada**. Ao chegar na mesma semana
seguinte, a rotina de cada dia se repete automaticamente.

![Ícone do RoutinLife](docs/icon.svg)

Exemplo de semana suportada pelo app — listas distintas, um exemplo por dia:

| Segunda-feira | Terça-feira |
|---------------|-------------|
| 09:00 Estudos | 10:00 Estudos |
| 12:00 Almoço  | 14:00 Academia |
| 14:00 Academia| 19:00 Trabalho |
| 21:00 Dormir  | 21:00 Dormir |

O que mudou em relação ao 0.2.x (rotina única repetida todo dia): os dados agora são
**agrupados por dia da semana**, cada dia com lista, alarmes e conclusões próprios. Os itens já
salvos são **migrados automaticamente** para os 7 dias na primeira abertura (ver
[Migração dos dados](#migração-dos-dados-da-versão-diária-02x)).

## As duas abas e os chips da semana

As duas telas começam com a **faixa da semana** (Seg Ter Qua Qui Sex Sáb Dom): os chips têm
seleção única e o dia escolhido é **compartilhado entre as abas** — montar em segunda e
conferir em segunda mostram o mesmo dia. O app abre em **hoje**.

1. **Montar Rotina** — dia (chips) + seletor de hora/minuto (24h) + nome da atividade + botão
   *Adicionar e agendar alarme*. O item nasce no **dia selecionado** e **já sai com o alarme
   agendado** para a próxima ocorrência daquele dia da semana.
2. **Minha Rotina** — a rotina do dia selecionado em formato de planilha
   (Horário | Atividade | Feito | Alarme | Ações), com **editar** (horário e nome), **excluir**,
   **ativar/desativar o alarme** sem apagar o item, **marcar como concluída** (✓ riscado, preso
   àquele dia/data) e **reordenar** segurando a linha e arrastando para cima/baixo.

Cada dia é uma lista **independente**: editar, excluir, concluir ou reordenar um item de
Segunda-feira não toca em nada de Terça-feira, e o estado de conclusão de segunda não aparece
em terça nem na segunda da semana seguinte.

Tratamento de **dois itens no mesmo horário**: dentro do mesmo dia, ao tentar salvar (ou editar
para) um horário já ocupado, o app mostra um diálogo informando quais itens já existem naquele
horário e avisa que os alarmes vão tocar juntos; o usuário decide entre *Adicionar mesmo assim*
ou *Cancelar*. A linha duplicada também aparece marcada na planilha (`mesmo horário — os alarmes
tocam juntos`) e, quando os alarmes disparam juntos, o serviço agrupa os dois em um único aviso
com um só som.

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
desconhecidas"). O APK de teste já compilado é entregue junto com o projeto
(`RoutinLife-0.3.0-debug.apk`).

### Gerando a release no GitHub (CI)

O workflow [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml) (GitHub
Actions) compila o `main` com JDK 17 + SDK 34 + Gradle 8.7 e publica o APK como asset da
release **v0.3.0**, com link público de download:

```
https://github.com/muriloq-byte/RoutinLife-0.2.2/releases/download/v0.3.0/RoutinLife-0.3.0-debug.apk
```

Para regerar: **Actions → "Gerar APK" → Run workflow** (o build precisa passar antes do upload).
Localmente o equivalente é `gradle assembleDebug`.

### APK de release assinado

O `assembleRelease` gera um APK **não assinado**; para distribuir (loja ou fora dela), gere uma
chave e assine:

```bash
# 1) chave (uma vez só)
keytool -genkeypair -v -keystore routinlife.jks -alias routinlife \
        -keyalg RSA -keysize 2048 -validity 10000

# 2) build de release
gradle assembleRelease        # -> app/build/outputs/apk/release/app-release-unsigned.apk

# 3) assinar (build-tools do SDK)
$ANDROID_HOME/build-tools/34.0.0/apksigner sign \
        --ks routinlife.jks --ks-key-alias routinlife \
        --out app/build/outputs/apk/release/RoutinLife-0.3.0-release.apk \
        app/build/outputs/apk/release/app-release-unsigned.apk
$ANDROID_HOME/build-tools/34.0.0/apksigner verify \
        app/build/outputs/apk/release/RoutinLife-0.3.0-release.apk
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
  própria). Isso permite cancelar, reagendar e desativar item por item sem afetar os outros —
  inclusive sem afetar os itens de outros dias.
- **Rotina semanal sem `setRepeating`**: no Android 4.4+ alarmes repetitivos deixam de ser
  exatos. O app agenda sempre a **próxima ocorrência exata do DIA da semana do item**
  (`WeekDays.nextTriggerMillis`: o próximo dia igual cujo horário ainda não passou) com
  `setExactAndAllowWhileIdle` e, no momento em que o alarme toca, o `AlarmReceiver` **reagenda
  a semana seguinte antes de tocar**. Se o processo morrer no meio, a rotina já está agendada.
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
- **Modelo por dia** — cada item carrega:
  - `day` (1–7, constantes de `Calendar`): o dia a que pertence; itens de dias diferentes são
    registros independentes;
  - `order`: posição da linha dentro do dia (nasce cronológica, o usuário pode arrastar);
  - `doneDate` (yyyy-MM-dd ou ausente): a **data** em que foi marcado como concluído — guardar a
    data e não um booleano mantém a conclusão presa ao dia certo.
- **`RoutineRepository`** expõe a lista como `LiveData`, então as duas abas se atualizam sozinhas
  depois de adicionar/editar/reordenar/concluir/excluir/ativar, e as telas de alarme usam acesso
  direto (sem UI). Cada tela filtra o dia exibido com `WeekDays.forDay`, o único ponto de
  filtragem — é isso que garante que dois dias nunca compartilhem lista.
- A persistência é **local**: nada é enviado para servidores.

### Migração dos dados da versão diária (0.2.x)

O formato antigo gravava uma lista única sem campo de dia (`{id, hour, minute, title,
enabled}`). Na **primeira leitura** após a atualização, `RoutineStore.load()` detecta itens sem
`day` e chama `RoutineMigration.expandLegacy`, que:

1. **copia cada item antigo para os 7 dias da semana** — coerente com o app antigo, em que a
   rotina se repetia todo dia: o usuário continua com os mesmos alarmes em todos os dias e
   passa a editar cada dia de forma independente;
2. gera **ids únicos** por cópia (`original * 10 + índice`), sem depender de relógio (o formato
   antigo usava `System.currentTimeMillis()`, que empilharia as 7 cópias no mesmo milissegundo);
3. atribui a **ordem cronológica** das linhas em cada dia e deixa tudo não concluído;
4. **regrava o arquivo** com os campos novos (`day`, `order`) — a migração roda **uma única
   vez** e é idempotente (as próximas leituras já veem `day` e não migram de novo).

Itens que já fossem semânticos do formato novo (`day` presente) são preservados sem reordenação.
O que não puder ser lido cai no tratamento de dados corrompidos (lista vazia), como antes.

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
│   ├── RoutineItem.kt            # modelo (id, hora, minuto, título, ativo, dia, ordem, conclusão)
│   ├── WeekDays.kt               # núcleo semanal: dias, data e próximo disparo
│   ├── RoutineMigration.kt       # migração 0.2.x (rotina única) -> 7 dias
│   ├── RoutineStore.kt           # SharedPreferences + JSON
│   └── RoutineRepository.kt      # acesso + LiveData
├── alarm/
│   ├── AlarmScheduler.kt         # agendamento/cancelamento e próxima ocorrência do dia
│   ├── AlarmReceiver.kt          # disparo: reagenda a semana + inicia o serviço
│   ├── BootReceiver.kt           # reagenda no boot / troca de horário / atualização
│   ├── AlarmService.kt           # serviço em primeiro plano com som e vibração em loop
│   ├── AlarmNotifier.kt          # canais e notificação de alarme (tela cheia + PARAR)
│   └── AlarmActivity.kt          # tela cheia do alarme sobre a tela de bloqueio
└── ui/
    ├── MainActivity.kt           # abas + fluxo de permissões explicado
    ├── MontarRotinaFragment.kt   # aba 1
    ├── MinhaRotinaFragment.kt    # aba 2
    ├── RoutineAdapter.kt         # linhas da planilha (conclusão, alarme, arraste)
    ├── DayChips.kt               # chips da semana compartilhados pelas duas abas
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
| versionName / versionCode | 0.3.0 / 4 |

## Limitações conhecidas

- **Gerenciamento agressivo de bateria** (Xiaomi/MIUI, Huawei/EMUI, Samsung, Oppo, Vivo…) pode
  atrasar ou bloquear alarmes mesmo com todas as permissões. Nesses aparelhos, libere o app em
  *Configurações › Bateria › Sem restrições* (e "Iniciar automaticamente", quando existir).
- O alarme usa o som padrão de alarme do sistema; tons personalizados por item não fazem parte
  desta versão.
- O APK entregue é de **debug** (assinado com a chave de debug), adequado para instalar e
  testar; para distribuir, gere um release assinado como descrito acima.
- Nesta versão não há snooze. A rotina é **semanal**: cada dia da semana tem a sua própria
  lista, com alarmes e conclusões independentes.

## Estado de verificação

- `gradle assembleDebug` **compila com sucesso** (APK gerado em
  `app/build/outputs/apk/debug/app-debug.apk`).
- `gradle test` **passa**: 11 testes de JVM em `app/src/test/.../WeeklyLogicTest.kt` cobrem
  isolamento entre dias, ordem das linhas, data de conclusão, próximo disparo de cada dia e a
  migração 0.2.x → 7 dias (incluindo idempotência).
- O workflow do GitHub Actions compila e roda os testes a cada execução, e só publica o APK
  depois que ambos passam.
- O app **não foi executado em um aparelho/emulador** neste ambiente (sem emulador disponível):
  os fluxos de alarme (disparo real, tela de bloqueio, reagendamento no boot) precisam ser
  validados em um celular real.
