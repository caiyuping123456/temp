# DOCX 文档合并服务

Spring Boot + docx4j 实现的 DOCX 合并示例。程序从副文档中提取 `${name-start}` 与 `${name-end}` 之间的内容，按上传顺序插入主文档的 `${merge-point}` 位置，并生成目录域。

## 环境

- JDK 21
- Maven 3.9+

## 运行

```bash
mvn test
mvn spring-boot:run
```

启动后访问 `http://localhost:8080/`。

也可以先打包：

```bash
mvn -DskipTests package
java -jar target/doc-merge-demo-1.0.0.jar
```

## 合并规则

- 标识格式为 `${name-start}` 和 `${name-end}`，名称支持字母、数字和下划线，区分大小写。
- 标识可以出现在不同段落中，也可以被 Word 拆分到多个 run。
- 只保留成对标识之间的内容。缺少开始或结束标识时返回警告，并跳过该文档。
- 主文档中的 `${merge-point}` 指定插入位置，未提供时插入正文末尾。
- `${toc}` 指定目录位置，未提供时目录放在文档开头。
- 默认在每个副文档之间插入分页符，可通过 `app.merge.page-break-between` 关闭。
- 副文档标题降为二级，原有 `Heading 1..9` 依次降一级并生成对应样式。
- 标题默认生成 `1 / 1.1 / 1.1.1` 多级编号。

## 接口

| 方法 | 地址 | 说明 |
| --- | --- | --- |
| GET | `/api/health` | 健康检查 |
| POST | `/api/documents/inspect` | 检查 DOCX 中的标识 |
| POST | `/api/documents/merge` | 上传主文档和副文档并合并 |
| GET | `/api/documents/download/{fileId}` | 下载合并结果 |
| DELETE | `/api/documents/files/{fileId}` | 删除临时文件 |

合并接口使用 `multipart/form-data`：

- `mainFile`：主文档，单个 DOCX。
- `subFiles`：副文档，可传多个，按表单顺序合并。
- `outputName`：输出文件名，可选。

## 示例文件

`samples/` 目录包含：

- `主文档.docx`：项目概述、目录标记、合并位置和附录。
- `副文档-产品介绍.docx`：检测范围与检测对象。
- `副文档-实施计划.docx`：现场计划和注意事项。
- `副文档-异常标记.docx`：故意缺少结束标识，用于观察警告。
- `合并结果示例.docx`：合并后的示例文件。

重新生成示例：

```bash
python samples/generate_samples.py
```

服务启动后可运行冒烟测试：

```bash
python samples/smoke_test.py
```

## 目录结构

```text
后端/
├─ pom.xml
├─ samples/
└─ src/
   ├─ main/
   │  ├─ java/com/example/docmerge/
   │  │  ├─ config/
   │  │  ├─ controller/
   │  │  ├─ dto/
   │  │  ├─ exception/
   │  │  └─ service/
   │  └─ resources/
   │     ├─ application.yml
   │     └─ static/
   └─ test/
```

## 配置

`src/main/resources/application.yml` 主要配置：

```yaml
app:
  storage:
    dir: ${java.io.tmpdir}/docx-merge
    retention-hours: 2
  merge:
    page-break-between: true
    toc-title: 目录
    toc-depth: 3
    heading-title-source: filename
    number-headings: true
```

## 错误码

| HTTP | code | 场景 |
| --- | --- | --- |
| 400 | `40001` | 文件为空、文档无法解析等 |
| 400 | `40002` | 缺少必填参数 |
| 400 | `40003` | multipart 请求格式错误 |
| 404 | `40400` | 临时文件不存在或已过期 |
| 413 | `41300` | 文件超过大小限制 |
| 415 | `41500` | 文件不是 `.docx` |
| 500 | `50000` | 未处理的服务端异常 |

## 限制

- 仅支持 `.docx`，不支持 `.doc`、`.docm` 和 PDF。
- 标识只在正文顶层段落中解析，不处理表格单元格、页眉页脚、脚注和文本框中的标识。
- 页眉页脚、页面设置、分节符、批注、脚注和尾注不会随副文档复制。
- 图表、SmartArt、OLE 对象和内容控件不保证完整复制。
- TOC 为 Word 域，页码需要 Word 更新域后显示。打开文件后按 `Ctrl+A`、`F9` 更新。
- 临时文件保存在本机磁盘，适合单机示例。生产环境应使用对象存储并增加鉴权、配额和病毒扫描。
