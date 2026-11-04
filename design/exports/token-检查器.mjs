#!/usr/bin/env node
/**
 * 地标寻宝 · 设计 token 体检（2.3 视觉统一度 / 信息清晰）
 *
 * 做两件事：
 *   1) contrast —— 按 WCAG 2.1 算关键前景/背景配对的对比度，正文要 ≥4.5，大字/图标要 ≥3.0
 *   2) audit    —— 扫 token 里的颜色是否成对定义了 on* 前景色、字号是否落在同一套比例上
 *
 * 用法（项目根目录）：
 *   node design/tools/tokens.mjs contrast
 *   node design/tools/tokens.mjs contrast --strict   # 有 FAIL 时退出码 1，可用于合入前检查
 *   node design/tools/tokens.mjs audit
 *   node design/tools/tokens.mjs all
 */

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))

/* ---------- 定位 tokens.json（真实路径，不再写死一条相对路径）----------
   查找顺序：
     1) --tokens <path>        显式指定（推荐在 CI / 交付包里用）
     2) $TOKENS_JSON 环境变量
     3) 从脚本所在目录逐级向上找 design/tokens/tokens.json
     4) 从当前工作目录逐级向上找 design/tokens/tokens.json
     5) 同目录 / 上两级的 tokens.json（交付包里常把 json 和脚本放一起）
   全部找不到就报错并列出找过的路径，不再静默失败。
------------------------------------------------------------------------ */
const CANDIDATES = []
function pushCandidates(base) {
  let dir = path.resolve(base)
  for (let i = 0; i < 6 && dir; i++) {
    CANDIDATES.push(path.join(dir, 'design', 'tokens', 'tokens.json'))
    CANDIDATES.push(path.join(dir, 'tokens.json'))
    const up = path.dirname(dir)
    if (up === dir) break
    dir = up
  }
}
function resolveTokens() {
  const idx = process.argv.indexOf('--tokens')
  if (idx > -1 && process.argv[idx + 1]) {
    const p = path.resolve(process.argv[idx + 1])
    if (!fs.existsSync(p)) { console.error('✗ --tokens 指定的文件不存在：' + p); process.exit(2) }
    return p
  }
  if (process.env.TOKENS_JSON) {
    const p = path.resolve(process.env.TOKENS_JSON)
    if (fs.existsSync(p)) return p
    console.error('✗ $TOKENS_JSON 指向的文件不存在：' + p)
  }
  pushCandidates(HERE)
  pushCandidates(process.cwd())
  let hit = CANDIDATES.find(p => fs.existsSync(p))
  if (!hit) {
    // 交付包里常带前缀（如 03-tokens.json），同目录扫一遍
    for (const dir of [HERE, process.cwd()]) {
      try {
        const f = fs.readdirSync(dir).filter(n => /(^|[^a-z])tokens\.json$/i.test(n) || /\d+-tokens\.json$/i.test(n))
        if (f.length) { hit = path.join(dir, f[0]); break }
      } catch (e) { /* 目录不可读就跳过 */ }
    }
  }
  if (hit) return hit
  console.error('✗ 找不到 tokens.json。试过这些路径（前 6 条）：')
  for (const p of CANDIDATES.slice(0, 6)) console.error('    ' + p)
  console.error('\n  修好办法（任选一）：')
  console.error('    1) node design/tools/tokens.mjs --tokens <你的/tokens.json 绝对路径>')
  console.error('    2) set TOKENS_JSON=<绝对路径> 后重跑')
  console.error('    3) 把 tokens.json 放到脚本同目录（交付包默认这么做）')
  process.exit(2)
}
const TOKENS_PATH = resolveTokens()
const T = JSON.parse(fs.readFileSync(TOKENS_PATH, 'utf8'))

/* ---------- 颜色工具 ---------- */
function parse(hex) {
  const h = hex.replace('#', '').trim()
  if (h.length === 8) {
    // #RRGGBBAA：合成到白底后参与对比度计算（保守估计）
    const a = parseInt(h.slice(6, 8), 16) / 255
    const rgb = [0, 2, 4].map(i => Math.round(parseInt(h.slice(i, i + 2), 16) * a + 255 * (1 - a)))
    return { rgb, alpha: a }
  }
  return { rgb: [0, 2, 4].map(i => parseInt(h.slice(i, i + 2), 16)), alpha: 1 }
}
const chan = c => { const s = c / 255; return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4) }
const lum = hex => { const { rgb } = parse(hex); return 0.2126 * chan(rgb[0]) + 0.7152 * chan(rgb[1]) + 0.0722 * chan(rgb[2]) }
const ratio = (a, b) => { const l1 = lum(a), l2 = lum(b); const [hi, lo] = l1 > l2 ? [l1, l2] : [l2, l1]; return (hi + 0.05) / (lo + 0.05) }

