import { describe, expect, it } from 'vitest'
import {
  addChildren,
  addRoot,
  buildTree,
  changeLevel,
  createNode,
  getDepthFromNo,
  moveByDrag,
  moveSibling,
  normalizeRows,
  removeRows,
  renumber,
} from './tree'

/** 构造测试数据：A / B(B1(B11)) / C */
function makeRows() {
  const a = createNode(null, { name: 'A' })
  const b = createNode(null, { name: 'B' })
  const b1 = createNode(b.id, { name: 'B1' })
  const b11 = createNode(b1.id, { name: 'B11' })
  const c = createNode(null, { name: 'C' })
  const rows = renumber([a, b, b1, b11, c])
  return { rows, a, b, b1, b11, c }
}

const numbers = (rows) => rows.map((row) => row.no)
const names = (rows) => rows.map((row) => row.name)

describe('renumber', () => {
  it('按先序层级生成 1 / 1.1 / 1.1.1 序号', () => {
    const { rows } = makeRows()
    expect(numbers(rows)).toEqual(['1', '2', '2.1', '2.1.1', '3'])
    expect(rows.every((row) => typeof row.no === 'string')).toBe(true)
  })

  it('根据序号推断深度', () => {
    expect(getDepthFromNo('1')).toBe(0)
    expect(getDepthFromNo('2.1')).toBe(1)
    expect(getDepthFromNo('2.1.1')).toBe(2)
    expect(getDepthFromNo('')).toBe(0)
  })
})

describe('buildTree / normalizeRows', () => {
  it('构建嵌套树且不破坏先序数组', () => {
    const { rows } = makeRows()
    const { roots, map } = buildTree(rows)
    expect(roots.map((node) => node.name)).toEqual(['A', 'B', 'C'])
    expect(map.get(rows[1].id).children.map((node) => node.name)).toEqual(['B1'])
  })

  it('修正孤儿节点、去重并打断环', () => {
    const a = createNode(null, { name: 'A' })
    const b = createNode(null, { name: 'B' })
    const orphan = createNode('not-exists', { name: '孤儿' })
    const loop1 = createNode(null, { id: 'loop1', name: '环1' })
    const loop2 = createNode(null, { id: 'loop2', name: '环2' })
    loop1.parentId = 'loop2'
    loop2.parentId = 'loop1'

    const normalized = normalizeRows([a, b, orphan, loop1, loop2, { ...a, name: '重复' }])
    // 环被打断后，loop2 成为根节点，loop1 挂在它下面，顺序随之调整
    expect(normalized.map((row) => row.name)).toEqual(['A', 'B', '孤儿', '环2', '环1'])
    expect(normalized.find((row) => row.name === '孤儿').parentId).toBeNull()
    expect(normalized.find((row) => row.name === '环1').parentId).toBe('loop2')
    expect(numbers(normalized)).toEqual(['1', '2', '3', '4', '4.1'])
  })
})

describe('新增', () => {
  it('新增一级目录追加到末尾', () => {
    const { rows } = makeRows()
    const result = addRoot(rows)
    expect(result.rows).toHaveLength(6)
    expect(result.rows.at(-1).no).toBe('4')
    expect(result.rows.at(-1).parentId).toBeNull()
  })

  it('新增下级放在父节点子级的最后一行', () => {
    const { rows, b, b11 } = makeRows()
    const result = addChildren(rows, [b.id])
    const index = result.rows.findIndex((row) => row.id === result.ids[0])

    expect(result.rows[index - 1].id).toBe(b11.id)
    expect(result.rows[index].parentId).toBe(b.id)
    expect(result.rows[index].no).toBe('2.2')
    expect(numbers(result.rows)).toEqual(['1', '2', '2.1', '2.1.1', '2.2', '3'])
  })

  it('支持选中多行批量新增下级', () => {
    const { rows, a, b } = makeRows()
    const result = addChildren(rows, [a.id, b.id])
    expect(result.ids).toHaveLength(2)
    expect(numbers(result.rows)).toEqual(['1', '1.1', '2', '2.1', '2.1.1', '2.2', '3'])
  })
})

