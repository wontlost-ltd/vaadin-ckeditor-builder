#!/usr/bin/env node
/**
 * 生成应用图标位图产物（favicon.ico + PWA icon.png）。
 *
 * 设计源文件为 src/main/resources/META-INF/resources/icons/icon.svg，
 * 本脚本用等价的几何描述在内存中光栅化，避免为一次性构建引入 sharp/canvas 等原生依赖。
 * 若修改了 icon.svg 的几何参数，必须同步更新下方 SHAPES 常量并重新运行本脚本。
 *
 * 用法：node scripts/gen-favicon.mjs
 */

import { deflateSync } from 'node:zlib';
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const OUT_DIR = join(ROOT, 'src/main/resources/META-INF/resources');

/** 设计画布尺寸，与 icon.svg 的 viewBox 一致。 */
const CANVAS = 512;

/** 每像素超采样倍率，用于获得平滑边缘。 */
const SUPERSAMPLE = 4;

/**
 * 图形元素，坐标系与 icon.svg 完全对应。
 * 依绘制顺序排列，后绘制的覆盖先绘制的。
 */
const SHAPES = [
  // 底板：垂直渐变 #3b82f6 → #2563eb
  { x: 0, y: 0, w: 512, h: 512, r: 112, gradient: [[0x3b, 0x82, 0xf6], [0x25, 0x63, 0xeb]] },
  // 文本行
  { x: 104, y: 146, w: 304, h: 48, r: 24, color: [255, 255, 255], alpha: 0.55 },
  { x: 104, y: 318, w: 200, h: 48, r: 24, color: [255, 255, 255], alpha: 0.55 },
  // I 型文本光标
  { x: 188, y: 146, w: 136, h: 48, r: 24, color: [255, 255, 255] },
  { x: 232, y: 146, w: 48, h: 220, r: 24, color: [255, 255, 255] },
  { x: 188, y: 318, w: 136, h: 48, r: 24, color: [255, 255, 255] },
  // Builder 方点
  { x: 336, y: 318, w: 72, h: 72, r: 24, color: [255, 255, 255] },
];

/**
 * 判断点 (px, py) 是否落在圆角矩形内。
 * 采用把圆角折算为角落圆心距离的标准判定，避免逐段构造路径。
 */
function insideRoundRect(px, py, { x, y, w, h, r }) {
  if (px < x || px > x + w || py < y || py > y + h) return false;
  const radius = Math.min(r, w / 2, h / 2);
  if (radius <= 0) return true;
  // 把点收缩到"角落圆心"构成的内矩形上，超出部分才需要做圆判定
  const cx = Math.min(Math.max(px, x + radius), x + w - radius);
  const cy = Math.min(Math.max(py, y + radius), y + h - radius);
  const dx = px - cx;
  const dy = py - cy;
  return dx * dx + dy * dy <= radius * radius;
}

/**
 * 光栅化为 size×size 的 RGBA 缓冲区。
 * 对每个输出像素做 SUPERSAMPLE² 次子采样并求平均，实现抗锯齿。
 */
function render(size) {
  const rgba = Buffer.alloc(size * size * 4);
  const scale = CANVAS / size;
  const step = 1 / SUPERSAMPLE;
  const samples = SUPERSAMPLE * SUPERSAMPLE;

  for (let py = 0; py < size; py++) {
    for (let px = 0; px < size; px++) {
      let r = 0, g = 0, b = 0, a = 0;

      for (let sy = 0; sy < SUPERSAMPLE; sy++) {
        for (let sx = 0; sx < SUPERSAMPLE; sx++) {
          // 子采样点映射回 512×512 设计坐标系
          const dx = (px + (sx + 0.5) * step) * scale;
          const dy = (py + (sy + 0.5) * step) * scale;

          // 逐层 source-over 合成
          let sr = 0, sg = 0, sb = 0, sa = 0;
          for (const shape of SHAPES) {
            if (!insideRoundRect(dx, dy, shape)) continue;
            const [cr, cg, cb] = shape.gradient
              ? mixGradient(shape, dy)
              : shape.color;
            const ca = shape.alpha ?? 1;
            sr = cr * ca + sr * (1 - ca);
            sg = cg * ca + sg * (1 - ca);
            sb = cb * ca + sb * (1 - ca);
            sa = ca + sa * (1 - ca);
          }

          r += sr; g += sg; b += sb; a += sa;
        }
      }

      const i = (py * size + px) * 4;
      const alpha = a / samples;
      // 存储为非预乘 RGBA：颜色分量需除以覆盖率还原
      rgba[i] = alpha > 0 ? Math.round(r / samples / alpha) : 0;
      rgba[i + 1] = alpha > 0 ? Math.round(g / samples / alpha) : 0;
      rgba[i + 2] = alpha > 0 ? Math.round(b / samples / alpha) : 0;
      rgba[i + 3] = Math.round(alpha * 255);
    }
  }
  return rgba;
}

