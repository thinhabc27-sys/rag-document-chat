let selectedFile = null;

document.addEventListener("DOMContentLoaded", () => {
    initApiKey();
    initDropZone();
    initSampleFiles();
    initQuickChips();
    fetchMemoryStatus();

    document.getElementById("uploadBtn").addEventListener("click", handleUpload);
    document.getElementById("clearRamBtn").addEventListener("click", handleClearRam);
    document.getElementById("clearChatBtn").addEventListener("click", handleClearChat);
    document.getElementById("chatForm").addEventListener("submit", handleChatSubmit);
});

// Nút chọn nhanh câu hỏi gợi ý 1 chạm
function initQuickChips() {
    document.querySelectorAll(".quick-chip-btn").forEach(btn => {
        btn.addEventListener("click", () => {
            const query = btn.getAttribute("data-query");
            if (query) {
                const input = document.getElementById("chatInput");
                input.value = query;
                submitQuestion(query);
            }
        });
    });
}

// Nút chọn nhanh file mẫu
function initSampleFiles() {
    const pdfBtn = document.getElementById("loadSamplePdfBtn");
    const docxBtn = document.getElementById("loadSampleDocxBtn");

    if (pdfBtn) {
        pdfBtn.addEventListener("click", () => loadSampleFile("/samples/tai_lieu_mau_rag.pdf", "tai_lieu_mau_rag.pdf", "application/pdf"));
    }
    if (docxBtn) {
        docxBtn.addEventListener("click", () => loadSampleFile("/samples/tai_lieu_mau_rag.docx", "tai_lieu_mau_rag.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }
}

async function loadSampleFile(url, fileName, mimeType) {
    const selectedFileName = document.getElementById("selectedFileName");
    const uploadBtn = document.getElementById("uploadBtn");
    try {
        selectedFileName.innerHTML = `<span class="spinner-border spinner-border-sm text-primary"></span> Đang nạp ${fileName}...`;
        const res = await fetch(url);
        if (!res.ok) throw new Error("Không thể tải file mẫu");
        const blob = await res.blob();
        selectedFile = new File([blob], fileName, { type: mimeType });
        selectedFileName.innerHTML = `<i class="bi bi-file-earmark-check text-success me-1"></i> ${fileName} (${(selectedFile.size / 1024).toFixed(1)} KB)`;
        uploadBtn.disabled = false;
        hideAlert();
    } catch (err) {
        selectedFileName.innerHTML = `<span class="text-danger small">Lỗi nạp file: ${err.message}</span>`;
    }
}

// Quản lý API Key trên giao diện
function initApiKey() {
    const input = document.getElementById("apiKeyInput");
    const toggleBtn = document.getElementById("toggleApiKeyBtn");
    const saveBtn = document.getElementById("saveApiKeyBtn");

    const savedKey = localStorage.getItem("gemini_api_key");
    if (savedKey) {
        input.value = savedKey;
    }

    toggleBtn.addEventListener("click", () => {
        if (input.type === "password") {
            input.type = "text";
            toggleBtn.innerHTML = '<i class="bi bi-eye-slash"></i>';
        } else {
            input.type = "password";
            toggleBtn.innerHTML = '<i class="bi bi-eye"></i>';
        }
    });

    saveBtn.addEventListener("click", () => {
        const key = input.value.trim();
        localStorage.setItem("gemini_api_key", key);
        alert("Đã lưu API Key vào trình duyệt thành công!");
    });
}

function getApiKey() {
    return document.getElementById("apiKeyInput").value.trim() || localStorage.getItem("gemini_api_key") || "";
}

// Xử lý kéo thả và chọn file
function initDropZone() {
    const dropZone = document.getElementById("dropZone");
    const fileInput = document.getElementById("fileInput");
    const uploadBtn = document.getElementById("uploadBtn");
    const selectedFileName = document.getElementById("selectedFileName");

    ['dragenter', 'dragover'].forEach(name => {
        dropZone.addEventListener(name, (e) => {
            e.preventDefault();
            dropZone.classList.add("drag-over");
        });
    });

    ['dragleave', 'drop'].forEach(name => {
        dropZone.addEventListener(name, (e) => {
            e.preventDefault();
            dropZone.classList.remove("drag-over");
        });
    });

    dropZone.addEventListener("drop", (e) => {
        if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
            handleFileSelect(e.dataTransfer.files[0]);
        }
    });

    fileInput.addEventListener("change", (e) => {
        if (e.target.files && e.target.files.length > 0) {
            handleFileSelect(e.target.files[0]);
        }
    });

    function handleFileSelect(file) {
        selectedFile = file;
        selectedFileName.innerHTML = `<i class="bi bi-file-earmark-check text-success me-1"></i> ${file.name} (${(file.size / 1024).toFixed(1)} KB)`;
        uploadBtn.disabled = false;
        hideAlert();
    }
}

// Tải lên và sinh Embedding vào RAM
async function handleUpload() {
    if (!selectedFile) return;

    const uploadBtn = document.getElementById("uploadBtn");
    const alertBox = document.getElementById("uploadStatusAlert");
    const apiKey = getApiKey();

    uploadBtn.disabled = true;
    uploadBtn.innerHTML = `
        <span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
        <span>Đang phân tích & Embedding vào RAM...</span>
    `;

    const formData = new FormData();
    formData.append("file", selectedFile);
    if (apiKey) {
        formData.append("apiKey", apiKey);
    }

    try {
        const response = await fetch("/api/documents/upload", {
            method: "POST",
            headers: apiKey ? { "X-Gemini-Api-Key": apiKey } : {},
            body: formData
        });

        const data = await response.json();

        if (response.ok) {
            showAlert("success", `
                <strong>Thành công!</strong> Đã embedding <strong>${data.totalChunks} đoạn</strong> vào <strong>RAM</strong>.<br>
                <small class="text-muted">Các Section: ${data.detectedSections ? data.detectedSections.join(", ") : "N/A"}</small>
            `);
            fetchMemoryStatus();
        } else {
            showAlert("danger", `<strong>Lỗi:</strong> ${data.error || "Không thể tải lên file"}`);
        }
    } catch (err) {
        showAlert("danger", `<strong>Lỗi kết nối:</strong> ${err.message}`);
    } finally {
        uploadBtn.disabled = false;
        uploadBtn.innerHTML = `
            <i class="bi bi-cpu-fill"></i>
            <span>Embedding & Lưu vào RAM</span>
        `;
    }
}

// Lấy trạng thái RAM
async function fetchMemoryStatus() {
    try {
        const res = await fetch("/api/documents/status");
        if (res.ok) {
            const data = await res.json();
            document.getElementById("docCountDisplay").innerText = data.totalDocuments;
            document.getElementById("vectorCountDisplay").innerText = data.totalVectorsInRam;

            const docNamesList = document.getElementById("docNamesList");
            if (data.documentNames && data.documentNames.length > 0) {
                docNamesList.innerHTML = `<span class="fw-semibold">File nạp:</span> ${data.documentNames.map(name => `<span class="badge bg-primary-subtle text-primary me-1">${name}</span>`).join("")}`;
            } else {
                docNamesList.innerHTML = `<span class="fst-italic text-muted">Chưa có tài liệu nào trong RAM.</span>`;
            }
        }
    } catch (err) {
        console.error("Lỗi lấy trạng thái RAM:", err);
    }
}

// Xóa RAM
async function handleClearRam() {
    if (!confirm("Bạn có chắc chắn muốn xóa toàn bộ vector và tài liệu trong RAM?")) return;

    try {
        const res = await fetch("/api/documents", { method: "DELETE" });
        if (res.ok) {
            fetchMemoryStatus();
            alert("Đã làm trống bộ nhớ RAM thành công!");
        }
    } catch (err) {
        alert("Lỗi khi xóa RAM: " + err.message);
    }
}

// Gửi câu hỏi chat
async function handleChatSubmit(e) {
    e.preventDefault();
    const input = document.getElementById("chatInput");
    const question = input.value.trim();
    if (!question) return;

    input.value = "";
    await submitQuestion(question);
}

async function submitQuestion(question) {
    const input = document.getElementById("chatInput");
    const sendBtn = document.getElementById("sendBtn");

    input.disabled = true;
    sendBtn.disabled = true;

    appendUserMessage(question);
    const typingId = appendTypingIndicator();

    const apiKey = getApiKey();

    try {
        const response = await fetch("/api/chat", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                ...(apiKey ? { "X-Gemini-Api-Key": apiKey } : {})
            },
            body: JSON.stringify({
                question: question,
                apiKey: apiKey
            })
        });

        removeMessage(typingId);
        const data = await response.json();

        if (response.ok) {
            appendBotMessage(data.answer, data.citations);
        } else {
            appendBotMessage(`⚠️ **Lỗi:** ${data.error || "Không thể xử lý câu hỏi."}`, []);
        }
    } catch (err) {
        removeMessage(typingId);
        appendBotMessage(`⚠️ **Lỗi kết nối:** ${err.message}`, []);
    } finally {
        input.disabled = false;
        sendBtn.disabled = false;
        input.focus();
        scrollToBottom();
    }
}

