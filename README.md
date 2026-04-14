# AI 心情留言墙（像素风 H5 + Spring Boot + LangChain4j）

一个前后端一体的小项目：  
用户无需登录，在像素风留言墙写下当下心情；后端调用 LLM 生成约 50 字暖心回复并展示。  
留言数据存内存，支持标记完成/删除，重启后状态自动清空。

## 技术栈

- 后端：`Spring Boot 3.2.5`
- AI：`LangChain4j 1.0.0-beta3` + `Qwen (qwen-turbo)`
- 前端：单页 H5（静态资源由 Spring Boot 直接托管）
- 存储：内存 `ConcurrentHashMap`

## 一步步启动项目

### 1) 准备环境

- JDK `17+`
- Maven `3.9+`

### 2) 拉起项目并安装依赖

在项目根目录执行：

```bash
mvn clean package
```

### 3) 修改 `application.yml` 的 Key

文件路径：`src/main/resources/application.yml`

把下面配置中的 key 改成你自己的 DashScope Key（建议使用环境变量，不要把真实 key 提交到仓库）：

```yaml
langchain4j:
  qwen:
    api-key: your-api-key
    model-name: qwen-turbo
    max-tokens: 1024
    temperature: 0.5
```

### 4) 启动服务

方式一（推荐）：

```bash
mvn spring-boot:run
```

方式二：在 IDE 里运行启动类 `AiBoardApplication`。

### 5) 本地访问

启动成功后浏览器打开：

- [http://localhost:8080](http://localhost:8080)

## 项目亮点

- **像素风游戏化视觉**
  - 中央留言墙、错落便签、顶部双黄灯打光、扫描线氛围。
- **无登录、即开即用**
  - 前端本地生成 `visitorId`，无需账号体系即可记录“我的留言”。
- **AI 暖心回复**
  - 每次提交心情，后端调用 LLM 自动生成温暖附言（约 50 字）。
- **交互细节丰富**
  - 悬停便签出现操作按钮（完成/删除）、完成后盖章、`BINGO` 动效反馈。
- **完成态规则清晰**
  - 已完成留言不可删除、不可重复完成，规则由后端保证。
- **默认数据开箱即用**
  - 系统启动自动加载 `default.json` 作为初始留言模板。
- **状态仅内存存储**
  - 重启后自动恢复初始状态，适合演示和快速迭代。


后续扩展思路：
- 数据持久化（MySQL/Redis）
- 多人共享留言墙（全局墙）
- 对条目类型/计划类型的支持（便签中todo项的状态管理）
