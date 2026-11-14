# Código de Resgate — projeto Android

O jogo completo fica em `app/src/main/assets/index.html` e roda offline dentro do app.
O progresso fica salvo no próprio celular.

## Opção 1 — Android Studio (no seu computador)
1. Instale o Android Studio (gratuito): https://developer.android.com/studio
2. File → Open → escolha esta pasta. Aguarde a sincronização do Gradle.
3. Build → Build App Bundle(s) / APK(s) → Build APK(s).
4. O APK fica em `app/build/outputs/apk/`.

## Opção 2 — GitHub com atualização automática no celular

### Configuração (uma vez só)
1. Crie um repositório **público** no GitHub (ex.: `codigo-de-resgate`).
   Precisa ser público para o app consultar as atualizações sem senha.
2. Envie os arquivos desta pasta (zip `CodigoDeResgate-github.zip`).
   **Nunca envie o arquivo `.jks`**: ele vai como segredo, no passo 3.
3. No repositório: **Settings → Secrets and variables → Actions → New repository secret**
   - Nome: `KEYSTORE_B64`
   - Valor: todo o conteúdo do arquivo `KEYSTORE_B64.txt`
4. Vá em **Actions → Publicar atualização → Run workflow**, digite `1.1.1` e rode.
5. Na aba **Releases** aparece o APK `CodigoDeResgate-1.1.1.apk`. Instale esse APK
   no celular **uma única vez** (ele instala por cima da 1.1.0 sem perder o progresso).
   A partir dele, o app passa a procurar atualizações sozinho.

### Toda vez que quiser atualizar
1. Substitua `app/src/main/assets/index.html` pela versão nova (Add file → Upload files).
2. **Actions → Publicar atualização → Run workflow**
   - versão: um número maior que o anterior (1.1.2, 1.2.0…)
   - novidades: o que mudou (aparece para os jogadores)
3. Pronto. Nos celulares:
   - o app verifica ao abrir e a cada ~12 horas em segundo plano;
   - aparece uma **notificação** “Código de Resgate X disponível”;
   - em **⚙️ Configurações** surge o cartão com **“Atualizar agora”** (e um ponto vermelho na engrenagem);
   - o APK é baixado e o Android pede para confirmar a instalação.
     Na primeira vez, o Android pede para permitir “instalar apps desconhecidos” para o jogo.

O fluxo “Gerar APK (teste)” roda a cada envio e deixa o APK em Artifacts, sem avisar os celulares.

## Publicar na Play Store
O APK sai assinado com chave de teste (serve para instalar no celular).
Para a loja, gere uma chave de upload própria e um App Bundle (.aab).

## Assinatura de teste
O app é assinado com `app/teste-codigoderesgate.jks` (senha `resgate192`).
Use sempre essa chave: assim cada APK novo instala por cima do anterior e o progresso é mantido.
No GitHub ela fica só como segredo `KEYSTORE_B64` — o arquivo `.jks` não vai para o repositório.
**Não publique na Play Store com essa chave.**
