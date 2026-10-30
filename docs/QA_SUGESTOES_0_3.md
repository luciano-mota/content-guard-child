# Testes: sugestões de pesquisa e cadastro em lote — Android MVP 0.3

## Cadastro em lote (já implementado na branch principal)
1. Na tela de regras, selecione **Palavra ou expressão**.
2. Digite `Bob Esponja, SpongeBob, SquarePants`, separado por vírgulas.
3. Confirme **Adicionar**. Devem ser criadas três regras independentes.
4. Repita o lote, incluindo `bob-esponja` e acentos/maiúsculas diferentes: não devem duplicar.
5. Teste também conteúdo permitido, como `Tom e Jerry`.

## Pesquisa: matriz de teste
| Cenário | Resultado esperado da nova tentativa |
| --- | --- |
| Digitar palavra proibida na busca interna do YouTube | Limpar pesquisa (já observado pelo usuário) |
| Digitar na barra de endereço do Chrome | Limpar pesquisa (já observado) |
| Digitar no campo do Google no Chrome | Limpar pesquisa (já observado) |
| **Tocar numa sugestão antiga/autocompletar dentro da barra do Chrome** | Tentar interromper pelo item selecionado ou pelo campo de pesquisa carregado |
| **Tocar numa sugestão no campo Google aberto no Chrome** | Mesma proteção, sem bloquear outros resultados na página |
| Tocar numa sugestão de `Tom e Jerry` | Permitir |
| Abrir vídeo por link externo sem expor o título | **Ainda pode escapar: não afirmar que está resolvido** |

## Proteções do algoritmo
- Lê a descrição/texto do evento de clique ou seleção.
- Se o item clicado não tiver texto, examina somente sua pequena subárvore e o ancestral clicável mais próximo, com limites.
- No Chrome, observa **campos de texto editáveis**, e não textos de todos os resultados/recomendações.
- Reconhece URLs de busca Google com parâmetro `q` codificado (ex.: `?q=Bob%20Esponja`), quando esse campo está acessível.
- Não captura credenciais, não salva nem transmite termos de pesquisa.
- Sem garantia contra todos os formatos de sugestão: a árvore de acessibilidade pode ser diferente em cada versão.

## Antes de distribuir para a família
O código do serviço precisa ser **validado em aparelho Android de teste**. Os testes unitários validam apenas correspondência de termos e URLs; não reproduzem eventos reais do Chrome/YouTube. Mantenha Google Play Protect ativado em aparelhos pessoais, use conta de teste e verifique falsos positivos.
