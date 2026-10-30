# Diagnóstico da Acessibilidade na Google TV — v0.4.1

O aplicativo agora exibe três informações independentes:
- **Autorizado nas configurações Android**: se a lista interna `ENABLED_ACCESSIBILITY_SERVICES` contém exatamente o serviço ContentGuard TV.
- **Serviço conectado ao sistema**: se `TvYoutubeAccessibilityService.onServiceConnected()` foi executado no processo do aplicativo.
- **Reconhecido pelo gerenciador Android**: se a API `AccessibilityManager` relata o serviço no conjunto de serviços habilitados.

**Resultados:**
- SIM/SIM/SIM: serviço ativado e conectado. Mesmo assim, os títulos do YouTube TV podem ser inacessíveis.
- SIM/NÃO/*: ativação solicitada no Android, mas o serviço não se conectou. Reinicie a TV e use “Atualizar diagnóstico”.
- NÃO/NÃO/*: confirme se foi ativado **ContentGuard Child TV · Filtro YouTube**, e não apenas Acessibilidade global/TalkBack.
- NÃO/SIM/*: a API de ajustes pode ter restrições de leitura no firmware. Verifique o serviço nomeado nas configurações.

A versão 0.4.1 não coleta nem transmite logs ou texto assistido. O diagnóstico é apenas mostrado localmente ao adulto com PIN; não o compartilhe com identificadores privados.

O APK de teste é gerado em GitHub Actions, artifact `contentguard-google-tv-debug-apk`. A instalação sobre a versão anterior requer assinatura compatível; APKs debug de workflows diferentes podem usar certificados distintos. Caso seja exigida reinstalação, o Android apagará o PIN/regras existentes ao desinstalar. Faça uma lista das regras antes.

Nota: “Serviço conectado” **não comprova** que o YouTube para TV disponibiliza títulos dos vídeos por Acessibilidade; essa limitação precisa de testes no hardware.
