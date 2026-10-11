@echo off
echo ========================================================
echo   KHOI DONG HE THONG RAG CHATBOT (PDF/DOCS + GEMINI)
echo ========================================================
echo Dang bien dich va khoi chay Server Spring Boot...
cd /d "%~dp0\.."
.\gradlew.bat :rag-document-chat:bootRun
pause
