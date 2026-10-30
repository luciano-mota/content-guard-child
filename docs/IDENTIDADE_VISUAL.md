# Identidade visual — ContentGuard Child

A arte com a família protegida dentro de um escudo é usada como ícone do aplicativo.
A arte com a criança, os responsáveis e o símbolo do infinito é a imagem de abertura.

- Ícone: recursos WebP em `mipmap-*`, com ícone adaptativo para Android 8+.
- Tela de abertura: `SplashActivity`, com a arte em `drawable-nodpi/contentguard_opening.webp`.
- Apresentação curta (aprox. 1,2 s) seguida da tela principal e do PIN.
- O conteúdo da imagem mantém a proporção original, sem cortar o texto da capa.
- Não há alteração das regras de bloqueio nem das permissões sensíveis nesta mudança.

**Teste importante:** verificar visualmente o ícone na tela inicial e a capa ao abrir o app em dispositivos
Android com diferentes proporções de tela. O APK precisa ser compilado e testado no aparelho.
Como o GitHub Actions usa uma chave de assinatura debug temporária, outra execução pode gerar
um APK incompatível para atualização sobre instalações de testes anteriores; se necessário,
desinstalar o APK de teste antigo antes de instalar o novo (isso apaga regras/PIN locais).
Não realizar essa migração em celulares de crianças antes da validação.
