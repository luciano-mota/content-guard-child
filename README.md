# ContentGuard Android — MVP 0.1

Aplicativo **experimental** de controle parental local. Regras genéricas por **palavra/frase**, **domínio** e **pacote de aplicativo**. Escrito em **Java 17** com APIs nativas do Android, sem backend e **sem permissão de internet**.

## Versão Google TV / Android TV (experimental)

O ContentGuard Child agora possui duas variantes de APK geradas pelo GitHub Actions:
**Android (celular)** e **Google TV**. A versão TV mantém regras e PIN locais,
exibe uma interface para controle remoto e usa um serviço de acessibilidade
limitado ao pacote do YouTube para TV. O bloqueio ainda depende dos textos que
o YouTube disponibiliza à API de Acessibilidade e **não é garantido**.

Consulte [instruções e matriz de testes](docs/GOOGLE_TV_MVP.md).
A versão para TV não tem navegador nem sincronização com o celular.

## Status

- ✅ Cadastro em lote de palavras e expressões, separadas por vírgulas (ou linhas), com remoção de entradas vazias/duplicadas e persistência local (SharedPreferences JSON).
- ✅ PIN parental de 4 a 8 dígitos com `PBKDF2WithHmacSHA256` e sal aleatório, para a **tela de configurações**.
- ✅ Motor puro Java de correspondência por expressão (ignorando acentos, caixa e pontuação) e domínio por host/sufixo confiável.
- ✅ Motor de regras testado com `engine-tests/run.sh`.
- ⚠️ Serviço `AccessibilityService` que **tenta** interromper pesquisa editável e cliques em títulos com palavras proibidas no YouTube, Chrome, Firefox, Samsung Internet, Edge e Brave.
- ⚠️ Regra de domínio atua **somente quando uma URL com o host é digitada em um campo editável acessível**; não é filtro de DNS/VPN.
- ⚠️ Regra de aplicativo tenta retornar à tela inicial quando o pacote bloqueado abre.
- ❌ Não há remoção individual de miniaturas do YouTube, varredura de toda a página, classificação semântica, inspeção HTTPS, VPN ou suporte Smart TV.
- ❌ APK **não gerado nem testado em aparelho** neste ambiente (Android SDK/Gradle não disponíveis). O projeto é um ponto de partida para validação no Android Studio.

## Cadastro de palavras em lote

Abra **+ Adicionar regra → Palavra ou expressão** e digite, por exemplo:

```text
Bob Esponja, SpongeBob, Sponge Bob, SquarePants
```

O ContentGuard cria uma regra **por expressão**, preservando os espaços internos
de nomes compostos. Também é possível colar uma lista com uma expressão por linha.
Entradas vazias e equivalentes (acentos, pontuação e caixa) são ignoradas.
Regras que já estão salvas não são duplicadas. Um termo inválido (fora do
limite de 2 a 180 caracteres) cancela todo o lote, sem salvar apenas parte.
Máximo de 500 entradas distintas por lote. O cadastro de DOMÍNIO e APP
continua individual. A eficácia do bloqueio depende dos elementos expostos
pelo YouTube/navegador e deve ser testada em um dispositivo de teste.

## Compilar com GitHub Actions (sem Android Studio)

Adicionamos o workflow `.github/workflows/android-apk.yml`. Ele tenta gerar
um **APK de debug** como artefato de CI. Consulte o passo a passo em
[docs/GITHUB_ACTIONS.md](docs/GITHUB_ACTIONS.md).

**Importante:** Ainda nao executamos esse workflow no GitHub. Um APK funcional
nao foi gerado nem testado; este codigo e experimental.

## Opcional: abrir e compilar localmente

Requisitos: Android Studio atualizado, Android SDK Platform 35, JDK 17, Gradle 8.9, Android Gradle Plugin 8.7.3.

