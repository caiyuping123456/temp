import { createRowId } from './id'

/**
 * 树形目录的核心数据工具。
 *
 * 设计约定：
 * 1. 对外（localStorage / 接口）始终是「一维先序数组」，通过 parentId 表达层级，
 *    满足需求中「保存时以一维数组方式存储」；
 * 2. 结构变更时内部临时构建嵌套树，操作完成后重新扁平化并重算序号，
 *    保证 parentId、先序顺序、序号三者永远一致；
 * 3. 序号是派生数据，但按要求单独存放在每行的 `no` 字段中。
 */

/** 每一级缩进的像素宽度（表格渲染与拖拽层级换算共用）。 */
export const INDENT_UNIT = 24

/** 新行 / 缺失字段的默认值。 */
const DEFAULT_FIELDS = {
  name: '',
  opType: '文字编辑',
  domain: '/',
  owner: '',
  dueDate: '',
  standard: '/',
  remark: '',
}

export function createNode(parentId = null, patch = {}) {
  return {
    ...DEFAULT_FIELDS,
    ...patch,
    id: patch.id || createRowId('dir'),
    parentId,
    no: '',
  }
}

/** 由一维先序数组构建嵌套树（不修改入参）。 */
export function buildTree(rows = []) {
  const map = new Map()
  const roots = []

  rows.forEach((row) => {
    map.set(row.id, { ...row, children: [], parent: null, depth: 0 })
  })

  rows.forEach((row) => {
    const node = map.get(row.id)
    if (!node) return
    const parent = row.parentId ? map.get(row.parentId) : null
    if (parent && parent !== node) {
      node.parent = parent
      parent.children.push(node)
    } else {
      roots.push(node)
    }
  })

  const assignDepth = (nodes, depth) => {
    nodes.forEach((node) => {
      node.depth = depth
      assignDepth(node.children, depth + 1)
    })
  }
  assignDepth(roots, 0)

  return { roots, map }
}

/** 嵌套树扁平化为一维先序数组，parentId 由树结构重新生成。 */
export function flattenTree(roots = []) {
  const rows = []
  const walk = (nodes, parentId) => {
    nodes.forEach((node) => {
      // eslint-disable-next-line no-unused-vars
      const { children, parent, depth, ...data } = node
      rows.push({ ...data, parentId })
      walk(children, node.id)
    })
  }
  walk(roots, null)
  return rows
}

/**
 * 重算序号：1 / 1.1 / 1.1.1 / 2 ...
 * 输入必须是一维先序数组（父节点一定排在子节点之前）。
 */
export function renumber(rows = []) {
  const counters = []
  const stack = []

  return rows.map((row) => {
    let depth = 0
    if (row.parentId) {
      const parentIndex = stack.lastIndexOf(row.parentId)
      depth = parentIndex >= 0 ? parentIndex + 1 : 0
    }

    stack.length = depth
    stack[depth] = row.id

    counters.length = depth + 1
    counters[depth] = (counters[depth] || 0) + 1

    return { ...row, no: counters.slice(0, depth + 1).join('.') }
  })
}

/** 根据序号推断层级（0 起）。 */
export function getDepthFromNo(no) {
  if (!no || typeof no !== 'string') return 0
  return Math.max(0, no.split('.').filter(Boolean).length - 1)
}

/**
 * 清洗外部数据（localStorage / 接口）：
 * 去重、修正孤儿 parentId、打断环、重新生成先序数组与序号。
 */
export function normalizeRows(input) {
  if (!Array.isArray(input)) return []

  const seen = new Set()
  const rows = []

  input.forEach((raw) => {
    if (!raw || typeof raw !== 'object') return
    const id = typeof raw.id === 'string' && raw.id ? raw.id : createRowId('dir')
    if (seen.has(id)) return
    seen.add(id)
    rows.push({
      ...DEFAULT_FIELDS,
      ...raw,
      id,
      parentId: typeof raw.parentId === 'string' && raw.parentId ? raw.parentId : null,
      no: '',
    })
  })

  const byId = new Map(rows.map((row) => [row.id, row]))
  rows.forEach((row) => {
    if (row.parentId && !byId.has(row.parentId)) row.parentId = null
  })
  breakCycles(rows, byId)

  return renumber(flattenTree(buildTree(rows).roots))
}

