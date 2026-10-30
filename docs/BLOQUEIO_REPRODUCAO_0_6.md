# ContentGuard Child 0.6 — Bloqueio de reprodução e proteção contra desativação

As regras continuam sendo as **palavras e expressões cadastradas pelos responsáveis**:
qualquer desenho, personagem, canal ou tema indesejado (ex.: `Bob Esponja,
Patrick Estrela, Lula Molusco`). Nada é fixo no código.

## 1. Bloqueio de reprodução (celular e Google TV)

O YouTube da Google TV praticamente não expõe títulos à Acessibilidade, então
barrar só a pesquisa não funcionava. Todo player Android, porém, publica o vídeo
que está tocando na **sessão de mídia** do sistema (é o que alimenta o "Tocando
agora", o Assistente e a notificação de mídia).

O novo `PlaybackGuardService` lê **somente** as sessões de mídia ativas: título,
canal e descrição do vídeo em reprodução. Se algum campo contém uma palavra
cadastrada, ele:

1. **pausa** o vídeo imediatamente;
2. pede ao serviço de Acessibilidade para sair do player (BACK), apenas se o
   player está na tela — uma música em segundo plano nunca aciona BACK em outro app;
3. pausa de novo toda vez que alguém aperta play no mesmo vídeo.

Cobre vídeos abertos por **pesquisa, recomendação, autoplay, busca por voz do
controle remoto e links**, no YouTube, YouTube Kids e em qualquer outro player que
publique a sessão de mídia (apps de streaming, navegadores).

### Permissão necessária
O Android só entrega as sessões de mídia a apps com **Acesso a notificações**.
O serviço não lê, não salva e não remove notificações.

- **Celular:** ContentGuard → *Autorizar bloqueio de reprodução* → ativar
  `ContentGuard · Bloqueio de reprodução`. No Android 13+, se a opção estiver
  cinza, abra *Informações do app* → ⋮ → *Permitir configurações restritas*.
- **Google TV:** ContentGuard Child TV → *Autorizar bloqueio de reprodução*.
  Muitas TVs não mostram a tela de Acesso a notificações. Nesse caso, com a
  depuração ADB ativada (Configurações → Sistema → Sobre → toque 7× em
  *Versão do SO Android TV* → Opções do desenvolvedor → Depuração USB/ADB):

```bash
adb connect IP_DA_TV
```

```bash
adb shell cmd notification allow_listener br.com.lcmtech.contentguard.tv/br.com.lcmtech.contentguard.PlaybackGuardService
```

### Como confirmar na TV
1. No painel do ContentGuard TV, *BLOQUEIO DE REPRODUÇÃO* deve mostrar **ATIVO**.
2. Toque um vídeo permitido no YouTube, volte e use *Atualizar diagnóstico*:
   *Vídeo detectado na sessão de mídia* deve mostrar **SIM**.
3. Opcional, pelo computador: `adb shell dumpsys media_session` lista a sessão
   do YouTube com `metadata: ... description=<título>`.
4. Toque um vídeo com uma palavra cadastrada: deve pausar e voltar.

## 2. Formas de burlar cobertas

| Tentativa | Cobertura |
| --- | --- |
| Abrir o vídeo pela página inicial, recomendação ou autoplay | Bloqueio de reprodução (título/canal) |
| Busca por voz no controle ou no celular | Bloqueio de reprodução; no celular também o campo preenchido pela voz |
| Apertar play de novo após a pausa | Pausa novamente |
| YouTube Kids (TV e celular) | Bloqueio de reprodução; no celular também pesquisa |
| Shorts / título no player do YouTube celular | Título do player (quando acessível) + sessão de mídia |
| Barra de pesquisa do Google na tela inicial, Google Go | Campos de pesquisa monitorados |
| Outro navegador (Chrome Beta, Opera, DuckDuckGo, Mi, Vivaldi, Kiwi, UC, Firefox Focus...) | Campos de pesquisa/URL monitorados |
| Escrever diferente: `B0b Esp0nja`, `bobesponja`, `B O B`, `Boob Espoonja`, `Sponge Bob` | Comparação tolerante, sem confundir `Bobby` com `Bob` |
| Vídeo no feed do TikTok/Kwai (0.7) | Legenda, hashtags ou perfil visíveis: pula para o próximo vídeo; se continuar aparecendo conteúdo bloqueado (busca, perfil), volta de tela |
| TikTok Lite | Mesmo tratamento do TikTok |
| Desativar a Acessibilidade ou o Acesso a notificações | Telas que citam o ContentGuard são fechadas (HOME) |
| Forçar parada, limpar dados ou desinstalar pelas Configurações | Idem (Informações do app, desinstalação) |

**Para os responsáveis mexerem nas Configurações:** no painel do ContentGuard
(com PIN), use *Liberar Configurações por 15 minutos*. Os botões que abrem
Acessibilidade e Acesso a notificações já liberam automaticamente. Mudar o
relógio do aparelho não estende a liberação além de 15 minutos.

## 3. O que ainda escapa (honestamente)

- Os **primeiros segundos** podem aparecer antes da pausa.
- Vídeos cujo **título e canal não contêm nenhuma palavra cadastrada**
  (ex.: compilações com título genérico). Cadastre também nomes de personagens
  e de canais.
- **Modo de segurança**, ADB e restauração de fábrica desativam apps de terceiros.
- Apps de vídeo que não publicam sessão de mídia e não estão na lista de pesquisa.
- No TikTok/Kwai (0.7), só é reconhecido o vídeo cuja legenda, hashtags ou nome
  de perfil **visíveis** contenham uma palavra cadastrada. Vídeos sem texto
  identificável passam. O vídeo pode tocar por um instante antes de ser pulado.
- Instalar outro app de vídeo: use o Family Link para exigir aprovação de instalação.

Teste sempre num aparelho de teste antes de confiar na proteção.
