const state = {
  extractedResume: "",
  currentResume: "",
  jd: "",
  firstChanges: [],
  secondChanges: []
};

const $ = (id) => document.getElementById(id);
const toast = $("toast");

function showToast(text) {
  toast.textContent = text;
  toast.classList.add("show");
  window.clearTimeout(showToast.timer);
  showToast.timer = window.setTimeout(() => toast.classList.remove("show"), 1800);
}

function setBusy(button, busy, text) {
  if (!button) return;
  if (busy) {
    button.dataset.oldText = button.textContent;
    button.textContent = text;
    button.disabled = true;
  } else {
    button.textContent = button.dataset.oldText || button.textContent;
    button.disabled = false;
  }
}

function activateStep(index) {
  ["step1", "step2", "step3"].forEach((id, i) => $(id).classList.toggle("active", i + 1 === index));
}

function escapeHtml(value) {
  return String(value || "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function applyChanges(base, changes) {
  let text = base || "";
  for (const change of changes || []) {
    const before = change.beforeText || "";
    const after = change.afterText || "";
    if (!after.trim()) continue;
    if (before && text.includes(before)) {
      text = text.replace(before, after);
    } else if (!text.includes(after)) {
      text = `${text.trim()}\n\n${after.trim()}`.trim();
    }
  }
  return text.trim();
}

function renderFirstChanges(changes) {
  $("firstChanges").innerHTML = changes.map((change, index) => `
    <article class="change" data-index="${index}">
      <div>
        <h3>${escapeHtml(change.section || "简历内容")}</h3>
        <div class="before">${escapeHtml(change.beforeText || "原简历中没有直接对应片段，将作为新增建议。")}</div>
      </div>
      <div>
        <h3>可编辑的新表达</h3>
        <textarea data-role="after">${escapeHtml(change.afterText || "")}</textarea>
        <p style="color:var(--muted);line-height:1.65;margin:8px 0 0">${escapeHtml(change.reason || "")}</p>
      </div>
    </article>
  `).join("");
}

function renderCritic(report) {
  $("scoreGrid").innerHTML = `
    <div class="score"><span>JD 匹配度</span><strong>${report.jdMatchScore ?? 0}</strong></div>
    <div class="score"><span>简历完整度</span><strong>${report.completenessScore ?? 0}</strong></div>
  `;
  $("criticLists").innerHTML = `
    <div class="panel" style="background:#fff;border:1px solid var(--line);border-radius:var(--radius)">
      <h3>优势</h3>
      <ul class="list">${(report.strengths || []).map(item => `<li>${escapeHtml(item)}</li>`).join("")}</ul>
    </div>
    <div class="panel" style="background:#fff;border:1px solid var(--line);border-radius:var(--radius)">
      <h3>风险与缺失</h3>
      <ul class="list">${[...(report.risks || []), ...(report.missingItems || [])].map(item => `<li>${escapeHtml(item)}</li>`).join("")}</ul>
    </div>
  `;
}

function renderSecondChanges(changes) {
  $("secondChanges").innerHTML = changes.map((change, index) => `
    <article class="change full" data-index="${index}">
      <input type="checkbox" checked aria-label="保留该改动">
      <div>
        <h3>${escapeHtml(change.section || "二次优化")}</h3>
        <div class="before">${escapeHtml(change.beforeText || "新增建议")}</div>
        <div class="after" style="margin-top:8px">${escapeHtml(change.afterText || "")}</div>
        <p style="color:var(--muted);line-height:1.65;margin:8px 0 0">${escapeHtml(change.reason || "")}</p>
      </div>
    </article>
  `).join("");
}

async function readError(response) {
  const text = await response.text();
  return text || `请求失败：${response.status}`;
}

function showThinkingPanel(show) {
  const panel = $("thinkingPanel");
  const content = $("thinkingContent");
  if (show) {
    content.textContent = "";
    panel.hidden = false;
  } else {
    panel.hidden = true;
    content.textContent = "";
  }
}

function appendThinking(text) {
  const content = $("thinkingContent");
  content.textContent += text;
  content.scrollTop = content.scrollHeight;
}

async function consumeSse(response, handlers) {
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let eventName = "message";
  let dataLines = [];

  const dispatch = () => {
    if (!dataLines.length) return;
    const data = dataLines.join("\n");
    dataLines = [];
    if (eventName === "thinking") {
      handlers.onThinking?.(data);
    } else if (eventName === "done") {
      handlers.onDone?.(data);
    } else if (eventName === "error") {
      handlers.onError?.(data);
    }
    eventName = "message";
  };

  while (true) {
    const { done, value } = await reader.read();
    if (done) {
      dispatch();
      break;
    }
    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split("\n");
    buffer = lines.pop() || "";
    for (const line of lines) {
      if (line.startsWith("event:")) {
        dispatch();
        eventName = line.slice(6).trim();
      } else if (line.startsWith("data:")) {
        dataLines.push(line.slice(5).trimStart());
      } else if (line === "") {
        dispatch();
      }
    }
  }
}

function applyInitialResult(data, jd) {
  state.jd = jd;
  state.extractedResume = data.extractedResume || "";
  state.currentResume = data.rewrittenResume || state.extractedResume;
  state.firstChanges = data.changes || [];
  $("resumePreview").value = state.currentResume;
  renderFirstChanges(state.firstChanges);
  $("firstPanel").style.display = "block";
  $("resumePanel").style.display = "block";
  activateStep(2);
  showToast("一轮改动已生成");
}

$("resumeFile").addEventListener("change", () => {
  const file = $("resumeFile").files[0];
  if (!file) return;
  $("uploadBox").classList.add("selected");
  $("fileName").textContent = file.name;
  $("fileHint").textContent = "文件已选择，可以开始生成";
  showToast("简历文件已选择");
});

$("sampleJd").addEventListener("click", () => {
  $("jdText").value = "我们正在招聘 Java 后端 / AI 应用开发工程师，负责基于 Spring Boot 3、LangChain4j 和大模型能力建设企业级智能应用。要求熟悉 Java、REST API、文件解析、Prompt 设计、Agent 工作流，有简历解析、知识库问答、招聘或 HR SaaS 项目经验优先。希望候选人能独立完成需求拆解、后端接口设计、前端联调和上线问题排查。";
  showToast("已填入示例 JD");
});

$("startBtn").addEventListener("click", async () => {
  const file = $("resumeFile").files[0];
  const jd = $("jdText").value.trim();
  if (!file) return showToast("请先上传简历文件");
  if (!jd) return showToast("请填写岗位 JD");

  const form = new FormData();
  form.append("resume", file);
  form.append("jd", jd);
  setBusy($("startBtn"), true, "生成中...");
  showThinkingPanel(true);
  try {
    const response = await fetch("/api/resume/jd/initial/stream", { method: "POST", body: form });
    if (!response.ok) throw new Error(await readError(response));
    await consumeSse(response, {
      onThinking: appendThinking,
      onDone: (payload) => {
        showThinkingPanel(false);
        applyInitialResult(JSON.parse(payload), jd);
      },
      onError: (message) => {
        throw new Error(message || "生成失败");
      }
    });
  } catch (error) {
    showThinkingPanel(false);
    showToast(error.message || "生成失败");
  } finally {
    setBusy($("startBtn"), false);
  }
});

$("showExtracted").addEventListener("click", () => {
  $("resumePreview").value = state.extractedResume || "";
  $("resumePreview").focus();
  showToast("已切换为抽取文本");
});

$("confirmFirst").addEventListener("click", async () => {
  const edited = Array.from(document.querySelectorAll("#firstChanges .change")).map((el) => {
    const index = Number(el.dataset.index);
    const original = state.firstChanges[index];
    return { ...original, afterText: el.querySelector('[data-role="after"]').value };
  });
  state.firstChanges = edited;
  state.currentResume = applyChanges(state.extractedResume, edited);
  $("resumePreview").value = state.currentResume;

  setBusy($("confirmFirst"), true, "评估中...");
  try {
    const response = await fetch("/api/resume/jd/review", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        jd: state.jd,
        currentResume: state.currentResume,
        confirmedChanges: edited
      })
    });
    if (!response.ok) throw new Error(await readError(response));
    const data = await response.json();
    state.secondChanges = data.changes || [];
    state.currentResume = data.rewrittenResume || state.currentResume;
    $("resumePreview").value = state.currentResume;
    renderCritic(data.criticReport || {});
    renderSecondChanges(state.secondChanges);
    $("reviewPanel").style.display = "block";
    activateStep(3);
    showToast("Critic 评估与二次生成已完成");
  } catch (error) {
    showToast(error.message || "评估失败");
  } finally {
    setBusy($("confirmFirst"), false);
  }
});

$("exportBtn").addEventListener("click", async () => {
  const acceptedChanges = Array.from(document.querySelectorAll("#secondChanges .change"))
    .filter((el) => el.querySelector('input[type="checkbox"]').checked)
    .map((el) => state.secondChanges[Number(el.dataset.index)]);
  setBusy($("exportBtn"), true, "导出中...");
  try {
    const response = await fetch("/api/resume/jd/export", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        resumeText: $("resumePreview").value,
        acceptedChanges
      })
    });
    if (!response.ok) throw new Error(await readError(response));
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "resume-agent-output.docx";
    link.click();
    URL.revokeObjectURL(url);
    showToast("新简历已导出");
  } catch (error) {
    showToast(error.message || "导出失败");
  } finally {
    setBusy($("exportBtn"), false);
  }
});
