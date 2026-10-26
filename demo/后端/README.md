# Word 文档合并 Demo（Spring Boot + docx4j）

基于标记区域（marker region）的 DOCX 合并服务。上传一个主文档和多个副文档，服务会从副文档中提取
`${name-start}` 与 `${name-end}` 之间的内容，按上传顺序插入主文档的 `${merge-point}` 位置，
同时生成 Word 原生目录（TOC），最后通过临时下载接口返回合并结果。

- 后端：Java 21 + Spring Boot 3.3.5 + docx4j 11.5.14
- 构建：Maven
- 前端：`src/main/resources/static` 内置一个无需构建的单页示例，启动后访问 <http://localhost:8080/>
- 包名：`com.example.docmerge`

---

## 1. 功能特性

| 能力 | 说明 |
| --- | --- |
| 标记区域提取 | 支持 `${name-start}` / `${name-end}`，名称由字母、数字、下划线组成，区分大小写 |
| 跨 run 识别 | Word 把一个标记拆成多个 `w:r` / `w:t` 时仍能正确识别 |
| 跨段落提取 | 开始、结束标记可以位于不同段落，区域内容按文档顺序完整提取 |
| 同段区域 | 开始、结束标记位于同一段落时，只保留两者之间的内容 |
| 精确删除 | 只删除标记本身和区域外文本，保留段落属性、字符格式、超链接等结构 |
| 格式保留 | 表格、图片、超链接、粗体/斜体、样式、编号列表随内容一起复制 |
| 多级标题 | 副文档标题自动降为二级，源文档 `Heading 1..9` 依次降一级，并生成派生样式 |
| 标题编号 | 默认生成 `1 / 1.1 / 1.1.1` 形式的多级编号，可通过配置关闭 |
| 原生目录 | 在 `${toc}` 位置插入 TOC 域，并写入 `updateFields=true` |
| 合并位置 | 在 `${merge-point}` 处插入内容；没有该标记时追加到正文末尾 |
| 异常提示 | 缺少结束/开始标记、重复开始、区域嵌套时返回 `warnings`，不会中断整个合并 |
| 临时下载 | 合并结果保存为临时文件，通过 UUID `fileId` 下载或删除，支持过期清理 |
| 安全防护 | 校验 `fileId` 为 UUID，规范化路径并阻止路径穿越 |
| 统一错误 | 所有业务异常返回统一 JSON，不暴露服务端堆栈和路径 |
| 测试 | 标记解析单元测试 + 基于 MockMvc 的端到端合并测试 |

---

## 2. 环境要求

- JDK 21
- Maven 3.9+
- 无需安装 Microsoft Word；生成的 DOCX 可以用 Word、WPS 或 LibreOffice 打开

确认环境：

```bash
java -version
mvn -version
```

---

## 3. 目录结构

```text
demo/backend/
├─ pom.xml
├─ README.md
├─ samples/                         # 示例 DOCX
│  ├─ main.docx                     # 简单示例：主文档
│  ├─ sub1.docx                     # 简单示例：副文档 1（标题/表格/图片/超链接/编号）
│  ├─ sub2.docx                     # 简单示例：副文档 2
│  ├─ 主文档.docx                    # 内容更丰富的中文示例
│  ├─ 副文档-产品介绍.docx
│  ├─ 副文档-实施计划.docx
│  ├─ 副文档-异常标记.docx           # 故意缺少结束标记，用于观察警告
│  ├─ 合并结果示例.docx              # 由 smoke_test.py 实际合并生成的结果
│  ├─ generate_samples.py           # 生成丰富示例（可选，需要 python-docx/Pillow）
│  └─ smoke_test.py                 # 端到端冒烟脚本（可选，需要 requests/python-docx）
└─ src/
   ├─ main/
   │  ├─ java/com/example/docmerge/
   │  │  ├─ DocMergeApplication.java
   │  │  ├─ config/                 # 配置属性、Web 配置
   │  │  ├─ controller/             # REST 接口
   │  │  ├─ dto/                    # 统一响应、合并结果、检查结果
   │  │  ├─ exception/              # 业务异常与全局异常处理
   │  │  └─ service/
   │  │     ├─ MarkerParser.java        # 标记解析、配对、切片、删除
   │  │     ├─ DocxContentCopier.java   # 内容/样式/编号/关系复制与标题降级
   │  │     ├─ TocService.java          # 目录域生成
   │  │     ├─ DocxMergeService.java    # 合并流程编排
   │  │     └─ TempFileStore.java       # 临时文件、sidecar、定时清理
   │  └─ resources/
   │     ├─ application.yml
   │     └─ static/                 # 内置演示页面（HTML/CSS/JS）
   └─ test/
      └─ java/com/example/docmerge/
         ├─ DocumentMergeIntegrationTest.java
         ├─ service/MarkerParserTest.java
         └─ support/TestDocuments.java
```