function breakCycles(rows, byId) {
  const status = new Map() // 1: 访问中, 2: 已完成
  const visit = (row) => {
    if (status.get(row.id) === 2) return true
    if (status.get(row.id) === 1) return false
    status.set(row.id, 1)
    const parent = row.parentId ? byId.get(row.parentId) : null
    if (parent && !visit(parent)) {
      row.parentId = null
    }
    status.set(row.id, 2)
    return true
  }
  rows.forEach(visit)
}

const commit = (roots) => renumber(flattenTree(roots))

/* ------------------------------------------------------------------ */
/* 结构操作                                                             */
/* ------------------------------------------------------------------ */

/** 新增一级目录（追加到末尾）。 */
export function addRoot(rows) {
  const { roots } = buildTree(rows)
  const node = { ...createNode(null, { name: '新目录' }), children: [], parent: null, depth: 0 }
  roots.push(node)
  return { rows: commit(roots), id: node.id }
}

/** 给选中的行批量新增下级，新行放在父节点子级的最后一行。 */
export function addChildren(rows, parentIds = []) {
  const { roots, map } = buildTree(rows)
  const created = []

  ;[...new Set(parentIds)].forEach((parentId) => {
    const parent = map.get(parentId)
    if (!parent) return
    const node = {
      ...createNode(parentId, { name: '新子目录' }),
      children: [],
      parent,
      depth: parent.depth + 1,
    }
    parent.children.push(node)
    created.push(node.id)
  })

  return { rows: commit(roots), ids: created }
}

/**
 * 删除行。
 * mode = 'subtree'：连同所有子孙一起删除；
 * mode = 'promote'：只删当前行，子级整体提升一级（接到被删节点的父级下）。
 * 先处理序号靠后的行，避免父子同时被选中时重复处理。
 */
export function removeRows(rows, ids = [], mode = 'subtree') {
  const targets = [...new Set(ids)]
    .map((id) => ({ id, index: rows.findIndex((row) => row.id === id) }))
    .filter((item) => item.index >= 0)
    .sort((a, b) => b.index - a.index)

  let current = rows

  targets.forEach(({ id }) => {
    const { roots, map } = buildTree(current)
    const node = map.get(id)
    if (!node) return

    const siblings = node.parent ? node.parent.children : roots
    const index = siblings.indexOf(node)
    if (index < 0) return

    if (mode === 'promote') {
      node.children.forEach((child) => {
        child.parent = node.parent
      })
      siblings.splice(index, 1, ...node.children)
    } else {
      siblings.splice(index, 1)
    }

    current = commit(roots)
  })

  return current
}

/** 同级上移 / 下移（offset = -1 / 1），整棵子树一起移动。 */
export function moveSibling(rows, id, offset) {
  const { roots, map } = buildTree(rows)
  const node = map.get(id)
  if (!node) return { ok: false, message: '未找到该目录' }

  const siblings = node.parent ? node.parent.children : roots
  const index = siblings.indexOf(node)

  if (offset < 0 && index === 0) {
    return { ok: false, message: '已经是同级第一行，无法上移' }
  }
  if (offset > 0 && index === siblings.length - 1) {
    return { ok: false, message: '已经是同级最后一行，无法下移' }
  }

  siblings.splice(index, 1)
  siblings.splice(index + offset, 0, node)

  return { ok: true, rows: commit(roots) }
}

/**
 * 调整层级：
 * delta = -1 向上移一级（左移，成为父节点的下一个兄弟）；
 * delta = +1 向下移一级（右移，成为前一个兄弟的最后一个子级）。
 */
export function changeLevel(rows, id, delta) {
  const { roots, map } = buildTree(rows)
  const node = map.get(id)
  if (!node) return { ok: false, message: '未找到该目录' }

  if (delta < 0) {
    if (!node.parent) {
      return { ok: false, message: '已经是最高层级，无法向上移一级' }
    }
    const parent = node.parent
    const parentSiblings = parent.parent ? parent.parent.children : roots
    const parentIndex = parentSiblings.indexOf(parent)
    const siblings = parent.children
    siblings.splice(siblings.indexOf(node), 1)
    parentSiblings.splice(parentIndex + 1, 0, node)
    node.parent = parent.parent
    return { ok: true, rows: commit(roots) }
  }

  const siblings = node.parent ? node.parent.children : roots
  const index = siblings.indexOf(node)
  const previous = siblings[index - 1]
  if (!previous) {
    return { ok: false, message: '前面没有同级目录，无法向下移一级' }
  }

  siblings.splice(index, 1)
  previous.children.push(node)
  node.parent = previous

  return { ok: true, rows: commit(roots) }
}

