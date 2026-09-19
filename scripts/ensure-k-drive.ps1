# Proje yolunda Türkçe karakter (ı) var; Gradle test işçisi bu yolu çözemiyor (ClassNotFoundException).
# Çözüm: projeyi ASCII bir sürücü harfine (K:) eşle. Oturum başına bir kez çalıştır; idempotent.
$root = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path 'K:\KampusAgi-Android')) {
    subst K: /D 2>$null
    subst K: $root
}
Write-Host "K: -> $root"