describe('删除', () => {
  it('mode=subtree 连同全部子孙删除', () => {
    const { rows, b } = makeRows()
    const next = removeRows(rows, [b.id], 'subtree')
    expect(names(next)).toEqual(['A', 'C'])
    expect(numbers(next)).toEqual(['1', '2'])
  })

  it('mode=promote 只删当前行，子级提升一级', () => {
    const { rows, b } = makeRows()
    const next = removeRows(rows, [b.id], 'promote')
    expect(names(next)).toEqual(['A', 'B1', 'B11', 'C'])
    expect(numbers(next)).toEqual(['1', '2', '2.1', '3'])
    expect(next[1].parentId).toBeNull()
  })

  it('父子同时选中时也能正确删除', () => {
    const { rows, b, b1 } = makeRows()
    const next = removeRows(rows, [b.id, b1.id], 'subtree')
    expect(names(next)).toEqual(['A', 'C'])
  })
})

describe('同级移动', () => {
  it('第一行不能上移、最后一行不能下移', () => {
    const { rows, a, c } = makeRows()
    expect(moveSibling(rows, a.id, -1).ok).toBe(false)
    expect(moveSibling(rows, c.id, 1).ok).toBe(false)
  })

  it('上移时整棵子树一起移动并重算序号', () => {
    const { rows, c } = makeRows()
    const result = moveSibling(rows, c.id, -1)
    expect(result.ok).toBe(true)
    expect(names(result.rows)).toEqual(['A', 'C', 'B', 'B1', 'B11'])
    expect(numbers(result.rows)).toEqual(['1', '2', '3', '3.1', '3.1.1'])
  })
})

describe('层级调整', () => {
  it('向上移一级：成为父节点的下一个兄弟', () => {
    const { rows, b1 } = makeRows()
    const result = changeLevel(rows, b1.id, -1)
    expect(result.ok).toBe(true)
    expect(names(result.rows)).toEqual(['A', 'B', 'B1', 'B11', 'C'])
    expect(numbers(result.rows)).toEqual(['1', '2', '3', '3.1', '4'])
    expect(result.rows[2].parentId).toBeNull()
  })

  it('最高层级不能继续向上移', () => {
    const { rows, a } = makeRows()
    expect(changeLevel(rows, a.id, -1).ok).toBe(false)
  })

  it('前面没有同级目录时不能向下移一级', () => {
    const { rows, b11 } = makeRows()
    expect(changeLevel(rows, b11.id, 1).ok).toBe(false)
  })

  it('向下移一级：成为前一个兄弟的子级', () => {
    const a = createNode(null, { name: 'A' })
    const b = createNode(null, { name: 'B' })
    const rows = renumber([a, b])
    const result = changeLevel(rows, b.id, 1)
    expect(result.ok).toBe(true)
    expect(result.rows[1].parentId).toBe(a.id)
    expect(numbers(result.rows)).toEqual(['1', '1.1'])
  })
})

describe('拖拽', () => {
  it('同级拖拽调整顺序', () => {
    const { rows, c } = makeRows()
    const result = moveByDrag(rows, {
      sourceId: c.id,
      targetId: rows[0].id,
      position: 'before',
      depth: 0,
    })
    expect(result.ok).toBe(true)
    expect(names(result.rows)).toEqual(['C', 'A', 'B', 'B1', 'B11'])
    expect(numbers(result.rows)).toEqual(['1', '2', '3', '3.1', '3.1.1'])
  })

  it('拖到目标行内部成为子级', () => {
    const { rows, c, b1 } = makeRows()
    const result = moveByDrag(rows, {
      sourceId: c.id,
      targetId: b1.id,
      position: 'after',
      depth: 2,
    })
    expect(result.ok).toBe(true)
    expect(result.rows.find((row) => row.id === c.id).parentId).toBe(b1.id)
  })

  it('不能拖进自己的子树', () => {
    const { rows, b, b11 } = makeRows()
    const result = moveByDrag(rows, {
      sourceId: b.id,
      targetId: b11.id,
      position: 'after',
      depth: 2,
    })
    expect(result.ok).toBe(false)
    expect(result.message).toContain('自己的子级')
  })

  it('拖到自身位置直接忽略', () => {
    const { rows, a } = makeRows()
    const result = moveByDrag(rows, {
      sourceId: a.id,
      targetId: a.id,
      position: 'after',
      depth: 0,
    })
    expect(result.ok).toBe(false)
  })
})
