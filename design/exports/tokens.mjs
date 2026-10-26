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
const TOKENS_PATH = path.resolve(HERE, '..', 'tokens', 'tokens.json')
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

/* ---------- 需要检查的配对 ---------- */
/* [前景 token, 背景 token, 场景, 最低要求] */
const PAIRS = [
  ['brand.onPrimary', 'brand.primary', '主按钮文字', 4.5],
  ['brand.onPrimaryContainer', 'brand.primaryContainer', '主色容器文字', 4.5],
  ['brand.onSecondary', 'brand.secondary', '次要按钮文字', 4.5],
  ['brand.onTertiary', 'brand.tertiary', '奖励/强调按钮文字', 4.5],
  ['brand.onTertiaryContainer', 'brand.tertiaryContainer', '奖励卡片文字', 4.5],
  ['surface.onBackground', 'surface.background', '页面正文', 4.5],
  ['surface.onSurface', 'surface.surface', '卡片正文', 4.5],
  ['surface.onSurfaceVariant', 'surface.surfaceVariant', '次级说明文字', 4.5],
  ['surface.outline', 'surface.surface', '描边/分隔（非文本）', 3.0],
  ['semantic.onSuccess', 'semantic.success', '成功提示文字', 4.5],
  ['semantic.error', 'surface.background', '错误文案', 4.5],
  ['semantic.onError', 'semantic.error', '错误按钮文字', 4.5],
  ['semantic.onErrorContainer', 'semantic.errorContainer', '错误提示条文字', 4.5],
  ['semantic.warning', 'surface.background', '警告文案', 4.5],
  ['semantic.onRewardGold', 'semantic.rewardGold', '成就徽章文字', 4.5],
  ['semantic.routeLine', 'semantic.mapCanvas', '地图路线对比', 3.0],
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

function cmdContrast(strict) {
  console.log(`\n设计 token 对比度体检　${path.relative(process.cwd(), TOKENS_PATH)}`)
  console.log('─'.repeat(84))
  console.log('场景'.padEnd(18) + '前景'.padEnd(28) + '背景'.padEnd(26) + '对比度   结果')
  console.log('─'.repeat(84))
  let fail = 0
  for (const [fgP, bgP, scene, min] of PAIRS) {
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
    process.exit(strict ? 1 : 0)
  }
  console.log('✓ 全部达标，可交付开发。')
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
    process.exitCode = 1
  }
  for (const s of suggest) console.log('· 建议：' + s)
}
function countLeaves(o) {
  let n = 0
  for (const v of Object.values(o || {})) n += (v && typeof v === 'object') ? countLeaves(v) : 1
  return n
}

const cmd = process.argv[2] || 'all'
const strict = process.argv.includes('--strict')
if (cmd === 'contrast') cmdContrast(strict)
else if (cmd === 'audit') cmdAudit()
else { cmdContrast(strict); cmdAudit() }
