import { describe, expect, it } from 'vitest'
import { uniqueRowsById } from './selection'

describe('uniqueRowsById', () => {
  it('按 id 合并当前页选择与跨页保留选择，并保持首次出现顺序', () => {
    const rows = uniqueRowsById(
      [{ id: 'a', name: 'A' }, { id: 'b', name: 'B' }],
      [{ id: 'b', name: 'B' }, { id: 'c', name: 'C' }],
    )

    expect(rows.map((row) => row.id)).toEqual(['a', 'b', 'c'])
  })

  it('忽略空分组和无效行', () => {
    const rows = uniqueRowsById(null, [{ id: '' }, { id: 'a' }, undefined], undefined)

    expect(rows.map((row) => row.id)).toEqual(['a'])
  })
})
