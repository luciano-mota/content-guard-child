# Gerar APK sem Android Studio

O projeto inclui `.github/workflows/android-apk.yml`, que prepara Java 17,
Android SDK 35 e Gradle 8.9 no runner, executa os testes locais e tenta compilar
um **APK de depuracao**. **A pipeline ainda nao foi executada no GitHub.**

## Instrucoes
1. Crie um repositorio **privado** chamado `contentguard-android`, sem arquivos iniciais.
2. Envie o codigo (inclusive o diretorio oculto `.github/workflows/`).
3. Na aba **Actions**, execute `ContentGuard - gerar APK de teste` (ou aguarde a
   execucao automatica apos o push na `main`).
4. Confirme que o job terminou com sucesso; em **Artifacts** baixe
   `contentguard-debug-apk`, extraia o arquivo ZIP e encontre `app-debug.apk`.
5. Instale apenas em **um Android de teste**, com autorizacao da responsavel.

### Cuidados
- APK **debug**; ainda nao houve compilacao nem validacao Android real.
- Nao promete ocultar pesquisas, miniaturas ou resultados do YouTube.
- PIN limita apenas a interface do ContentGuard, nao impede desinstalacao.
- Regras locais; o app nao pede permissao de internet.
- Nao colete dados de criancas para depurar; use exemplos ficticios.
- Nao instale diretamente no celular da crianca antes de avaliar estabilidade.
