# ContentGuard Child Android 0.5 — Pesquisa no TikTok e Kwai

## Novo escopo da versão de celular
As regras de palavras existentes passam a ser verificadas também **nas pesquisas** dos aplicativos oficiais:
- TikTok BR: `com.zhiliaoapp.musically`
- TikTok regional: `com.ss.android.ugc.trill`
- Kwai: `com.kwai.video`

O aplicativo detecta campos de texto editáveis identificáveis como pesquisa pelos
rótulos de interface, descrições e IDs acessíveis. Tenta limpar palavras proibidas
na digitação e também após mudança da tela de busca. Ao selecionar sugestões
ou resultados **enquanto um campo de pesquisa está visível**, tenta interromper
o item específico se o rótulo contém uma das regras.

**Não inspeciona por padrão o feed inteiro, comentários ou mensagens privadas.**
O código não solicita internet, não registra buscas nem lê vídeo/áudio.

## Matriz de QA no celular com autorização

1. Confirmar a permissão de Acessibilidade do ContentGuard ativa.
2. Cadastrar palavras: `Bob Esponja, SpongeBob, SquarePants`.
3. YouTube: digitar termo bloqueado; confirmar que o comportamento anterior não regrediu.
4. Chrome: pesquisar termo e selecionar sugestão; confirmar comportamento anterior.
5. **TikTok:** abrir lupa/pesquisa, digitar `Bob Esponja`, observar limpeza/interrupção.
6. TikTok: pesquisar `Tom e Jerry`; confirmar que a pesquisa permitida funciona.
7. TikTok: selecionar sugestão/histórico `SpongeBob`; observar se o clique é impedido.
8. TikTok: escrever uma expressão bloqueada num campo de **comentário** em conta de teste;
   não deve bloquear como pesquisa.
9. **Kwai:** repetir os cenários 5–8 na pesquisa do Kwai.
10. Abrir um vídeo do feed com título bloqueado, sem passar por busca: **não é objetivo
    desta versão; não afirmar que esteja bloqueado**.
11. Verificar erros, travamentos ou comportamentos de Acessibilidade inesperados.

## Limitações importantes
- O TikTok/Kwai podem esconder a identidade do campo de busca ou não publicar
  eventos acessíveis em determinada versão. Neste caso o filtro **não age**;
  depende de teste real para cada aplicativo e versão.
- Recomendações, reprodução automática, legendas visuais e feed infinito
  **não estão protegidos**.
- Conteúdos podem aparecer antes da decisão do serviço.
- O PIN e o serviço não impedem que alguém desative/desinstale o app.
- Se o APK debug for assinado com outra chave em Actions, a atualização pode exigir
  desinstalação e apagar PIN e regras locais. Mantenha Play Protect ativo e teste
  num dispositivo autorizado antes de distribuir para criança.

## Google TV
Nenhuma regra de TikTok ou Kwai foi adicionada à versão Google TV. O módulo
`TvYoutubeAccessibilityService` continua dedicado ao YouTube TV.