---

## 4. 快速开始

### 4.1 构建并启动

在 `demo/backend` 目录执行：

```bash
mvn -q -DskipTests package
java -jar target/doc-merge-demo-1.0.0.jar
```

启动后：

- 演示页面：<http://localhost:8080/>
- 健康检查：<http://localhost:8080/api/health>
- 服务端口：`8080`（可在 `application.yml` 中修改）

Windows PowerShell 5.1 如果看到中文日志乱码，可以先执行：

```powershell
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
```

这只是控制台显示问题，不影响 HTTP 接口和生成的文件内容。

### 4.2 使用示例文档

`samples/main.docx` 中已经包含：

```text
首页标题
${toc}
说明文字
${merge-point}
结尾文字
```

`samples/sub1.docx`、`samples/sub2.docx` 分别包含成对的标记区域，其中 `sub1.docx` 还包含表格、
图片、超链接和编号列表。直接上传这三个文件即可体验完整流程。

`samples` 下另外提供了一套内容更丰富的中文示例，可以使用：

```bash
python samples/smoke_test.py
```

该脚本会自动调用合并、下载接口，并校验目录域、图片、表格、标记清理等结果。运行前需要先启动服务，
并安装 `requests` 与 `python-docx`：

```bash
pip install requests python-docx
```

---

## 5. 标记规则

### 5.1 标记一览

| 标记 | 位置 | 作用 |
| --- | --- | --- |
| `${name-start}` | 副文档正文段落 | 区域开始，`name` 为 `[A-Za-z0-9_]+`，区分大小写 |
| `${name-end}` | 副文档正文段落 | 区域结束，必须与开始标记同名 |
| `${merge-point}` | 主文档正文段落 | 合并内容插入到该位置 |
| `${toc}` | 主文档正文段落 | 目录插入到该位置 |

### 5.2 副文档示例

```text
这段在标记之外，不会出现在合并结果中。
${design-start}本节从标记之后开始：
这里是正文，可以包含表格、图片、列表和任意样式。
${design-end}
这段也不应出现。
```

合并结果只包含 `本节从标记之后开始：` 到 `本节结束` 之前的内容，开始/结束标记本身会被删除。

### 5.3 解析规则

1. 标记名称只允许字母、数字、下划线，并且区分大小写：`${A-start}` 与 `${a-start}` 是两个不同的区域。
2. 一个副文档可以包含多对标记，所有成对区域都会按文档顺序复制。
3. 标记可以跨多个 run，也可以跨多个段落；解析时先拼接段落文本，再按字符偏移回写。
4. 开始和结束标记在同一段落时，只保留两者之间的文本。
5. 每个 `start` 与它之后最近的同名 `end` 配对。
6. 缺少 `end`、缺少 `start`、重复 `start`、区域嵌套/重叠时会产生 `warnings`，有问题的区域会被跳过，
   其余区域仍会正常合并。
7. 标记必须位于正文顶层段落中。表格单元格、页眉页脚、脚注、文本框中的标记不会被解析。
8. `${merge-point}` 和 `${toc}` 只在主文档中生效；出现多次时只有第一个位置生效，其余标记会被删除（部分重复情况会给出警告）。

> 建议：在 Word 中直接输入标记时关闭“自动更正”的智能引号，确保使用的是英文 `$`、`{`、`}` 和 `-`。

---

## 6. 合并流程

```text
校验主文档/副文档（.docx、非空、≤50MB）
        │
        ▼
加载主文档 → 补齐样式/标题样式 → 生成多级标题编号
        │
        ▼
定位并删除 ${toc} → 插入“目录”标题 + TOC 域 + 分页符
        │
        ▼
按上传顺序处理每个副文档：
  解析标记 → 收集警告 → 提取所有成对区域
  → 复制内容/表格/图片/超链接/编号/样式
  → 标题降一级并生成 MergeH{level}_{baseStyle}
  → 插入副文档标题（默认取文件名）
  → 副文档之间插入分页符
        │
        ▼
定位并删除 ${merge-point} → 在原位插入全部合并内容
        │
        ▼
保存到临时目录 → 写入 sidecar 元数据 → 返回 fileId / 下载地址 / 警告
```