// Append tin nhắn User
function appendUserMessage(text) {
    const container = document.getElementById("chatMessages");
    const msg = document.createElement("div");
    msg.className = "d-flex justify-content-end mb-3";
    msg.innerHTML = `
        <div class="user-bubble p-3 shadow-sm">
            ${escapeHtml(text)}
        </div>
    `;
    container.appendChild(msg);
    scrollToBottom();
}

// Append tin nhắn Bot kèm trích dẫn Section
function appendBotMessage(answerMarkdown, citations) {
    const container = document.getElementById("chatMessages");
    const msg = document.createElement("div");
    msg.className = "bot-message-wrapper mb-4";

    let citationsHtml = "";
    if (citations && citations.length > 0) {
        citationsHtml = `
            <div class="citation-box p-3 mt-3">
                <div class="fw-bold text-dark small mb-2 d-flex align-items-center gap-2">
                    <i class="bi bi-bookmark-check-fill text-primary"></i>
                    <span>Nguồn Trích Dẫn Thực Tế Từ RAM:</span>
                </div>
                <div class="d-flex flex-column gap-2">
                    ${citations.map((c) => `
                        <div class="citation-card p-2 rounded-3 small">
                            <div class="d-flex justify-content-between align-items-center mb-1">
                                <span class="section-badge">
                                    <i class="bi bi-geo-alt-fill"></i> ${escapeHtml(c.section || 'Không rõ section')}
                                </span>
                                <span class="badge score-badge border text-secondary">
                                    Trang ${c.pageNumber} • Khớp: ${(c.score * 100).toFixed(0)}%
                                </span>
                            </div>
                            <div class="text-secondary small fst-italic ps-1">
                                "${escapeHtml(c.snippet)}"
                            </div>
                        </div>
                    `).join("")}
                </div>
            </div>
        `;
    }

    const renderedAnswer = (typeof marked !== 'undefined') ? marked.parse(answerMarkdown) : escapeHtml(answerMarkdown);

    msg.innerHTML = `
        <div class="d-flex align-items-start gap-3">
            <div class="avatar-bot text-white rounded-circle d-flex align-items-center justify-content-center flex-shrink-0 shadow">
                <i class="bi bi-robot"></i>
            </div>
            <div class="bot-bubble p-3 p-md-4 rounded-4 shadow-sm flex-grow-1">
                <div class="markdown-body">${renderedAnswer}</div>
                ${citationsHtml}
            </div>
        </div>
    `;

    container.appendChild(msg);
    scrollToBottom();
}

