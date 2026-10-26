/**
 * 文档合并页面逻辑。
 *
 * 接口：
 * - POST /api/documents/inspect  单文件，返回识别到的标识符
 * - POST /api/documents/merge    主文档 + 多个副文档 + 输出名
 * - GET  /api/documents/download/{fileId}
 */
;(() => {
  'use strict'

  const state = {
    main: null, // { file, inspect, error }
    subs: [], // [{ file, inspect, error }]
    loading: false,
    result: null,
  }

  const dom = {
    tabs: Array.from(document.querySelectorAll('.tab')),
    panels: {
      main: document.getElementById('panel-main'),
      sub: document.getElementById('panel-sub'),
    },
    mainInput: document.getElementById('main-input'),
    subInput: document.getElementById('sub-input'),
    mainDrop: document.getElementById('main-drop'),
    subDrop: document.getElementById('sub-drop'),
    mainList: document.getElementById('main-list'),
    subList: document.getElementById('sub-list'),
    subEmpty: document.getElementById('sub-empty'),
    mainCount: document.getElementById('main-count'),
    subCount: document.getElementById('sub-count'),
    outputName: document.getElementById('output-name'),
    mergeBtn: document.getElementById('merge-btn'),
    resetBtn: document.getElementById('reset-btn'),
    status: document.getElementById('status'),
  }

  const STATUS_LABELS = {
    PAIRED: '已配对',
    MISSING_END: '缺少结束标记',
    MISSING_START: '缺少开始标记',
    DUPLICATE_START: '重复开始标记',
    OVERLAP: '标记嵌套冲突',
  }

  /* ------------------------------------------------------------------ */
  /* 工具                                                                */
  /* ------------------------------------------------------------------ */

  const formatSize = (bytes) => {
    if (!Number.isFinite(bytes) || bytes <= 0) return '0 B'
    if (bytes < 1024) return `${bytes} B`
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
    return `${(bytes / 1024 / 1024).toFixed(2)} MB`
  }

  const isDocx = (file) => file && /\.docx$/i.test(file.name)

  const create = (tag, className, text) => {
    const node = document.createElement(tag)
    if (className) node.className = className
    if (text !== undefined) node.textContent = text
    return node
  }

  const fileKey = (file) => `${file.name}::${file.size}::${file.lastModified}`

  const setLoading = (value) => {
    state.loading = value
    dom.mergeBtn.disabled = value || !state.main || state.subs.length === 0
    dom.mergeBtn.textContent = value ? '合并中…' : '开始合并'
  }

  /* ------------------------------------------------------------------ */
  /* 标签页                                                              */
  /* ------------------------------------------------------------------ */

  const activateTab = (name) => {
    dom.tabs.forEach((tab) => {
      const active = tab.dataset.tab === name
      tab.classList.toggle('is-active', active)
      tab.setAttribute('aria-selected', String(active))
    })
    Object.entries(dom.panels).forEach(([key, panel]) => {
      panel.hidden = key !== name
    })
  }

  dom.tabs.forEach((tab) => {
    tab.addEventListener('click', () => activateTab(tab.dataset.tab))
  })

  /* ------------------------------------------------------------------ */
  /* 标识解析                                                            */
  /* ------------------------------------------------------------------ */

  const inspectFile = async (file) => {
    const form = new FormData()
    form.append('file', file)

    try {
      const response = await fetch('/api/documents/inspect', { method: 'POST', body: form })
      const payload = await response.json().catch(() => null)

      if (!response.ok || !payload || payload.code !== 0) {
        return { error: payload?.message || `解析失败（HTTP ${response.status}）`, data: null }
      }
      return { error: null, data: payload.data }
    } catch (error) {
      return { error: '无法连接后端服务，请确认服务已启动', data: null }
    }
  }

  const renderTags = (container, inspect, fallbackError) => {
    if (fallbackError) {
      container.append(create('span', 'tag warn', '解析失败'))
      return
    }
    if (!inspect) {
      container.append(create('span', 'tag info', '解析中…'))
      return
    }

    const tags = inspect.tags || []
    if (!tags.length) {
      container.append(create('span', 'tag info', '未发现标记'))
      return
    }

    tags.slice(0, 6).forEach((tag) => {
      const paired = tag.status === 'PAIRED'
      const label = `${tag.name} · ${STATUS_LABELS[tag.status] || tag.status}`
      const chip = create('span', `tag ${paired ? 'ok' : 'warn'}`, label)
      chip.title = tag.message || ''
      container.append(chip)
    })

    if (tags.length > 6) {
      container.append(create('span', 'tag info', `+${tags.length - 6}`))
    }
  }

  /* ------------------------------------------------------------------ */
  /* 文件列表渲染                                                        */
  /* ------------------------------------------------------------------ */

  const renderMain = () => {
    dom.mainList.replaceChildren()

    if (!state.main) {
      dom.mainCount.textContent = '0'
      setLoading(state.loading)
      return
    }

    const item = state.main
    const li = create('li', 'file-item')
    li.append(create('span', 'file-index', '主'))

    const body = create('div', 'file-body')
    const nameRow = create('div', 'file-name')
    nameRow.append(document.createTextNode(item.file.name))
    nameRow.append(create('span', 'file-size', formatSize(item.file.size)))
    body.append(nameRow)

    const tags = create('div', 'file-tags')
    renderTags(tags, item.inspect, item.error)
    body.append(tags)

    if (item.error) {
      body.append(create('div', 'file-note', item.error))
    }

    li.append(body)

    const remove = create('button', 'remove', '移除')
    remove.type = 'button'
    remove.addEventListener('click', () => {
      state.main = null
      renderMain()
    })
    li.append(remove)

    dom.mainList.append(li)
    dom.mainCount.textContent = '1'
    setLoading(state.loading)
  }

  const renderSubs = () => {
    dom.subList.replaceChildren()

    state.subs.forEach((item, index) => {
      const li = create('li', 'file-item')
      li.append(create('span', 'file-index', String(index + 1)))

      const body = create('div', 'file-body')
      const nameRow = create('div', 'file-name')
      nameRow.append(document.createTextNode(item.file.name))
      nameRow.append(create('span', 'file-size', formatSize(item.file.size)))
      body.append(nameRow)

      const tags = create('div', 'file-tags')
      renderTags(tags, item.inspect, item.error)
      body.append(tags)

      if (item.error) {
        body.append(create('div', 'file-note', item.error))
      }

      li.append(body)

      const remove = create('button', 'remove', '移除')
      remove.type = 'button'
      remove.addEventListener('click', () => {
        state.subs = state.subs.filter((entry) => entry !== item)
        renderSubs()
      })
      li.append(remove)

      dom.subList.append(li)
    })

    dom.subCount.textContent = String(state.subs.length)
    dom.subEmpty.hidden = state.subs.length > 0
    setLoading(state.loading)
  }

  /* ------------------------------------------------------------------ */
  /* 添加文件                                                            */
  /* ------------------------------------------------------------------ */

  const addMainFile = (file) => {
    if (!isDocx(file)) {
      renderError(`「${file?.name || '未知文件'}」不是 .docx 文件`)
      return
    }

    const entry = { file, inspect: null, error: null }
    state.main = entry
    renderMain()
    clearStatus()

    inspectFile(file).then(({ data, error }) => {
      if (state.main !== entry) return
      state.main.error = error
      state.main.inspect = data
      renderMain()
    })
  }

  const addSubFiles = (files) => {
    const incoming = Array.from(files || [])
    const invalid = incoming.filter((file) => !isDocx(file))
    if (invalid.length) {
      renderError(`以下文件不是 .docx，已忽略：${invalid.map((file) => file.name).join('、')}`)
    }

    const existing = new Set(state.subs.map((item) => fileKey(item.file)))
    const entries = incoming
      .filter((file) => isDocx(file))
      .filter((file) => {
        const key = fileKey(file)
        if (existing.has(key)) return false
        existing.add(key)
        return true
      })
      .map((file) => ({ file, inspect: null, error: null }))

    if (!entries.length) {
      if (!invalid.length) renderError('没有新增文件（重复文件已自动忽略）')
      return
    }

    state.subs.push(...entries)
    renderSubs()
    if (!invalid.length) clearStatus()

    entries.forEach((entry) => {
      inspectFile(entry.file).then(({ data, error }) => {
        if (!state.subs.includes(entry)) return
        entry.error = error
        entry.inspect = data
        renderSubs()
      })
    })
  }

  /* ------------------------------------------------------------------ */
  /* 拖拽上传                                                            */
  /* ------------------------------------------------------------------ */

  const bindDropzone = (zone, input, handler) => {
    zone.addEventListener('dragover', (event) => {
      event.preventDefault()
      zone.classList.add('is-dragover')
    })

    zone.addEventListener('dragleave', () => {
      zone.classList.remove('is-dragover')
    })

    zone.addEventListener('drop', (event) => {
      event.preventDefault()
      zone.classList.remove('is-dragover')
      handler(event.dataTransfer?.files)
    })

    input.addEventListener('change', () => {
      handler(input.files)
      input.value = ''
    })
  }

  bindDropzone(dom.mainDrop, dom.mainInput, (files) => {
    if (files && files[0]) addMainFile(files[0])
  })

  bindDropzone(dom.subDrop, dom.subInput, addSubFiles)

  /* ------------------------------------------------------------------ */
  /* 状态区                                                              */
  /* ------------------------------------------------------------------ */

  const clearStatus = () => {
    dom.status.replaceChildren()
  }

  const renderLoading = () => {
    dom.status.replaceChildren()
    const box = create('div', 'status-box loading')
    box.append(create('span', 'spinner'))
    box.append(create('span', '', '正在解析并合并文档，请稍候…'))
    dom.status.append(box)
  }

  const renderError = (message) => {
    dom.status.replaceChildren()
    const box = create('div', 'status-box error')
    box.append(create('span', 'status-title', '操作失败'))
    box.append(create('span', '', message))
    dom.status.append(box)
  }

  const renderResult = (data) => {
    dom.status.replaceChildren()

    const box = create('div', 'status-box success')
    box.append(create('p', 'result-file-name status-title', '合并完成'))

    const row = create('div', 'result-file')
    row.append(create('strong', '', data.fileName || '合并结果.docx'))
    row.append(create('span', '', formatSize(data.size)))

    const link = create('a', 'btn download', '下载文档')
    link.href = data.downloadUrl
    link.setAttribute('download', '')
    row.append(link)
    box.append(row)

    if (data.tags && data.tags.length) {
      const paired = data.tags.filter((tag) => tag.status === 'PAIRED').length
      box.append(create('div', 'file-note', `识别到 ${data.tags.length} 个标识，其中 ${paired} 个成功配对。`))
    }

    const warnings = data.warnings || []
    if (warnings.length) {
      const list = create('ul', 'warnings')
      warnings.forEach((warning) => list.append(create('li', '', warning)))
      box.append(list)
    }

    dom.status.append(box)
  }

  /* ------------------------------------------------------------------ */
  /* 合并 / 重置                                                         */
  /* ------------------------------------------------------------------ */

  const merge = async () => {
    if (state.loading || !state.main || !state.subs.length) return

    setLoading(true)
    renderLoading()

    const form = new FormData()
    form.append('mainFile', state.main.file)
    state.subs.forEach((item) => form.append('subFiles', item.file))
    if (dom.outputName.value.trim()) form.append('outputName', dom.outputName.value.trim())

    try {
      const response = await fetch('/api/documents/merge', { method: 'POST', body: form })
      const payload = await response.json().catch(() => null)

      if (!response.ok || !payload || payload.code !== 0) {
        throw new Error(payload?.message || `合并失败（HTTP ${response.status}）`)
      }

      clearStatus()
      renderResult(payload.data)
      state.result = payload.data
    } catch (error) {
      renderError(error.message || '合并失败，请查看后端日志')
    } finally {
      setLoading(false)
    }
  }

  dom.mergeBtn.addEventListener('click', merge)

  dom.resetBtn.addEventListener('click', () => {
    state.main = null
    state.subs = []
    state.result = null
    dom.outputName.value = '合并结果'
    renderMain()
    renderSubs()
    clearStatus()
  })

  dom.outputName.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') merge()
  })

  /* ------------------------------------------------------------------ */
  /* 初始化                                                              */
  /* ------------------------------------------------------------------ */

  renderMain()
  renderSubs()
  setLoading(false)
})()