### 标题与目录说明

- 每个副文档会先插入一个 `Heading1` 标题，默认使用去掉 `.docx` 后的文件名。
  可通过 `app.merge.heading-title-source=tag` 改为使用第一个标记名。
- 副文档内部的 `Heading 1` 会降为二级，`Heading 2` 降为三级，以此类推，避免与主文档标题冲突。
- 降级后的标题使用派生样式，例如 `MergeH2_Heading1`、`MergeH3_Heading2`，原始字体、字号、颜色等格式会保留。
- TOC 域默认深度为 3，即显示 1、1.1、1.1.1 三级标题。
- 目录是 Word 原生域，但页码不会在服务端预先计算。Word 打开文档时会提示更新域；
  如果没有提示，按 `Ctrl+A` 后按 `F9` 即可刷新目录和页码。

---

## 7. HTTP API

基础地址：`http://localhost:8080`

所有 JSON 接口使用统一响应体：

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

- `code = 0` 表示成功，非 0 表示失败。
- 失败时 `data` 通常省略，`message` 为可直接展示给用户的中文提示。

### 7.1 合并文档

```http
POST /api/documents/merge
Content-Type: multipart/form-data
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `mainFile` | file | 是 | 主文档，必须是 `.docx` |
| `subFiles` | file（可重复） | 是 | 副文档，至少一个；多个文件使用相同字段名 |
| `outputName` | text | 否 | 输出文件名，可带或不带 `.docx`；缺省为 `主文档名-merged.docx` |

成功响应示例：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "fileId": "a726246b-bbbb-4535-9e84-28cd06ee7f54",
    "fileName": "演示合并结果.docx",
    "size": 17792,
    "downloadUrl": "/api/documents/download/a726246b-bbbb-4535-9e84-28cd06ee7f54",
    "warnings": [],
    "tags": [
      {
        "name": "design",
        "status": "PAIRED",
        "source": "sub1.docx",
        "startParagraph": 1,
        "endParagraph": 10,
        "preview": "${design-start}本节从标记之后开始：",
        "message": "已配对，可参与合并"
      }
    ]
  }
}
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `fileId` | 临时文件 ID（UUID），用于下载和删除 |
| `fileName` | 规范化后的下载文件名 |
| `size` | 文件字节数 |
| `downloadUrl` | 相对下载地址 |
| `warnings` | 合并过程中的非致命问题；空数组表示没有警告 |
| `tags` | 所有副文档的标记解析结果，`source` 为来源文件名 |

`warnings` 常见内容：

- `副文档 [x.docx] 未找到成对的 ${name-start}/${name-end} 标识，已跳过该文档`
- `标识 [name] 缺少 end，已跳过该区域`
- `标识 [name] 的 end 没有对应的 start，已跳过`
- `标识 [name] 出现重复 start`
- `标识 [name] 与其他标识区域重叠（嵌套），已跳过该区域`
- `主文档中存在多个 ${merge-point}，仅第一个生效，其余已删除`

### 7.2 下载合并结果

```http
GET /api/documents/download/{fileId}
```

成功时返回 `200` 和 DOCX 二进制流：

```http
Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document
Content-Disposition: attachment; filename="merged.docx"; filename*=UTF-8''%E6%BC%94%E7%A4%BA%E5%90%88%E5%B9%B6%E7%BB%93%E6%9E%9C.docx
Cache-Control: no-store
```

中文文件名使用 RFC 5987 的 `filename*=UTF-8''...` 形式，浏览器会正确还原。
`fileId` 不是合法 UUID、文件不存在或已过期时返回 `404`：

```json
{"code":40400,"message":"文件不存在或已过期，请重新合并"}
```

### 7.3 删除临时文件

```http
DELETE /api/documents/files/{fileId}
```

```json
{"code":0,"message":"已删除"}
```

删除后再次下载会返回 `404`。服务每小时清理一次超过保留时间的临时文件，启动时也会清理一次。

### 7.4 检查文档标记

```http
POST /api/documents/inspect
Content-Type: multipart/form-data
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `file` | file | 是 | 要检查的 `.docx`；为兼容前端也接受 `mainFile` |

