# KampüsAğı — Claude Code'u limit dolsa bile kaldığı yerden sürdüren döngü
# Kullanım: proje kök klasöründe  ->  powershell -ExecutionPolicy Bypass -File .\kesintisiz-calistir.ps1

$PromptFile   = ".\KAMPUSAGI_CLAUDE_CODE_PROMPT.md"
$ProgressFile = ".\docs\PROGRESS.md"
$WaitMinutes  = 15   # limit/hata sonrası tekrar denemeden önce bekleme
$Tools        = "Read,Edit,Write,Glob,Grep,Bash"

$first = $true
while ($true) {
    if ((Test-Path $ProgressFile) -and (Select-String -Path $ProgressFile -Pattern "^DURUM: TAMAMLANDI" -Quiet)) {
        Write-Host "Proje tamamlandı." -ForegroundColor Green
        break
    }

    if ($first -and -not (Test-Path $ProgressFile)) {
        Get-Content $PromptFile -Raw | claude -p --permission-mode acceptEdits --allowedTools $Tools
    } else {
        claude -p --continue --permission-mode acceptEdits --allowedTools $Tools `
          "KAMPUSAGI_CLAUDE_CODE_PROMPT.md dosyasındaki kuralları tekrar oku. docs/PROGRESS.md'den kaldığın yerden devam et. Durma, soru sorma."
    }
    $first = $false

    Write-Host "Oturum bitti ($(Get-Date -Format HH:mm)). $WaitMinutes dk sonra devam edilecek..." -ForegroundColor Yellow
    Start-Sleep -Seconds ($WaitMinutes * 60)
}
