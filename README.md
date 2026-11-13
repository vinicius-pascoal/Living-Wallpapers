# Living Wallpapers — Wallpapers animados Android

Projeto Android **Kotlin + Jetpack Compose + WallpaperService** com uma coleção de cenas em pixel art
renderizadas proceduralmente em 2D nativo (sem imagens externas).

| Cena | O que tem |
| --- | --- |
| **Floresta Viva** | Céu com dithering, lua com halo, nuvens passando, estrelas cadentes, pinheiros balançando ao vento, cabana com janela tremulando e fumaça, névoa, vaga-lumes e folhas |
| **Galáxia** | Nebulosa gerada por ruído (fBm com distorção), galáxia espiral girando, estrelas em 3 camadas de profundidade, cometas, planeta com anéis e tempestade |
| **Mundo Fantástico** | Pôr do sol, castelo nas montanhas, ilhas flutuantes derivando e balançando, cachoeiras caindo no mar de nuvens, nuvens em camadas, pássaros e esporos luminosos |

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
15 FPS. Cada cena também corta efeitos extras (brilhos, fumaça, respingos, menos partículas).

## Requisitos

- Android Studio com JDK 21 (o Gradle 9.3 ainda não roda com JDK 25), Android SDK Platform 37.
- Gradle 9.3.1, Android Gradle Plugin 9.1.0 (Kotlin embutido) e plugin Compose 2.3.0.
- Celular Android 8.0 (API 26) ou superior; testagem prioritária no Samsung Galaxy S25 Edge.
- Internet para sincronizar dependências na primeira importação.

> **Gradle Wrapper:** o projeto contém `gradle/wrapper/gradle-wrapper.properties`, mas não o
> `gradle-wrapper.jar` nem os scripts `gradlew`. O Android Studio baixa o Gradle 9.3.1 sozinho;
> para linha de comando, gere o wrapper com `gradle wrapper --gradle-version 9.3.1`.

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
│   ├── scene/PixelPainter.kt                 # Desenho pixel art, ruído, dithering, cache de camadas
│   ├── scene/forest/ForestScene.kt           # Cena "Floresta Viva"
│   ├── scene/galaxy/GalaxyScene.kt           # Cena "Galáxia"
│   ├── scene/fantasy/FantasyScene.kt         # Cena "Mundo Fantástico"
│   ├── settings/WallpaperSettings.kt         # Configuração + regras de FPS
│   ├── settings/SettingsRepository.kt        # Persistência em SharedPreferences
│   ├── ui/ScenePreviewView.kt                # Prévia ao vivo no app
│   ├── wallpaper/SceneWallpaperService.kt    # Serviço base: superfície, quadros e sensores
│   └── wallpaper/SceneServices.kt            # Um serviço por cena (Forest/Galaxy/FantasyWallpaperService)
└── res/
    ├── drawable-nodpi/{forest,galaxy,fantasy}_thumbnail.png
    ├── drawable/app_icon.xml
    ├── values/{styles,strings}.xml
    └── xml/wallpaper_{forest,galaxy,fantasy}.xml   # Descrição e miniatura de cada wallpaper
```

### Como adicionar uma cena nova

1. Crie `scene/<nome>/<Nome>Scene.kt` implementando `SceneRenderer`. Desenhe com um `PixelPainter`
   (`begin(bitmap)` a cada quadro) e use `settings.timeScale()`, `settings.effectiveParticles()`
   e `settings.batterySaver`.
   - Partes estáticas (céu, halos) vão num `LayerCache`, desenhado uma única vez.
   - Objetos que só se movem (ilhas, nuvens) podem ser pré-desenhados em bitmaps no construtor.
   - Nada caro na thread principal: geração pesada (como a nebulosa) vai para uma thread de fundo.
2. Crie o serviço da cena em `wallpaper/SceneServices.kt` (`class XWallpaperService : SceneWallpaperService()`
   com `override val sceneId`), declare-o no `AndroidManifest.xml` e crie `res/xml/wallpaper_x.xml`
   com descrição e miniatura (`res/drawable-nodpi/x_thumbnail.png`, 360×780).
3. Registre a cena em `SceneRegistry.scenes` com `id`, `title`, `subtitle`, `create` e `service`.
   O seletor do app mostra a cena automaticamente.

### Observações técnicas

- A renderização é feita em bitmap pequeno (180 px de largura), ampliado com `filterBitmap=false`.
- Custo medido no emulador (x86_64, 180×400): 1–4 ms por quadro nas três cenas; o primeiro quadro
  da galáxia leva ~45 ms; a nebulosa é gerada numa thread de fundo e compartilhada entre instâncias.
- Cada cena tem seu próprio `WallpaperService`, então o sistema as trata como wallpapers distintos:
  dá para usar uma cena na tela inicial e outra na tela de bloqueio. FPS, velocidade, partículas,
  parallax e economia de bateria são compartilhados por todas.
- As configurações são atualizadas no wallpaper via listener de SharedPreferences.
- A prévia da tela de configurações não usa o acelerômetro; o parallax só ocorre no wallpaper.
- Sem internet, anúncios, rastreamento, permissão de armazenamento ou serviço em primeiro plano.

## Próximos incrementos

- Novas cenas (praia, cidade chuvosa, neve, deserto…).
- Otimização: reutilizar `Canvas`/`Path` por quadro e cache de camadas estáticas.
- Prévia do parallax com simulação de inclinação e suporte a gestos.
- Opção de exportação para vídeo para compatibilidade com telas de bloqueio restritas.