// Typing indicator
function appendTypingIndicator() {
    const container = document.getElementById("chatMessages");
    const id = "typing-" + Date.now();
    const msg = document.createElement("div");
    msg.id = id;
    msg.className = "bot-message-wrapper mb-3";
    msg.innerHTML = `
        <div class="d-flex align-items-start gap-3">
            <div class="avatar-bot text-white rounded-circle d-flex align-items-center justify-content-center flex-shrink-0 shadow">
                <i class="bi bi-robot"></i>
            </div>
            <div class="bot-bubble p-3 rounded-4 shadow-sm">
                <div class="typing-indicator">
                    <span></span><span></span><span></span>
                </div>
            </div>
        </div>
    `;
    container.appendChild(msg);
    scrollToBottom();
    return id;
}

function removeMessage(id) {
    const el = document.getElementById(id);
    if (el) el.remove();
}

function handleClearChat() {
    const container = document.getElementById("chatMessages");
    container.innerHTML = `
        <div class="bot-message-wrapper mb-4">
            <div class="d-flex align-items-start gap-3">
                <div class="avatar-bot text-white rounded-circle d-flex align-items-center justify-content-center flex-shrink-0 shadow">
                    <i class="bi bi-robot"></i>
                </div>
                <div class="bot-bubble p-4 rounded-4 shadow-sm">
                    <p class="mb-0 text-secondary">Cuộc trò chuyện đã được làm mới. Hãy chọn câu gợi ý bên dưới hoặc nhập câu hỏi mới nhé!</p>
                </div>
            </div>
        </div>
    `;
}

function showAlert(type, htmlContent) {
    const alertBox = document.getElementById("uploadStatusAlert");
    alertBox.className = `alert alert-${type} small mb-2 py-2 px-3 rounded-3`;
    alertBox.innerHTML = htmlContent;
    alertBox.classList.remove("d-none");
}

function hideAlert() {
    document.getElementById("uploadStatusAlert").classList.add("d-none");
}

function scrollToBottom() {
    const container = document.getElementById("chatMessages");
    if (container) {
        container.scrollTo({
            top: container.scrollHeight,
            behavior: "smooth"
        });
    }
}

function escapeHtml(text) {
    if (!text) return "";
    return text
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}
