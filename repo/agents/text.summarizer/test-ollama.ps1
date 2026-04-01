# Test Ollama API with text.summarizer agent configuration

Write-Host "Testing Ollama API with text.summarizer agent configuration..." -ForegroundColor Cyan
Write-Host ""

$response = Invoke-RestMethod -Uri "http://localhost:11434/api/chat" `
    -Method Post `
    -ContentType "application/json" `
    -InFile "request.json"

Write-Host "Response:" -ForegroundColor Green
$response | ConvertTo-Json -Depth 10

Write-Host ""
Write-Host "Extracted summary:" -ForegroundColor Yellow
$response.message.content

Write-Host ""
Write-Host "Model: $($response.model)" -ForegroundColor Gray
Write-Host "Test completed." -ForegroundColor Cyan