/** 按纵向位置在渐变两端色之间线性插值。 */
function mixGradient({ y, h, gradient: [from, to] }, dy) {
  const t = Math.min(Math.max((dy - y) / h, 0), 1);
  return [
    Math.round(from[0] + (to[0] - from[0]) * t),
    Math.round(from[1] + (to[1] - from[1]) * t),
    Math.round(from[2] + (to[2] - from[2]) * t),
  ];
}

/** CRC32 查表，用于 PNG 分块校验。 */
const CRC_TABLE = (() => {
  const table = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c;
  }
  return table;
})();

function crc32(buf) {
  let c = -1;
  for (const byte of buf) c = CRC_TABLE[(c ^ byte) & 0xff] ^ (c >>> 8);
  return (c ^ -1) >>> 0;
}

/** 组装一个 PNG 分块（长度 + 类型 + 数据 + CRC）。 */
function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
}

/** 将 RGBA 缓冲区编码为 8 位真彩色 PNG。 */
function encodePng(rgba, size) {
  // 每行前置 filter type 字节 0（None）
  const raw = Buffer.alloc(size * (size * 4 + 1));
  for (let y = 0; y < size; y++) {
    raw[y * (size * 4 + 1)] = 0;
    rgba.copy(raw, y * (size * 4 + 1) + 1, y * size * 4, (y + 1) * size * 4);
  }

  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8;  // 位深
  ihdr[9] = 6;  // 颜色类型：RGBA
  // [10]=压缩方法 [11]=滤波方法 [12]=隔行方式，均为 0

  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

/**
 * 将多个尺寸的 PNG 打包为 ICO。
 * 现代浏览器均支持 ICO 内嵌 PNG，无需退回 BMP 编码。
 */
function encodeIco(entries) {
  const header = Buffer.alloc(6);
  header.writeUInt16LE(0, 0);              // 保留字段
  header.writeUInt16LE(1, 2);              // 类型：1 = 图标
  header.writeUInt16LE(entries.length, 4);

  const dirSize = 16 * entries.length;
  let offset = header.length + dirSize;
  const dir = [];

  for (const { size, png } of entries) {
    const e = Buffer.alloc(16);
    e[0] = size >= 256 ? 0 : size;  // 256 需以 0 表示
    e[1] = size >= 256 ? 0 : size;
    // [2]=调色板数 [3]=保留 均为 0
    e.writeUInt16LE(1, 4);   // 颜色平面
    e.writeUInt16LE(32, 6);  // 每像素位数
    e.writeUInt32LE(png.length, 8);
    e.writeUInt32LE(offset, 12);
    dir.push(e);
    offset += png.length;
  }

  return Buffer.concat([header, ...dir, ...entries.map((e) => e.png)]);
}

// ---- 产物生成 ----

mkdirSync(join(OUT_DIR, 'icons'), { recursive: true });

// favicon.ico：内嵌 16/32/48 三种尺寸，覆盖标签页、书签栏与桌面快捷方式
const icoSizes = [16, 32, 48];
const ico = encodeIco(
  icoSizes.map((size) => ({ size, png: encodePng(render(size), size) })),
);
writeFileSync(join(OUT_DIR, 'favicon.ico'), ico);
console.log(`favicon.ico            ${icoSizes.join('/')}px  ${ico.length} bytes`);

// PWA 母图：Vaadin @PWA 由此自动派生各平台所需尺寸
const pwa = encodePng(render(512), 512);
writeFileSync(join(OUT_DIR, 'icons/icon.png'), pwa);
console.log(`icons/icon.png         512px      ${pwa.length} bytes`);

// 现代浏览器优先使用的高分辨率 PNG favicon
for (const size of [180, 192]) {
  const png = encodePng(render(size), size);
  const name = size === 180 ? 'apple-touch-icon.png' : 'icons/icon-192.png';
  writeFileSync(join(OUT_DIR, name), png);
  console.log(`${name.padEnd(22)} ${size}px      ${png.length} bytes`);
}
