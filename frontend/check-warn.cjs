// Vue 运行时警告规则扫描器
// 检查常见能导致浏览器控制台警告的写法
const fs = require('fs')
const path = require('path')

const SRC = path.join(__dirname, 'src')

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

// ===== 规则 1: el-statistic :value=String  =====
const RULE1 = /<el-statistic[^>]*:value="([^"]+)"/g
// ===== 规则 2: <component :is=nonMarkRaw =====
const RULE2 = /<component\s+:is="([^"]+)"/g
// ===== 规则 3: el-link :underline="false" (3.0 改 underline="never") =====
const RULE3 = /:underline="(false|true)"/g
// ===== 规则 4: 自定义 prop 名 kebab-case 但 props 写 camelCase 不匹配 =====
const RULE4 = /@[a-z]+-[a-z]+-[a-z]+/g  // 粗扫, 不一定准
// ===== 规则 5: :prop 后没引号包字符串 =====
const RULE5 = /:\w+="[^"']*\$\{[^}]+\}[^"']*"/g  // 检查带变量字符串

for (const f of files) {
  const src = fs.readFileSync(f, 'utf8')
  const filename = path.relative(SRC, f)
  const lines = src.split('\n')

  // 规则 1
  let m
  RULE1.lastIndex = 0
  while ((m = RULE1.exec(src))) {
    // 看 :value="s.value", s.value 默认是不是 String
    // 简单: 看赋值行 value: "—"|value: '—'
    const looksLikeString = /value:\s*['"`]—['"`]|value:\s*['"`]-['"`]/.test(src)
    if (looksLikeString) {
      // 找哪一行
      const ln = src.substring(0, m.index).split('\n').length
      issues.push(`[EL-STATISTIC] ${filename}:${ln} :value=可能传 String '—'`)
    }
  }

  // 规则 2: <component :is= 用法, 看变量是否经过 markRaw
  RULE2.lastIndex = 0
  while ((m = RULE2.exec(src))) {
    const expr = m[1]  // 例如 s.icon
    // 找 expr 的赋值行, 看是否 markRaw()
    const assignRe = new RegExp(`\\b${expr.replace(/\./g, '\\.')}\\s*[:=]\\s*([^,\\n}]+)`)
    const am = src.match(assignRe)
    if (am && !/markRaw\(/.test(am[1])) {
      const ln = src.substring(0, m.index).split('\n').length
      // 排除字符串 icon
      if (!/^['"`]/.test(am[1].trim())) {
        issues.push(`[MARK-RAW] ${filename}:${ln} <component :is="${expr}"> 但赋值没 markRaw()`)
      }
    }
  }

  // 规则 3: el-link :underline
  RULE3.lastIndex = 0
  while ((m = RULE3.exec(src))) {
    const ln = src.substring(0, m.index).split('\n').length
    issues.push(`[EL-LINK] ${filename}:${ln} :underline="${m[1]}"  Element Plus 3.0 应改 underline="never"`)
  }

  // 规则 6: 把 onMounted/watch/watchEffect 等 Vue API 没 import 用了
  const uses = []
  const usedWatchEffect = /\bwatchEffect\s*\(/.test(src)
  const usedWatchBasic = /\bwatch\s*\(/.test(src)
  const usedComputed = /\bcomputed\s*\(/.test(src)
  const usedRef = /\bref\s*\(/.test(src)
  const usedReactive = /\breactive\s*\(/.test(src)
  const usedMarkRaw = /\bmarkRaw\s*\(/.test(src)
  const usedOnMounted = /\bonMounted\s*\(/.test(src)
  const usedOnBeforeUnmount = /\bonBeforeUnmount\s*\(/.test(src)
  const usedNextTick = /\bnextTick\s*\(/.test(src)

  const importMatch = src.match(/import\s*\{([^}]+)\}\s*from\s*['"]vue['"]/)
  if (importMatch) {
    const imported = importMatch[1]
    const check = (used, name) => {
      if (used && !new RegExp(`\\b${name}\\b`).test(imported)) {
        // 找使用行
        const re = new RegExp(`\\b${name}\\s*\\(`)
        const match2 = re.exec(src)
        if (match2) {
          const ln = src.substring(0, match2.index).split('\n').length
          issues.push(`[MISSING-IMPORT] ${filename}:${ln} 用了 ${name}() 但 import 里没有`)
        }
      }
    }
    check(usedWatchEffect || usedWatchBasic, 'watch')
    check(usedWatchEffect, 'watchEffect')
    check(usedComputed, 'computed')
    check(usedRef, 'ref')
    check(usedReactive, 'reactive')
    check(usedMarkRaw, 'markRaw')
    check(usedOnMounted, 'onMounted')
    check(usedOnBeforeUnmount, 'onBeforeUnmount')
    check(usedNextTick, 'nextTick')
  }
}

console.log(`扫了 ${files.length} 个文件, 发现 ${issues.length} 个潜在问题:`)
console.log('---')
for (const i of issues) console.log(i)
process.exit(issues.length > 0 ? 1 : 0)
