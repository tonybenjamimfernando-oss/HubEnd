# HubOps 1.9.1 — relatório de alterações e validação

## Âmbito
Esta versão aperfeiçoa apenas as funcionalidades gerais: GitHub, WordPress, HTTP, Terminal local, diagnóstico do dispositivo, feed, parsers e validação das configurações. Os módulos Despertar e respetivos analisadores/geradores não foram modificados.

## Alterações funcionais
- GitHub: paginação até 100 páginas, limite 1–100 itens por página, repositórios públicos de utilizador e repositórios autorizados da conta autenticada; apresentação de estrelas, forks, linguagem, visibilidade e última atualização.
- WordPress: paginação até 50 publicações por página; leitura de conteúdos acessíveis quando autenticado; URL base normalizada para HTTPS; rejeição de user-info, query e fragment; criação de rascunho validada por ID e estado retornados pela API.
- HTTP dos módulos gerais: até três redirecionamentos; cada destino é revalidado como HTTPS; redirects em pedidos de escrita não são seguidos; cabeçalhos de autenticação/cookies são removidos ao mudar de origem; timeout e limite de resposta são validados. O método HTTP legado usado pelo Despertar foi mantido inalterado.
- Terminal: limites de timeout/saída validados, stdin fechado, buffer de saída protegido contra corrida e histórico de sessão limpável; execução permanece no UID/sandbox Android da aplicação e não oferece root.
- Dispositivo: leitura de bateria/carregamento, RAM, armazenamento do volume da pasta privada e uptime.
- Feed: buffer de replay finito, limite de 100 itens e anonimização de comandos nos eventos agregados.
- Configuração: validação do URL e coerência das credenciais WordPress antes de persistir.

## Verificações executadas neste ambiente
- `kotlinc` compilou isoladamente os serviços gerais (HTTP, GitHub, WordPress, parsers e Terminal) com stubs temporários para os componentes de plataforma/dependências não disponíveis neste ambiente.
- Smoke tests JVM: política HTTPS (aceita HTTPS e rejeita HTTP/credenciais/fragmentos), normalização de URL WordPress, validação de username GitHub, extração de texto HTML, execução `printf`, código de saída, limite de 100 bytes e timeout do Terminal: aprovados.
- Feed/EventBus: compilação e smoke test com replay de eventos, atualização do feed e ocultação de argumentos de comandos: aprovados; foi usada uma pequena compatibilidade de teste para a extensão `MutableStateFlow.update` ausente na versão local de coroutines.
- Integridade do ZIP final: verificada após empacotamento.

- Comparação direta entre os arquivos 1.9.0 e 1.9.1 confirmou que `app/src/main/java/dev/hubops/despertar/` permanece byte a byte inalterado. O método HTTP legado usado por esse pacote também conserva o comportamento anterior.

## Verificações não executadas
- Não foi possível executar `assembleDebug` nem `testDebugUnitTest`: este ambiente não tem Gradle instalado, Gradle Wrapper funcional ou Android SDK/NDK configurados.
- As APIs Android (diagnóstico de RAM/bateria), ecrãs Compose e integração real com APIs GitHub/WordPress ainda necessitam de validação num dispositivo/emulador e numa build Android completa.
- Não foram feitos pedidos de escrita para qualquer site WordPress nem alterações a repositórios GitHub durante a validação.

## Limites conhecidos
- O Terminal executa comandos fornecidos pelo utilizador com o mesmo UID da app; não é um contentor isolado nem um substituto de Termux. Processos filhos destacados podem exigir gestão adicional em versões futuras.
- A paginação GitHub/WordPress é limitada deliberadamente para evitar ciclos ou consumo excessivo.
- O cliente HTTP não segue redirects de POST/PUT/PATCH; se um servidor exigir redirecionamento numa escrita, o utilizador recebe erro em vez de repetição ambígua.