成功响应示例：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "fileName": "sub1.docx",
    "paragraphCount": 13,
    "tagCount": 6,
    "tags": [
      {
        "name": "design",
        "status": "PAIRED",
        "source": "sub1.docx",
        "startParagraph": 1,
        "endParagraph": 10,
        "preview": "${design-start}本节从标记之后开始：",
        "message": "已配对，可参与合并"
      }
    ],
    "warnings": []
  }
}
```

- `tagCount` 是标记**出现次数**（一个成对区域算 2 次），`tags` 中则是区域或问题条目。
- `status` 可能为 `PAIRED`、`MISSING_END`、`MISSING_START`、`DUPLICATE_START`、`OVERLAP`。
- `startParagraph` / `endParagraph` 是从 0 开始的正文顶层段落序号，便于定位问题。

### 7.5 健康检查

```http
GET /api/health
```

```json
{"status":"UP"}
```

---

## 8. 调用示例

示例文件均位于 `demo/backend/samples`。以下命令在 `demo/backend` 目录执行。

### 8.1 curl（Linux / macOS / Git Bash）

```bash
# 1. 合并
curl -s -X POST http://localhost:8080/api/documents/merge \
  -F "mainFile=@samples/main.docx" \
  -F "subFiles=@samples/sub1.docx" \
  -F "subFiles=@samples/sub2.docx" \
  -F "outputName=演示合并结果" \
  -o merge.json

# 2. 查看返回
cat merge.json

# 3. 提取 fileId（需要 jq）
FILE_ID=$(jq -r '.data.fileId' merge.json)

# 4. 下载
curl -s -o "演示合并结果.docx" \
  "http://localhost:8080/api/documents/download/$FILE_ID"

# 5. 检查副文档标记
curl -s -X POST http://localhost:8080/api/documents/inspect \
  -F "file=@samples/sub1.docx"

# 6. 删除临时文件
curl -s -X DELETE "http://localhost:8080/api/documents/files/$FILE_ID"
```

### 8.2 Windows cmd

```bat
curl.exe -s -X POST http://localhost:8080/api/documents/merge ^
  -F "mainFile=@samples/main.docx" ^
  -F "subFiles=@samples/sub1.docx" ^
  -F "subFiles=@samples/sub2.docx" ^
  -F "outputName=merged-demo" ^
  -o merge.json
```

打开 `merge.json`，复制 `data.fileId`，然后：

```bat
curl.exe -s -o merged-demo.docx "http://localhost:8080/api/documents/download/粘贴fileId"
```

> Windows PowerShell 5.1 调用原生 `curl.exe` 时，命令行中的中文会按系统代码页（简体中文环境通常为 GBK）
> 传给 curl，可能导致 `outputName` 乱码。此时可以：
> 1. 使用 ASCII 的 `outputName`（如 `merged-demo`），或
> 2. 使用 PowerShell 7+ 的 `Invoke-RestMethod -Form`，或
> 3. 直接在浏览器页面中操作。
>
> 无论使用哪种方式，下载接口返回的中文文件名都是正确的 UTF-8 编码。

### 8.3 PowerShell 7+

```powershell
$base = 'http://localhost:8080'

$form = @{
    mainFile   = Get-Item 'samples/main.docx'
    subFiles   = Get-Item 'samples/sub1.docx', 'samples/sub2.docx'
    outputName = '演示合并结果'
}

$result = Invoke-RestMethod -Method Post -Uri "$base/api/documents/merge" -Form $form
$result.data | Format-List

Invoke-WebRequest -Uri ($base + $result.data.downloadUrl) -OutFile $result.data.fileName

# 删除临时文件
Invoke-RestMethod -Method Delete -Uri "$base/api/documents/files/$($result.data.fileId)"
```

### 8.4 PowerShell 5.1（.NET HttpClient）

如果必须使用 Windows PowerShell 5.1 并希望发送中文 `outputName`，可以绕过代码页问题，直接使用 .NET 的
`HttpClient`：

```powershell
$base = 'http://localhost:8080'
$docxType = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
$form = New-Object System.Net.Http.MultipartFormDataContent

function Add-FilePart($form, $field, $path, $contentType) {
    $bytes = [System.IO.File]::ReadAllBytes((Resolve-Path $path))
    $part = New-Object System.Net.Http.ByteArrayContent(,$bytes)
    $part.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse($contentType)
    $form.Add($part, $field, [System.IO.Path]::GetFileName($path))
}

Add-FilePart $form 'mainFile'  'samples/main.docx'  $docxType
Add-FilePart $form 'subFiles'  'samples/sub1.docx'  $docxType
Add-FilePart $form 'subFiles'  'samples/sub2.docx'  $docxType
$form.Add((New-Object System.Net.Http.StringContent('演示合并结果', [System.Text.Encoding]::UTF8)), 'outputName')

