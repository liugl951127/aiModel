// 终极扫描器: 用 line 状态机, 不依赖正则匹配
// 1) 状态: READING_IMPORT 时持续吃, 遇到 } from '...icons-vue' 结束
// 2) 简单可靠, 不跨 import
const fs = require('fs')
const path = require('path')

const SRC = path.join(__dirname, 'src')
const icons = require('@element-plus/icons-vue')
const allIcons = new Set(Object.keys(icons))

const KNOWN_COMPONENTS = new Set([
  'BizCrudPage', 'WorkflowAssistant', 'LiveTickerBar'
])

function walk(dir, files = []) {
  for (const f of fs.readdirSync(dir)) {
    const p = path.join(dir, f)
    const s = fs.statSync(p)
    if (s.isDirectory()) walk(p, files)
    else if (p.endsWith('.vue')) files.push(p)
  }
  return files
}

const files = walk(SRC)
const issues = []

function extractNamesFromImport(startLine, lines) {
  // 从 startLine 开始, 读 } from '...icons-vue' 为止
  let buf = ''
  for (let i = startLine; i < lines.length; i++) {
    buf += lines[i] + '\n'
    if (/\}\s*from\s*['"]@element-plus\/icons-vue['"]/.test(buf)) {
      // 完成
      const m = buf.match(/\{([\s\S]*?)\}\s*from\s*['"]@element-plus\/icons-vue['"]/)
      if (m) return m[1].split(',').map(s => s.trim()).filter(Boolean)
      return []
    }
  }
  return []
}

function extractOtherImport(startLine, lines) {
  let buf = ''
  for (let i = startLine; i < lines.length; i++) {
    buf += lines[i] + '\n'
    if (/\}\s*from\s*['"]/.test(buf) && !/@element-plus\/icons-vue/.test(buf)) {
      const m = buf.match(/\{([\s\S]*?)\}\s*from\s*['"]/)
      if (m) {
        return m[1].split(',').map(s => s.trim()).filter(Boolean).map(n => {
          const asMatch = n.match(/\bas\s+(\w+)/)
          return asMatch ? asMatch[1] : n.split(/\s+/)[0]
        })
      }
      return []
    }
  }
  return []
}

for (const f of files) {
  const src = fs.readFileSync(f, 'utf8')
  const filename = path.relative(SRC, f)
  const lines = src.split('\n')

  // 1) 找所有 import { ... } from 起点
  const iconImports = new Set()
  const otherImports = new Set()
  for (let i = 0; i < lines.length; i++) {
    if (/^\s*import\s*\{/.test(lines[i])) {
      if (/@element-plus\/icons-vue/.test(lines[i]) ||
          // 跨行情况: 这一行 import { 但 end 不在本行
          (i + 1 < lines.length && /@element-plus\/icons-vue/.test(lines.slice(i, i + 5).join('\n')))) {
        const names = extractNamesFromImport(i, lines)
        names.forEach(n => iconImports.add(n))
      } else {
        const names = extractOtherImport(i, lines)
        names.forEach(n => otherImports.add(n))
      }
    }
  }

  // 2) template 标签
  const tplMatch = src.match(/<template>([\s\S]*?)<\/template>/)
  if (!tplMatch) continue
  const tpl = tplMatch[1]
  const tplOffset = tplMatch.index + '<template>'.length
  const tagRe = /<([A-Z][A-Za-z0-9_]*)\b/g
  let tm
  while ((tm = tagRe.exec(tpl))) {
    const name = tm[1]
    if (KNOWN_COMPONENTS.has(name)) continue
    if (otherImports.has(name)) continue
    if (iconImports.has(name)) continue
    if (allIcons.has(name)) {
      const ln = src.substring(0, tm.index + tplOffset).split('\n').length
      issues.push({ file: filename, line: ln, name })
    }
  }
}

console.log(`扫了 ${files.length} 个文件, ${issues.length} 个图标引用问题:`)
console.log('---')
for (const i of issues) {
  console.log(`  ${i.file}:${i.line}  <${i.name}> 用了但没 import`)
}
process.exit(issues.length > 0 ? 1 : 0)