/**
 * 拖拽落点处理：把 source 整棵子树移动到 target 的前 / 后，
 * desiredDepth 为拖拽时指针横移换算出的目标层级。
 *
 * 语义与 vxe-table 的行拖拽一致：
 * - 水平拖到更深的位置 => 成为目标位置的子级；
 * - 水平拖回左侧 => 提升层级；
 * - 不允许把节点拖进自己的子树。
 */
export function moveByDrag(rows, { sourceId, targetId, position = 'after', depth } = {}) {
  if (!sourceId || !targetId) return { ok: false, message: '拖拽参数不完整' }
  if (sourceId === targetId) return { ok: false, message: '不能拖拽到自身位置' }

  const { roots, map } = buildTree(rows)
  const source = map.get(sourceId)
  const target = map.get(targetId)
  if (!source || !target) return { ok: false, message: '拖拽目标不存在' }
  if (isDescendant(source, target)) {
    return { ok: false, message: '不能把目录拖拽到自己的子级中' }
  }

  const isAfter = position === 'after'
  const maxDepth = target.depth + (isAfter ? 1 : 0)
  const desiredDepth = Math.min(maxDepth, Math.max(0, Number.isFinite(depth) ? depth : source.depth))

  const chain = []
  let cursor = target
  while (cursor) {
    chain.unshift(cursor)
    cursor = cursor.parent
  }

  let newParent = null
  let refChild = null

  if (isAfter && desiredDepth === target.depth + 1) {
    // 横向拖到目标内部：作为目标的第一个子级
    newParent = target
  } else {
    newParent = desiredDepth === 0 ? null : chain[desiredDepth - 1]
    refChild = chain[desiredDepth]
  }

  if (newParent && (newParent === source || isDescendant(source, newParent))) {
    return { ok: false, message: '不能把目录拖拽到自己的子级中' }
  }

  // 先摘除，再按对象引用查找新的插入位置（避免下标偏移）
  const oldSiblings = source.parent ? source.parent.children : roots
  oldSiblings.splice(oldSiblings.indexOf(source), 1)

  const newSiblings = newParent ? newParent.children : roots
  const refIndex = refChild ? newSiblings.indexOf(refChild) : -1

  if (refChild && refIndex >= 0) {
    newSiblings.splice(isAfter ? refIndex + 1 : refIndex, 0, source)
  } else {
    newSiblings.unshift(source)
  }
  source.parent = newParent

  return { ok: true, rows: commit(roots) }
}

/* ------------------------------------------------------------------ */
/* 查询                                                                */
/* ------------------------------------------------------------------ */

export function isDescendant(ancestor, node) {
  let cursor = node?.parent || null
  let guard = 0
  while (cursor && guard < 10000) {
    if (cursor === ancestor) return true
    cursor = cursor.parent
    guard += 1
  }
  return false
}

export function hasChildren(rows, id) {
  return rows.some((row) => row.parentId === id)
}

export function countDescendants(rows, id) {
  const children = rows.filter((row) => row.parentId === id)
  return children.reduce((total, child) => total + 1 + countDescendants(rows, child.id), 0)
}

export function findRow(rows, id) {
  return rows.find((row) => row.id === id) || null
}

export function findRowIndex(rows, id) {
  return rows.findIndex((row) => row.id === id)
}

/** 返回某一行的同级信息，便于按钮禁用态 / 提示文案使用。 */
export function getSiblingInfo(rows, id) {
  const row = findRow(rows, id)
  if (!row) return null
  const siblings = rows.filter((item) => item.parentId === row.parentId)
  const index = siblings.findIndex((item) => item.id === id)
  return {
    index,
    size: siblings.length,
    isFirst: index === 0,
    isLast: index === siblings.length - 1,
    depth: getDepthFromNo(row.no),
  }
}