$client = New-Object System.Net.Http.HttpClient
$response = $client.PostAsync("$base/api/documents/merge", $form).GetAwaiter().GetResult()
$json = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
$json.data | Format-List

Invoke-WebRequest -Uri ($base + $json.data.downloadUrl) -OutFile $json.data.fileName
```

---

## 9. 配置项

配置文件：`src/main/resources/application.yml`。所有 `app.*` 配置都可以通过环境变量覆盖，例如
`APP_STORAGE_DIR`、`APP_MERGE_TOC_TITLE`。

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `server.port` | `8080` | HTTP 端口 |
| `spring.servlet.multipart.max-file-size` | `50MB` | 单个文件上限 |
| `spring.servlet.multipart.max-request-size` | `160MB` | 整个 multipart 请求上限（可容纳多个文件） |
| `app.storage.dir` | `${java.io.tmpdir}/docx-merge` | 临时文件目录 |
| `app.storage.retention-hours` | `2` | 临时文件保留小时数，小于 1 时按 1 处理 |
| `app.merge.page-break-between` | `true` | 副文档之间是否插入分页符 |
| `app.merge.toc-title` | `目录` | 目录标题文本 |
| `app.merge.toc-depth` | `3` | 目录深度，范围 1–9 |
| `app.merge.heading-title-source` | `filename` | 副文档标题来源：`filename` 或 `tag` |
| `app.merge.number-headings` | `true` | 是否生成 1 / 1.1 / 1.1.1 多级标题编号 |

修改临时目录示例：

```bash
java -jar target/doc-merge-demo-1.0.0.jar --app.storage.dir=D:/temp/docx-merge
```

---

## 10. 关键实现说明

### 10.1 标记解析（`MarkerParser`）

- 先按顶层段落拼接所有 `w:t` 文本，记录每个字符属于哪个 `Text` 节点。
- 用 `${name-start}` / `${name-end}` 正则匹配，再换算成段落内绝对偏移。
- 提取区域时深拷贝整个段落，删除范围外的文本；这样 `w:pPr`、`w:rPr`、超链接等结构不会被破坏。
- 删除标记时只清空对应的字符范围，跨 run、跨段落的标记都能精确移除。
- 区域重叠时保留先出现的区域并给出 `OVERLAP` 警告，避免同一内容被复制两次。

### 10.2 内容复制（`DocxContentCopier`）

- 将源块序列化为 DOM 后复制到目标文档，关系 ID、`numId`、图片 ID 会重新映射。
- 图片通过 `BinaryPartAbstractImage` 和目标 `Part.addTargetPart(...)` 复制，避免直接复用源包关系。
- 外部超链接关系单独复制，保证 `w:hyperlink r:id` 仍然有效。
- 从副文档复制缺失的样式；标题样式降级后生成 `MergeH{level}_{baseStyle}` 派生样式，并设置对应的大纲级别。
- 为 `Heading1..9` 生成名为 `MergeHeadingNumbering` 的多级编号定义，实现 1 / 1.1 / 1.1.1。
- 复制失败的元素会记录警告并跳过，不影响其他内容。

### 10.3 目录（`TocService`）

- 在 `${toc}` 位置写入“目录”标题、`fldChar begin`、`instrText TOC \o "1-3" \h \z \u`、`fldChar separate/end`。
- 在 `settings.xml` 中写入 `<w:updateFields w:val="true"/>`，Word 打开时会提示更新域。
- 目录不预计算页码，避免服务端排版结果与 Word 不一致。

### 10.4 临时文件（`TempFileStore`）

- 使用 UUID 作为 `fileId`，文件名为 `{fileId}.docx`，元数据写入 `{fileId}.json` sidecar。
- `resolve()` 会 `normalize()` 并检查 `startsWith(root)`，阻止 `../` 路径穿越。
- `find()` 优先读内存索引，进程重启后仍可通过 sidecar 恢复下载。
- `@Scheduled` 每小时清理超过保留时间的文件，启动时执行一次。

### 10.5 依赖注意

`docx4j-JAXB-ReferenceImpl` 必须作为普通 jar 依赖引入，**不要**写成 `<type>pom</type>`。
否则运行时会缺少 `org.docx4j.jaxb.ri.NamespacePrefixMapper`，导致
`ClassNotFoundException`。

---

## 11. 测试与验证

### 11.1 运行测试

```bash
mvn -q test
```

测试覆盖：

- `MarkerParserTest`：跨 run、跨段落、同段区域、标记删除、重叠/缺失/重复标记、图片与空段落判断。
- `DocumentMergeIntegrationTest`：通过 MockMvc 真实生成 DOCX 并上传，覆盖合并、下载、结构校验、删除、
  inspect、非法请求和健康检查。
  - 校验目录域 `TOC \o`、表格、图片、`updateFields`
  - 校验派生标题样式 `MergeH2_Heading1` / `MergeH3_Heading2`
  - 校验多级编号 `MergeHeadingNumbering`
  - 校验标记之外的内容不会进入结果
  - 校验中文文件名下载头、404 错误码等

本次实现验证结果：

```text
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
```

### 11.2 打包

```bash
mvn -q -DskipTests package
```

产物：`target/doc-merge-demo-1.0.0.jar`

### 11.3 端到端冒烟（可选）

服务启动后，在 `demo/backend` 目录执行：

```bash
python samples/smoke_test.py
```

脚本会检查标记配对、合并结果、目录域、图片、表格、标记清理，并把下载结果保存为
`samples/合并结果示例.docx`。

---

## 12. 错误码

| HTTP | code | 场景 |
| --- | --- | --- |
| 400 | `40001` | 文件为空、没有可用副文档、文档无法解析等 |
| 400 | `40002` | 缺少 `mainFile` / `subFiles` 等必填参数 |
| 400 | `40003` | multipart 请求格式错误 |
| 404 | `40400` | 文件不存在、已删除或已过期；`fileId` 非法 |
| 413 | `41300` | 单个文件超过 50MB |
| 415 | `41500` | 上传的不是 `.docx`（不支持 `.doc` / `.docm` / `.pdf`） |
| 500 | `50000` | 服务端处理失败 |

---

## 13. 常见问题

**Q：打开合并结果后目录是空的，或者显示“错误！未找到目录项”？**

目录是 Word 域，需要更新。按 `Ctrl+A` 后按 `F9`；也可以在打印预览中让 Word 自动更新域。
页码依赖 Word 的排版结果，服务端不会预计算。

**Q：为什么合并结果里没有副文档开头/结尾的某些文字？**

合并只保留 `${name-start}` 与 `${name-end}` 之间的内容，标记之外的文字会被丢弃。
可以先用 `POST /api/documents/inspect` 检查每个副文档的标记配对情况。

**Q：表格里的标记为什么没有被识别？**

当前版本只解析正文顶层段落中的标记，不解析表格单元格、页眉页脚、脚注和文本框。
如果需要在表格内标记，建议把标记放在表格前后的正文段落中。

**Q：上传后返回 415？**

接口只接受 `.docx`。旧版 `.doc`、启用宏的 `.docm`、PDF 都需要先另存为 `.docx`。

**Q：上传大文件返回 413？**

单个文件上限为 50MB，整个请求上限为 160MB。可在 `application.yml` 中调整
`spring.servlet.multipart.max-file-size` 和 `max-request-size`。

**Q：8080 端口被占用？**

```bash
java -jar target/doc-merge-demo-1.0.0.jar --server.port=8081
```

**Q：临时文件在哪里？多久清理？**

默认在系统临时目录下的 `docx-merge` 子目录，保留 2 小时。可通过 `app.storage.dir` 和
`app.storage.retention-hours` 修改。

---

## 14. 已知限制

- 仅支持 `.docx`，不支持 `.doc`、`.docm`、PDF、RTF。
- 标记只在正文顶层段落生效，不支持表格单元格、页眉页脚、脚注/尾注、文本框中的标记。
- 合并的是标记区域的正文块；页眉页脚、页面设置、分节符、页码、批注、脚注/尾注不会随副文档复制。
- 图表、SmartArt、OLE 对象、内容控件等复杂对象不保证完整复制，复制失败时会记录警告并跳过。
- 目录页码需要 Word 更新域后才会显示；不同 Word/WPS 版本的域更新行为可能略有差异。
- 字体是否正常显示取决于打开文档的机器是否安装了对应字体。
- 临时文件存储在本机磁盘，适合单机 Demo；生产环境建议替换为对象存储并增加鉴权、病毒扫描和并发配额。

---

## 15. 许可证

本目录仅作为 OpenHarness 的演示示例，未单独声明许可证；如需复用，请遵循仓库根目录的许可证约定。
