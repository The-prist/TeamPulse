# 图源码说明（可导入 UML 工具自行修改）

9 张图各提供两种源码：

| 格式 | 扩展名 | 适合 |
|---|---|---|
| PlantUML | `.puml` | StarUML / IntelliJ IDEA / VS Code 插件 / plantuml.com；真正的 UML 语义 |
| Mermaid | `.mmd` | Typora / Obsidian / mermaid.live / GitHub Markdown 直接渲染 |

## 怎么用

1. **只看不改**：把 `.mmd` 粘到 https://mermaid.live ，或把 `.puml` 粘到 https://www.plantuml.com/plantuml
2. **导入星型 UML（StarUML）**：装 PlantUML 扩展后打开 `.puml`；或用 draw.io 的 "Arrange → Insert → From Text"（PlantUML）导入
3. **本地渲染**：VS Code 装 `PlantUML` 插件，`Alt+D` 预览、导出 PNG/SVG
4. **改完导图**：导出 2x PNG（宽 ≥1500px）替换 `docs/diagrams/v2/` 下的同名文件，再跑 `python scripts/docs/make_atlas_docx.py` 重生成图册

## 文件对应

| 文件 | 图 | 类型 |
|---|---|---|
| fig1-architecture | 图 1 系统架构图 | 组件图 / 部署视图 |
| fig2-deployment | 图 2 部署图 | UML 部署图 |
| fig3-business-flow | 图 3 业务流程图 | 活动图（泳道） |
| fig4-use-case | 图 4 用例图 | UML 用例图（6 角色 × 15 用例） |
| fig5-sequence | 图 5 时序图 | UML 时序图 |
| fig6-state | 图 6 状态图 | UML 状态机（补卡 + 设备） |
| fig7-class | 图 7 类图 | UML 类图（14 个核心类） |
| fig8-component | 图 8 组件图 | UML 组件图（前端 41 / 后端 116 文件） |
| fig9-data-model | 图 9 数据模型图 | ER 图（21 表核心关系） |

## 内容与代码一致

图中的表数、类名、字段、接口数量、权限点、端口、容器名均取自当前代码：
21 表 / 6 角色 / 22 权限点 / 90 接口 / 16 控制器 / 34 处 @PreAuthorize / 37 页面 / 5 容器。
改动代码后请同步改源码，再重新导图。