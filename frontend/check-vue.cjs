// Vue SFC 语法静态检查器
// 扫描 src 下所有 .vue, 解析 template + script, 输出潜在警告
const { parse, compileTemplate, compileScript } = require('@vue/compiler-sfc')
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
let totalWarn = 0
let totalError = 0

for (const f of files) {
  const src = fs.readFileSync(f, 'utf8')
  const filename = path.relative(SRC, f)
  const { descriptor, errors } = parse(src, { filename: f })

  // 1) SFC 解析错误
  for (const e of errors) {
    console.log(`[ERROR] ${filename}: ${e.message}`)
    totalError++
  }

  // 2) template 编译错误
  if (descriptor.template) {
    try {
      const r = compileTemplate({
        source: descriptor.template.content,
        filename: f,
        id: filename
      })
      for (const e of r.errors || []) {
        console.log(`[TPL-ERR] ${filename}: ${e.message}`)
        totalError++
      }
    } catch (e) {
      console.log(`[TPL-EXC] ${filename}: ${e.message}`)
      totalError++
    }
  }

  // 3) script 编译 (检查未声明变量 / 语法)
  if (descriptor.script || descriptor.scriptSetup) {
    try {
      const scriptContent = (descriptor.scriptSetup || descriptor.script).content
      compileScript(descriptor, { id: filename, isProd: false })
    } catch (e) {
      console.log(`[SCRIPT-ERR] ${filename}: ${e.message}`)
      totalError++
    }
  }
}

console.log('---')
console.log(`扫了 ${files.length} 个 .vue 文件`)
console.log(`错误: ${totalError}, 警告位点: ${totalWarn}`)
process.exit(totalError > 0 ? 1 : 0)
