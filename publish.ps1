# Music Hub - Script de Publicação 🚀

# 1. Configurações da Chave (Edite se quiser mudar as senhas)
$KEYSTORE_NAME = "music_hub.jks"
$KEYSTORE_PASSWORD = "password123"
$KEY_ALIAS = "music_hub_key"
$KEY_PASSWORD = "password123"

Write-Host "--- Preparando o APK de Produção ---" -ForegroundColor Cyan

# 2. Gerar KeyStore se não existir
if (-not (Test-Path $KEYSTORE_NAME)) {
    Write-Host "(!) KeyStore não encontrada. Gerando uma nova..." -ForegroundColor Yellow
    keytool -genkey -v -keystore $KEYSTORE_NAME -alias $KEY_ALIAS -keyalg RSA -keysize 2048 -validity 10000 `
            -storepass $KEYSTORE_PASSWORD -keypass $KEY_PASSWORD `
            -dname "CN=Joao Magdaleno, OU=Music Hub, O=Music Hub, L=Lisbon, S=Lisbon, C=PT"
    Write-Host "(+) KeyStore gerada com sucesso: $KEYSTORE_NAME" -ForegroundColor Green
}

# 3. Configurar Variáveis de Ambiente para o Gradle
$env:KEYSTORE_PATH = (Resolve-Path $KEYSTORE_NAME).Path
$env:KEYSTORE_PASSWORD = $KEYSTORE_PASSWORD
$env:KEY_ALIAS = $KEY_ALIAS
$env:KEY_PASSWORD = $KEY_PASSWORD

# 4. Compilar o APK
Write-Host "--- Compilando APK Release (Signed) ---" -ForegroundColor Cyan
./gradlew assembleRelease

# 5. Resultado
if ($LASTEXITCODE -eq 0) {
    Write-Host "`n🏆 SUCESSO! O seu APK está pronto em:" -ForegroundColor Green
    $apkPath = "app\build\outputs\apk\release\app-release.apk"
    if (Test-Path $apkPath) {
        Write-Host (Resolve-Path $apkPath).Path -ForegroundColor White
    }
} else {
    Write-Host "`n❌ Erro durante a compilação." -ForegroundColor Red
}
