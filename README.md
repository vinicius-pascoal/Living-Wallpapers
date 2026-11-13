# Living Wallpapers — Wallpapers animados Android

Projeto Android **Kotlin + Jetpack Compose + WallpaperService** com uma coleção de cenas em pixel art
renderizadas proceduralmente em 2D nativo. Primeira cena: **Floresta Viva** (floresta ao anoitecer com lua,
estrelas, montanhas, árvores em camadas, vaga-lumes, folhas e névoa).

## Parâmetros iniciais

| Preferência | Valor inicial |
| --- | --- |
| Cena | **Floresta Viva** |
| Limite de FPS | **30** |
| Velocidade | **45%** |
| Densidade de partículas | **60%** |
| Parallax | **Ligado** |
| Economia de bateria | **Ligada** |

**Economia de bateria:** o wallpaper não renderiza enquanto invisível; registra sensores apenas
quando visível e quando parallax está ativo. Com a economia do Android ativada, reduz o limite para
15 FPS. Cada cena pode reduzir efeitos extras (na floresta: brilho dos vaga-lumes, folhas e névoa).

## Requisitos

- Android Studio com JDK 17 ou 21, Android SDK Platform 36 e SDK Build Tools instalados.
- Gradle 8.13 e Android Gradle Plugin 8.13.0; Kotlin 2.3.0.
- Celular Android 8.0 (API 26) ou superior; testagem prioritária no Samsung Galaxy S25 Edge.
- Internet para sincronizar dependências na primeira importação.

> **Importante sobre o Gradle Wrapper:** este projeto contém `gradle/wrapper/gradle-wrapper.properties`,
> mas não inclui `gradle-wrapper.jar` nem os scripts `gradlew`. Abra o projeto no Android Studio usando
> uma instalação local do Gradle 8.13 ou gere o wrapper com `gradle wrapper --gradle-version 8.13`.
> Depois disso, `./gradlew assembleDebug` funcionará.

## Executar

1. Abra **a pasta do projeto** no Android Studio (Open).
2. Configure JDK / SDK, sincronize o Gradle e instale `app` no aparelho via USB ou Wi-Fi debugging.
3. Abra **Living Wallpapers** no celular, escolha a cena e ajuste as opções na prévia animada.
4. Pressione **Aplicar wallpaper** e escolha **tela inicial**, **tela inicial + bloqueio**, ou a opção
   disponibilizada pela sua versão do Android/One UI.

A possibilidade de aplicar um live wallpaper de terceiros na tela de bloqueio depende da One UI.
O projeto usa a API oficial; não contorna restrições do fabricante.

## Arquitetura

```
app/src/main/
├── AndroidManifest.xml
├── java/com/livingwallpapers/app/
│   ├── MainActivity.kt                       # Configuração em Compose (cena, FPS, efeitos)
│   ├── scene/SceneRenderer.kt                # Interface comum a todas as cenas + SceneInfo
│   ├── scene/SceneRegistry.kt                # Lista das cenas disponíveis
│   ├── scene/forest/ForestScene.kt           # Cena "Floresta Viva"
│   ├── settings/WallpaperSettings.kt         # Configuração + regras de FPS
│   ├── settings/SettingsRepository.kt        # Persistência em SharedPreferences
│   ├── ui/ScenePreviewView.kt                # Prévia ao vivo no app
│   └── wallpaper/LivingWallpaperService.kt   # Serviço, superfície e sensores
└── res/
    ├── drawable-nodpi/forest_thumbnail.png
    ├── drawable/app_icon.xml
    ├── values/{styles,strings}.xml
    └── xml/living_wallpaper.xml
```

### Como adicionar uma cena nova

1. Crie `scene/<nome>/<Nome>Scene.kt` implementando `SceneRenderer` (desenhe no bitmap recebido,
   use `settings.timeScale()`, `settings.effectiveParticles()` e `settings.batterySaver`).
2. Registre a cena em `SceneRegistry.scenes` com `id`, `title`, `subtitle` e `create`.
3. O seletor de cena aparece automaticamente no app quando há mais de uma cena; o wallpaper
   aplicado troca de cena em tempo real ao mudar a escolha.

### Observações técnicas

- A renderização é feita em bitmap pequeno (180 px de largura), ampliado com `filterBitmap=false`.
- Um único `WallpaperService` desenha a cena escolhida nas configurações.
- As configurações são atualizadas no wallpaper via listener de SharedPreferences.
- A prévia da tela de configurações não usa o acelerômetro; o parallax só ocorre no wallpaper.
- Sem internet, anúncios, rastreamento, permissão de armazenamento ou serviço em primeiro plano.

## Próximos incrementos

- Novas cenas (praia, cidade chuvosa, neve, deserto…).
- Otimização: reutilizar `Canvas`/`Path` por quadro e cache de camadas estáticas.
- Prévia do parallax com simulação de inclinação e suporte a gestos.
- Opção de exportação para vídeo para compatibilidade com telas de bloqueio restritas.