const get = (p) => p.split('.').reduce((o, k) => (o ? o[k] : undefined), T.color)

/* ---------- 需要检查的配对（阈值分档登记，不凭截图猜色）----------
 * [前景 token, 背景 token, 场景, 档位]
 * 档位（WCAG 2.1 + 本项目设计口径，改阈值先在这里登记）：
 *   text    正文/说明文字          ≥ 4.5
 *   large   大字（≥18.5px 粗体 / ≥24px，如成就徽章大数字）≥ 3.0
 *   graphic 图形/描边/路线（非文本）≥ 3.0
 */
const THRESH = { text: 4.5, large: 3.0, graphic: 3.0 }
const PAIRS = [
  ['brand.onPrimary', 'brand.primary', '主按钮文字', 'text'],
  ['brand.onPrimaryContainer', 'brand.primaryContainer', '主色容器文字', 'text'],
  ['brand.onSecondary', 'brand.secondary', '次要按钮文字', 'text'],
  ['brand.onTertiary', 'brand.tertiary', '奖励/强调按钮文字', 'text'],
  ['brand.onTertiaryContainer', 'brand.tertiaryContainer', '奖励卡片文字', 'text'],
  ['surface.onBackground', 'surface.background', '页面正文', 'text'],
  ['surface.onSurface', 'surface.surface', '卡片正文', 'text'],
  ['surface.onSurfaceVariant', 'surface.surfaceVariant', '次级说明文字', 'text'],
  ['surface.outline', 'surface.surface', '描边/分隔（非文本）', 'graphic'],
  ['semantic.onSuccess', 'semantic.success', '成功提示文字', 'text'],
  ['semantic.error', 'surface.background', '错误文案', 'text'],
  ['semantic.onError', 'semantic.error', '错误按钮文字', 'text'],
  ['semantic.onErrorContainer', 'semantic.errorContainer', '错误提示条文字', 'text'],
  ['semantic.warning', 'surface.background', '警告文案', 'text'],
  ['semantic.onRewardGold', 'semantic.rewardGold', '成就徽章大数字（≥24px）', 'large'],
  ['semantic.routeLine', 'semantic.mapCanvas', '地图路线对比', 'graphic'],
]

/* ---------- 必须成对出现的前景色 ---------- */
const PAIRING = [
  ['brand.primary', 'brand.onPrimary'],
  ['brand.primaryContainer', 'brand.onPrimaryContainer'],
  ['brand.secondary', 'brand.onSecondary'],
  ['brand.tertiary', 'brand.onTertiary'],
  ['semantic.success', 'semantic.onSuccess'],
  ['semantic.error', 'semantic.onError'],
  ['semantic.rewardGold', 'semantic.onRewardGold'],
]

/* 三个阶段都返回「失败计数」；是否退出 / 退出码统一由末尾的 dispatch 决定（all 模式绝不中途退出） */
function cmdContrast(strict) {
  console.log(`\n设计 token 对比度体检　${path.relative(process.cwd(), TOKENS_PATH)}`)
  console.log('─'.repeat(84))
  console.log('场景'.padEnd(18) + '前景'.padEnd(28) + '背景'.padEnd(26) + '对比度   结果')
  console.log('─'.repeat(84))
  let fail = 0
  for (const [fgP, bgP, scene, cls] of PAIRS) {
    const min = THRESH[cls]
    const fg = get(fgP), bg = get(bgP)
    if (!fg || !bg) { console.log(`${scene.padEnd(16)}  缺少 token：${!fg ? fgP : bgP}`); fail++; continue }
    const r = ratio(fg, bg)
    const ok = r >= min
    if (!ok) fail++
    const flag = ok ? (r >= min + 3 ? '✓ AAA' : '✓ AA') : '✗ 未达标'
    const short = s => (s.length > 24 ? s.slice(0, 23) + '…' : s)
    console.log(
      short(scene).padEnd(6) + '  ' +
      short(fgP).padEnd(26) + short(bgP).padEnd(24) +
      r.toFixed(2).padStart(6) + '  ' + `${flag}（需 ${min}）`
    )
  }
  console.log('─'.repeat(84))
  if (fail) {
    console.log(`✗ ${fail} 处不达标。改 tokens.json 后三端（Figma / Compose / CSS）同步，不要只在设计稿里改。`)
    return fail
  }
  console.log('✓ 全部达标，可交付开发。')
  return 0
}

