# Resume Agent Demo

一个基于 `Spring Boot 3`、`LangChain4j` 和 Qwen 模型的简历 Agent 演示项目。

当前版本保留原技术框架，移除了旧的心情留言板业务，先实现第一个场景：上传简历后，根据目标岗位 JD 生成定向优化的新简历。

## 技术栈

- 后端：`Spring Boot 3.2.5`
- AI：`LangChain4j 1.0.0-beta3` + `Qwen (qwen-turbo)`
- 文件解析：`Apache POI`、`PDFBox`
- 前端：Spring Boot 托管的多页静态 H5

## 当前功能

- 上传 `doc`、`docx`、`pdf` 简历并抽取文本。
- 粘贴岗位 JD 后交给 Writer Agent 生成一轮改动点。
- 一轮改动会展示“原文片段 -> 新表达”，用户可以编辑。
- 用户确认后交给 Critic Agent 评估：
  - 是否符合 JD 要求。
  - 简历是否完整。
- Critic 结果再交给 Writer Agent 二次生成。
- 二次改动不允许编辑，只允许选择是否保留。
- 根据最终内容导出 `resume-agent-output.docx`。

第二个场景“通过智能追问更新旧简历”目前只做前端页面占位，后端暂未实现。

## 启动项目

### 1. 准备环境

- JDK `17+`
- Maven `3.9+`

### 2. 配置 Qwen

修改 `src/main/resources/application.yml`：

```yaml
langchain4j:
  qwen:
    api-key: your-api-key
    model-name: qwen-turbo
    max-tokens: 4096
    temperature: 0.5
```

### 3. 启动

```bash
mvn spring-boot:run
```

浏览器访问：

- [http://localhost:8080](http://localhost:8080)
- [http://localhost:8080/jd.html](http://localhost:8080/jd.html)

## 接口

- `POST /api/resume/jd/initial`
  - `multipart/form-data`
  - 字段：`resume`、`jd`
  - 返回：抽取文本、Writer 一轮完整简历、可编辑改动点。
- `POST /api/resume/jd/review`
  - `application/json`
  - 字段：`jd`、`currentResume`、`confirmedChanges`
  - 返回：Critic 评估结果、Writer 二次完整简历、不可编辑改动点。
- `POST /api/resume/jd/export`
  - `application/json`
  - 字段：`resumeText`、`acceptedChanges`
  - 返回：`docx` 文件。
