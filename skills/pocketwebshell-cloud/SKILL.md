---
name: pocketwebshell-cloud
description: 仅用于 AIMFllyYS/PocketWebShell。用户要求启动 PocketWebShell、Web pocket、配置其云端环境或在云端修改该项目时使用。初始化 Android 环境，从 origin/main 创建工作分支，验证后提交 PR 到 dev；真机最终验收、正式签名、Release、官网更新和 SSH 部署交给用户本地完成。
---

# PocketWebShell 云端工作流程

## 固定约定

- 仓库：`https://github.com/AIMFllyYS/PocketWebShell`。用户明确给出另一个仓库时，先确认是否仍是此项目；不自动套用到其他项目。
- 云端负责环境初始化、实现、可执行的验证和源码 PR。用户本地负责最终端侧验收、合并决策、正式签名与发布、官网同步和 SSH 部署。
- 每次改动从最新 `origin/main` 切出独立分支，PR 的目标是 `dev`。不自动合并，不直接推送 `main` / `dev`。
- 默认保持应用版本号，不构建正式 Release、不打发布 tag、不更新官网。云端 Debug 构建用于验证；需要交付试装包时按仓库规范归档。
- 签名与 SSH 凭据在用户本地。不要再次索要这些凭据，不生成替代正式签名，不把发布失败作为继续提交源码 PR 的障碍。

## 1. 启动与初始化

1. 简短告知正在使用本 Skill。确定仓库和工作目录，检查已有克隆的 remote、分支、未提交改动；保护已有工作，必要时使用独立 worktree。
2. 在托管云环境先读取可用的云运行说明，检查网络、代理和凭据就绪状态。使用现有 GitHub 连接 / CLI 验证访问，不打印 token 或完整环境变量；仅在实际无访问权限时说明缺失项。
3. 缺少克隆就克隆上述仓库；已有克隆先 `git fetch origin`。读取仓库根目录及作用路径下的 `AGENTS.md`，依任务读取相关技术文档。
4. 按 [环境初始化附页](references/environment.md) 复用或安装 JDK、Android SDK，配置忽略提交的 `local.properties`，保留云代理与 CA 信任，使用仓库 Gradle Wrapper。
5. 仅要求「启动项目」时，到环境检查和 Debug 基线构建完成即可，报告就绪状态，不自行添加功能或创建空 PR。收到具体改动后再执行下一节。

## 2. 修改与验证

1. 从最新 `origin/main` 创建 `feat/`、`fix/`、`refactor/`、`test/` 或 `docs/` 分支；不要把上一次任务的提交带入。示例：`git switch -c fix/<topic> origin/main`。有未提交工作时在独立 worktree 操作。
2. 实现请求并遵守仓库架构。更新相关文档和必要回归测试；版本号以当前源码为准，不从历史聊天推测。
3. 按 `AGENTS.md` 的测试矩阵验证。应用代码改动通常执行 `./gradlew testDebugUnitTest :app:assembleDebug`；仪器测试先编译测试包，有可用设备再实际运行。纯文档改动验证路径和命令即可。
4. `adb devices` 没有设备、模拟器未完成启动、缺少 KVM 等情况必须如实记录，不能把测试包编译成功写成端侧测试通过。避免无期限等待软件模拟器；厂商兼容问题保留给用户真机确认。
5. 子智能体只在用户或适用规范要求时使用；用户要求便宜模型时按要求选用可用模型。委派审查也不等于真机验收。

## 3. 提交 PR 与本地交接

1. 检查 `git status`、暂存 diff 和 `git diff --check`，确认只包含本次源码 / 文档，没有密钥、环境配置或生成产物。使用 Conventional Commit，推送工作分支。
2. 创建 PR，`base=dev`，`head=本次分支`。提交前检查相对 `origin/dev` 的 diff；遇到分支分歧，不删除无关改动或重写共享历史。需要真机验收或有未完成检查时创建草稿 PR。
3. PR 描述写清问题、改动、实际执行的检查和结果、未完成的端侧项目，以及本地检出 / 测试方式。核对远端 PR 的分支、状态和 CI，不因创建成功而宣称所有检查通过。
4. 最终交付 PR 链接、分支、验证结果和本地验收清单。到源码交接结束；不重复询问是否可以发布，也不自动继续合并或部署。
5. 用户本地后续按仓库 `docs/VERSIONING.md`、`docs/RELEASE.md`、`docs/WEBSITE.md` 和 `docs/OPS.md` 完成同号 Debug 真机验收、版本与更新日志同步、发布 PR、原签名构建与校验、GitHub Release，以及独立官网项目 `../PocketWebShell-site/` 的更新、SSH 部署和线上验收。只记录该交接，不在云端执行。

## 长期使用

仓库内由 `AGENTS.md` 引导读取本文件。需要在克隆前通过「启动 PocketWebShell」触发时，将整个 `pocketwebshell-cloud` 目录安装到支持 Agent Skills 的工具个人目录，例如 Codex 的 `~/.agents/skills/`，重新开启会话。安装与云会话持久化由平台决定；不能仅凭文件已写入就宣称已保存到账号或所有未来云环境。

本 Skill 保存工作约定，不保存某次白边问题的根因猜测、固定版本号、测试数量、机器路径或凭据。用户后续明确修改约定时，以最新要求为准，并同步更新本 Skill。