function cmdAudit() {
  const problems = []
  for (const [base, on] of PAIRING) {
    if (get(base) && !get(on)) problems.push(`定义了 ${base} 却缺 ${on}，深色/浅色模式下会出现不可读文字`)
  }
  const scale = T.typography?.scale || {}
  const sizes = new Set(Object.values(scale).map(v => v.size))
  const suggest = []
  // 中文正文行高：body/label 建议 ≥1.35×，title ≥1.3×；display 是大号短数字，按 Material 原生 1.12 即可，不参与判定
  const minRatio = (k) => (k.startsWith('body') || k.startsWith('label') ? 1.35 : k.startsWith('title') ? 1.3 : k.startsWith('headline') ? 1.25 : 0)
  const lh = Object.entries(scale).filter(([k, v]) => {
    const r = minRatio(k)
    return r && v.size && v.lineHeight && v.lineHeight < v.size * r
  })
  if (lh.length) suggest.push(`行高偏紧（Material 原生值，中文长文案建议抬高）：${lh.map(([k, v]) => `${k} ${v.lineHeight}/${v.size}=${(v.lineHeight / v.size).toFixed(2)}`).join('、')}`)

  const minTouch = T.spacing?.touchTargetMin
  if (!minTouch || minTouch < 44) problems.push(`触控目标 ${minTouch}dp 小于 44dp，手指点不准`)

  const budget = T.assetBudget || {}
  for (const [k, v] of Object.entries(budget)) {
    if (k.endsWith('KB') && (typeof v !== 'number' || v <= 0)) problems.push(`资产预算 ${k} 非法：${v}`)
  }

  console.log(`\n设计 token 结构审计`)
  console.log('─'.repeat(60))
  console.log(`颜色 token：${countLeaves(T.color)} 个　字号档位：${sizes.size} 级　间距档位：${(T.spacing?.scale || []).length} 档`)
  console.log(`最小触控：${minTouch}dp　单资源上限：${budget.apkSingleAssetMaxKB}KB　图标基线：${budget.iconOpticalSize}dp / 线宽 ${budget.iconStrokeWidth}`)
  if (!problems.length) {
    console.log('✓ 结构完整，命名成对，预算齐备。')
  } else {
    for (const p of problems) console.log('✗ ' + p)
  }
  for (const s of suggest) console.log('· 建议：' + s)
  return problems.length ? 1 : 0
}
function countLeaves(o) {
  let n = 0
  for (const v of Object.values(o || {})) n += (v && typeof v === 'object') ? countLeaves(v) : 1
  return n
}

