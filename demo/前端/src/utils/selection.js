export function uniqueRowsById(...groups) {
  const seen = new Set()
  const rows = []

  groups.forEach((group) => {
    if (!Array.isArray(group)) return
    group.forEach((row) => {
      if (!row || typeof row.id !== 'string' || !row.id || seen.has(row.id)) return
      seen.add(row.id)
      rows.push(row)
    })
  })

  return rows
}
