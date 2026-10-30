# Google TV: bloquear busca e impedir abertura de vídeo (0.6.0 — experimental)

## Objetivo
1. **Busca por palavra proibida** (ex.: `Bob Esponja`): tentar limpar o campo
   editável do YouTube TV e voltar; também tentar tratar componentes próprios de
   pesquisa que exponham ID de busca/query.
2. **Vídeo ainda visível nos resultados**: antes da abertura, quando um cartão
   focalizado expõe um título proibido na árvore de Acessibilidade, consumir a
   tecla OK/Enter do controle remoto. O YouTube não recebe esse toque.
3. **Fallback pós-clique**: se a tecla não for interceptada, tentar voltar ao
   receber evento de clique com título proibido ou identificar título do
   player usando ID reconhecível `player/watch + title`.

## Como testar na TV
1. Instale o artifact `contentguard-google-tv-debug-apk` do GitHub Actions.
   **Se a TV não aceitar a atualização por assinatura diferente,** desinstalar
   a versão antiga pode apagar todas as regras e o PIN. Anote-os primeiro.
2. **Desative e reative só o serviço ContentGuard Child TV · Filtro YouTube**
   nas configurações de Acessibilidade. A versão 0.6 solicita a capacidade
   extra de filtrar teclas do controle remoto; a nova autorização precisa ser
   aceita pelo usuário responsável.
3. Abra ContentGuard, informe o PIN, cadastre
   `Bob Esponja, SpongeBob, SquarePants` e confirme o status de conexão.
4. Abra YouTube TV e pesquise `Bob Esponja`. Se a pesquisa não for interrompida,
   deixe aparecer os resultados.
5. **Selecione um vídeo que contenha o termo no título, mas antes de apertar OK**,
   confira que ele está em foco. Aperte OK. Esperado: aviso "Vídeo bloqueado" e
   permanência na tela de resultados, caso o título do item seja acessível.
6. Faça o controle com `Tom e Jerry`: deverá abrir normalmente.
7. Volte ao ContentGuard e toque em **Atualizar diagnóstico**. Observe:
   - `Serviço conectado ao sistema`: SIM/NÃO;
   - `Eventos recebidos do YouTube TV`: SIM/NENHUM;
   - `Botão OK recebido do controle`: SIM/NENHUM;
   - `Seleções impedidas nesta sessão`: contador.

## Interpretação
- **Sem eventos do YouTube:** serviço, pacote correto ou sistema da TV.
- **Eventos SIM, botão OK NENHUM:** o firmware pode não permitir o filtro de
  teclas; verifique nova autorização. Outro serviço com captura de teclas
  também pode impedir o ContentGuard de receber eventos.
- **Botão OK SIM, mas contador 0 e vídeo abriu:** o YouTube TV provavelmente
  não disponibilizou ao filtro o título do **item focado**. A aplicação não
  tem como deduzir o título pelo vídeo nem pelo botão OK sozinho.
- **Contador aumentou e vídeo não abriu:** bloqueio preventivo funcionou.
- **Contador aumentou mas vídeo abriu:** pode ter sido aberta outra rota (voz,
  autoplay, controle externo) e exige investigar eventos por aparelho.

## Limites de segurança/privacidade
O método não pode garantir 100% de cobertura: versões de YouTube TV expõem
árvores diferentes e podem usar uma interface de vídeo sem texto acessível.
A lógica nunca varre a lista inteira de recomendações para decidir qual item
foi clicado, para não bloquear vídeos permitidos por engano. Também não
inspeciona frames, áudio, conta Google, HTTPS ou histórico de vídeos.

A aplicação retém somente três **sinais de diagnóstico em memória**
(data aproximada do último evento, último OK e número de seleções barradas);
não armazena títulos, pesquisas ou conteúdos. Permanece sem permissão de
internet. Não substitui supervisão nem outro controle parental já validado.

**Se os títulos não forem expostos na sua TV, não há como prometer que
este filtro funcionará com a técnica de Acessibilidade.** Recomendamos
não instalar como proteção exclusiva no aparelho de uma criança.