/* ---------- actual：从原型 HTML 里抽取"实际用出来的"数值，与 token 比对 ---------- */
const PROTO_DEFAULT = path.resolve(HERE, '..', 'prototype', 'index.html')
function resolveProto() {
  const idx = process.argv.indexOf('--proto')
  if (idx > -1 && process.argv[idx + 1]) return path.resolve(process.argv[idx + 1])
  for (const p of [PROTO_DEFAULT, path.resolve(process.cwd(), 'design/prototype/index.html'),
                   path.resolve(HERE, 'index.html'), path.resolve(HERE, '..', 'index.html')]) {
    if (fs.existsSync(p)) return p
  }
  return null /* 缺原型不在这里退出 —— 交给 dispatch 汇总后统一设退出码 */
}
function tally(arr) {
  const m = new Map()
  for (const v of arr) m.set(v + 'px', (m.get(v + 'px') || 0) + 1)
  return [...m.entries()].sort((a, b) => b[1] - a[1])
}
function cmdActual() {
  const PROTO = resolveProto()
  if (!PROTO) {
    console.log('\n实际数值体检：跳过 —— 找不到原型 index.html（交付包副本无原型属正常）。')
    console.log('  要跑这一阶段：node ' + path.basename(process.argv[1]) + ' actual --proto <design/prototype/index.html 绝对路径>')
    return 2
  }
  const src = fs.readFileSync(PROTO, 'utf8')
  const css = (src.match(/<style[\s\S]*?<\/style>/) || [''])[0].replace(/<\/?style[^>]*>/g, '')

  const rootBlock = (css.match(/:root\s*\{([\s\S]*?)\}/) || ['', ''])[1]
  const vars = [...rootBlock.matchAll(/--([a-z0-9-]+)\s*:\s*([^;]+);/gi)]
    .map(m => [m[1], m[2].trim()])

  const fsSizes = [...css.matchAll(/font-size\s*:\s*(\d+(?:\.\d+)?)px/g)].map(m => parseFloat(m[1]))
  const radii = [...css.matchAll(/border-radius\s*:\s*(\d+(?:\.\d+)?)px/g)].map(m => parseFloat(m[1]))
  const gaps = [...css.matchAll(/(?:^|[;{\s])(?:gap|padding|margin)\s*:\s*([\d.]+)px/g)].map(m => parseFloat(m[1]))
  const hexes = [...css.matchAll(/#[0-9A-Fa-f]{6}\b/g)].map(m => m[0].toUpperCase())

  const tokenHexes = new Set()
  const walkColor = (o) => {
    for (const v of Object.values(o || {})) {
      if (typeof v === 'string' && /^#[0-9A-Fa-f]{6}$/.test(v)) tokenHexes.add(v.toUpperCase())
      else if (v && typeof v === 'object') walkColor(v)
    }
  }
  walkColor(T.color)

  console.log('\n实际数值体检（从原型实现里抽，不是从文档里抄）')
  console.log('─'.repeat(84))
  console.log('token 文件：' + TOKENS_PATH)
  console.log('原型文件　：' + PROTO)
  console.log('─'.repeat(84))
  console.log('\n:root 里实际定义的变量（' + vars.length + ' 个）')
  console.log('  ' + vars.map(([k, v]) => `--${k}:${v}`).join('  '))

  console.log('\n字号（实际出现，按频次）')
  console.log('  ' + tally(fsSizes).slice(0, 14).map(([k, n]) => `${k}×${n}`).join('  '))
  console.log('圆角（实际出现，按频次）')
  console.log('  ' + tally(radii).slice(0, 10).map(([k, n]) => `${k}×${n}`).join('  '))
  console.log('间距（gap/padding/margin，按频次）')
  console.log('  ' + tally(gaps).slice(0, 14).map(([k, n]) => `${k}×${n}`).join('  '))

  const outside = [...new Set(hexes)].filter(h => !tokenHexes.has(h)).sort()
  console.log('\n不在 token 里的颜色（' + outside.length + ' 个）：' + (outside.join(' ') || '无'))
  if (outside.length) {
    console.log('  · 这些是原型里的过渡色/临时色。若要回收，请先在 tokens.json 里命名再替换，不要让开发照截图吸色。')
  }
  console.log('\n触控目标：' + (T.spacing?.touchTargetMin ?? '未定义') + 'dp（原型按钮实测高度见下）')
  const btnH = [...css.matchAll(/\.btn[^{]*\{[^}]*height\s*:\s*(\d+)px/g)].map(m => +m[1])
  if (btnH.length) console.log('  .btn 高度：' + [...new Set(btnH)].sort((a, b) => a - b).join(' / ') + ' px')
  console.log('─'.repeat(84))
  return 0
}

/* ---------- 统一出口：所有阶段跑完、汇总后一次设退出码（all 绝不在中途退出） ----------
 * 退出码：0 全过；1 有阶段失败（--strict 时 contrast 未达标也计失败）；2 阶段被跳过（缺原型等）。
 */
const cmd = process.argv[2] || 'all'
const strict = process.argv.includes('--strict')
const results = []
function runStage(name, fn) {
  const before = process.exitCode
  process.exitCode = undefined
  const rc = fn()
  results.push({ name, rc })
  if (before !== undefined) process.exitCode = before /* 尊重前置阶段已设的退出码 */
}
if (cmd === 'contrast') runStage('contrast', () => cmdContrast(strict))
else if (cmd === 'audit') runStage('audit', cmdAudit)
else if (cmd === 'actual') runStage('actual', cmdActual)
else {
  runStage('contrast', () => cmdContrast(strict))
  runStage('audit', cmdAudit)
  runStage('actual', cmdActual)
  const fail = results.filter(r => r.rc === 1)
  const skip = results.filter(r => r.rc === 2)
  console.log(`\n汇总：${results.length - fail.length - skip.length}/${results.length} 阶段通过` +
    (fail.length ? `，未通过：${fail.map(r => r.name).join('、')}` : '') +
    (skip.length ? `，跳过：${skip.map(r => r.name).join('、')}` : ''))
}
const worst = results.reduce((m, r) => (r.rc === 1 ? 1 : r.rc === 2 && m === 0 ? 2 : m), 0)
if (worst) process.exitCode = worst
