# ContentGuard Child — Google TV / Android TV (MVP 0.4)

## Escopo
A versão **TV é separada** do aplicativo para celular e tem seu próprio APK:
`br.com.lcmtech.contentguard.tv`. Não sincroniza regras com o celular,
não tem navegador, não tem integração com serviços externos e não acessa a internet.

- Usa `LEANBACK_LAUNCHER`, `android.software.leanback` e navegação por **controle remoto**.
- Usa o ícone/arte ContentGuard Child e dispõe de tela própria para cadastrar regras.
- Só cadastra **palavras e expressões** separadas por vírgulas ou linhas.
- Protege as configurações com um PIN definido pela pessoa responsável.
- Serviço Android `TvYoutubeAccessibilityService` filtra eventos apenas do pacote
  `com.google.android.youtube.tv`, sem salvar histórico ou pesquisas.
- Tenta limpar o campo de busca proibido, interromper vídeos ao selecionar/clicar
  em itens cujos rótulos contenham uma regra e reconhecer título do player quando
  o identificador de acessibilidade explicitamente contiver player/watch + title.

## Limitação principal
**Não há garantia de bloqueio do YouTube TV.** O aplicativo YouTube da TV pode
não expor título, busca ou conteúdo selecionado à API de Acessibilidade. O bloqueio
pode ocorrer *depois* do início da reprodução ou não acontecer, especialmente em
vídeos abertos por sugestões e em telas de reprodução sem IDs de título acessíveis.
O serviço também pode não ser ativável nas configurações de alguns modelos de TV.
A implementação prefere deixar passar um conteúdo não identificável a bloquear
aleatoriamente outros vídeos por recomendações.

Não utilize esta versão como única proteção no aparelho de uma criança.

## Gerar APK pelo GitHub Actions (sem Android Studio)
O workflow `.github/workflows/android-apk.yml` agora gera dois artifacts:
- `contentguard-android-debug-apk` — celular Android;
- `contentguard-google-tv-debug-apk` — Google TV / Android TV.

## Instalação na TV
1. Baixe a versão **Google TV** em Actions → artifacts, após o workflow verde.
2. No modelo de TV autorizado para testes, utilize o procedimento oficial de
   instalação de APK compatível com o fabricante (por USB ou ADB, se disponível).
3. Se o Android/Play Protect bloquear a instalação, não desative a proteção da
   TV da família para contornar o alerta; use um equipamento separado.
4. Abra `ContentGuard Child TV` pelo launcher da TV; crie PIN e cadastre regras.
5. Pela tela do app, abra configurações da **Acessibilidade** da TV e procure por
   `ContentGuard Child TV · Filtro YouTube`. Se não houver opção de ativar,
   documente a incompatibilidade; o app não consegue ativar a própria permissão.

## Matriz de teste — no aparelho secundário
| Cenário | Observar |
| --- | --- |
| Abrir tela de configurações e navegar por D-pad | botões recebem foco claro |
| Adicionar `Bob Esponja, SpongeBob, SquarePants` | 3 regras independentes |
| Repetir o lote | não duplica |
| Pesquisar `Bob Esponja` no YouTube TV | texto bloqueado, ou anotar lacuna |
| Selecionar vídeo proibido pelo controle | voltar se o evento expõe título |
| Abrir resultado com título genérico/sugestões | anotar possível lacuna |
| Assistir vídeo permitido como `Tom e Jerry` | não deve ser bloqueado |
| Sair e reabrir ContentGuard TV | PIN solicitado novamente |
| Desativar acessibilidade | app exibe status desativado |

## Segurança/privacidade
Use em dispositivo de teste sem dados sensíveis; nunca envie dados pessoais da
criança, históricos ou fotos das telas privadas para o repositório. Acessibilidade
é uma permissão sensível, que requer consentimento expresso, com possibilidade de
revogação a qualquer momento. Mantenha o Play Protect ativo.