1. No Android Studio, abra a pasta `contentguard-android` como projeto Gradle.
2. **Wrapper:** somente `gradle-wrapper.properties` está incluído. O wrapper JAR/scripts não foram gerados neste ambiente, pois o Gradle não estava disponível. Caso sua IDE exija o wrapper completo, com o Gradle 8.9 instalado execute `gradle wrapper --gradle-version 8.9` (ou gere o wrapper num projeto Gradle local e copie os artefatos oficiais, validando os hashes).
3. Sincronize o projeto, compile `app` em modo debug e instale em um dispositivo **de teste**, antes de qualquer uso por criança.
4. Abra o aplicativo, crie PIN, cadastre uma palavra, confirme a divulgação de acessibilidade e ative manualmente o serviço nas Configurações do Android.
5. Teste gradualmente no app do YouTube e no navegador, anotando casos em que o conteúdo aparece, não bloqueia ou causa bloqueios indevidos.

**Atenção:** versões recentes do Android podem exigir confirmação adicional para serviços de acessibilidade de aplicativos instalados fora de lojas confiáveis. Não contorne controles de segurança sem compreender o risco e obter autorização da responsável.

## Arquitetura

```
MainActivity                      UI local, PIN e manutenção de regras
  |-- PinStore                    PBKDF2 + salt
  |-- RuleRepository              armazenamento local de regras
GuardAccessibilityService         eventos somente em apps suportados
  |-- RuleRepository              recarrega ao mudar regras
  |-- RuleMatcher                 texto e domínio normalizados (Java puro)
```

### Sem falsas garantias

O Android envia eventos de acessibilidade **depois** que a interface muda. O YouTube pode não expor ao serviço o título completo ou o campo de busca. Portanto, o app **não pode assegurar** que a criança nunca veja uma miniatura, sugestão ou trecho de vídeo. O YouTube pode ainda modificar os elementos acessíveis sem aviso. Não use este MVP como substituto de supervisão ativa.

O PIN limita edição **dentro do ContentGuard**, mas não evita que o usuário abra as Configurações do Android, desative a acessibilidade, limpe os dados ou desinstale o aplicativo. Esse nível de proteção exige gerenciamento parental aprovado pelo Android e testes adicionais.

### Privacidade e publicação

O serviço lê temporariamente textos exibidos/alterados na interface **somente** dos aplicativos suportados; não armazena o texto das telas, histórico ou buscas. Não possui permissão `INTERNET`. Não lê campos de senha. A responsável precisa autorizar o recurso no aparelho.

Para eventual publicação na Google Play, o uso da Accessibility API exige descrição clara, divulgação destacada e consentimento afirmativo; apps de controle parental não devem alegar ser ferramentas de acessibilidade se essa não for sua função central. Consulte a política atual do Google Play antes da publicação.

## Próximas etapas

1. **P0 — teste em Android real:** validar eventos do campo de pesquisa do YouTube/Chrome e cliques em títulos, medindo falsos positivos e atraso.
2. **P1 — controle de domínios independente da UI:** avaliar `VpnService`/DNS local, regras de domínios e proteção de tráfego com devido consentimento. Não confundir bloqueio de domínio com filtro de vídeo individual em HTTPS.
3. **P1 — lista de apps instalados:** seleção por nome/ícone, não exigindo informar pacote Java.
4. **P2 — regras por contexto:** grupos, ativação/desativação, exceções, horário, importação/exportação local.
5. **P2 — extensões ou integração de navegador:** filtrar páginas e miniaturas onde tecnicamente e contratualmente permitido, sem alegar compatibilidade universal.
6. **P3 — suporte a Smart TV:** avaliar Android/Google TV separadamente; Samsung Tizen e LG webOS exigem abordagem diferente.

## Testes locais do motor de regras

```bash
./engine-tests/run.sh
```

## Fontes oficiais

- Android AccessibilityService: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- Google Play — permissões e APIs sensíveis: https://support.google.com/googleplay/android-developer/answer/16558241
