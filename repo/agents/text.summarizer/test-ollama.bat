@echo off
echo Testing Ollama API with text.summarizer agent configuration...
echo.

curl -s http://localhost:11434/api/chat ^
  -H "Content-Type: application/json" ^
  -d @request.json

echo.
echo.
echo Test completed.
pause
