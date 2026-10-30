# Validação de campo de pesquisa / Chrome — ContentGuard

## Resultados observados no primeiro teste

- A pesquisa por Bob Esponja no YouTube oficial foi interrompida.
- A caixa de busca da página Google no Chrome limpou o texto proibido.
- A barra de endereços do Chrome permitiu fazer a pesquisa.
- Um link de vídeo do Google abriu o YouTube e o vídeo foi reproduzido.

## O que muda nesta branch

- Em eventos de mudança de texto/conteúdo do Chrome, consultar *somente o campo de entrada com foco*.
- Se for reconhecido como a barra de endereço/pesquisa do Chrome e o texto corresponder à regra local, tentar limpar o campo e voltar.
- Não examinar toda a página: isso poderia causar falsos positivos em resultados e vídeos recomendados.

## Teste controlado em Android

1. Em dispositivo de teste autorizado, confirmar que a Acessibilidade do ContentGuard está ativa.
2. Cadastrar regras individuais: `Bob Esponja`, `SpongeBob`.
3. Abrir Chrome, tocar na **barra superior de endereço**, digitar `Bob Esponja` e observar se limpa.
4. Abrir Google no Chrome, digitar `Bob Esponja` na caixa de pesquisa da página e comparar.
5. No YouTube normal, testar pesquisa por `Bob Esponja` e por um título permitido (`Tom e Jerry`).
6. No Chrome, abrir um link de vídeo proibido no YouTube: **espera-se que continue uma lacuna nesta branch**.
7. Anotar apenas: versão do Android, versão do Chrome, tipo de teste e resultado. Não registrar buscas pessoais nem dados da criança.

## Limites

Este MVP **não intercepta navegação HTTPS** nem conhece os metadados reais do vídeo só pelo URL do YouTube. Quando um link externo abre diretamente o reprodutor, não há necessariamente um campo editável nem clique com o título acessível. A próxima etapa necessita validar, em dispositivo real, se a tela de reprodução expõe o título em nós de acessibilidade. Não criar filtros que analisem cegamente todo o resultado do YouTube, pois bloqueariam vídeos permitidos quando aparecerem sugestões com termos proibidos.

A política de segurança do Play Protect deve continuar ativa em aparelhos de uso pessoal.
