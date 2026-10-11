/**
 * 二维码内容表。
 *
 * 换内容只改这里：每一项是一个 { label, value }。
 *   label —— 屏幕上显示的小字说明（可留空字符串）
 *   value —— 真正编码进二维码的文本（网址 / 文本 / 任意字符串）
 *
 * 手表上点一下屏幕就切到下一条，循环播放。
 * qrcode 组件最高支持 QR 版本 12，即 value 长度上限约 300 字节（纯数字更多，中文更少）。
 */
export default [
  { label: '名片主页', value: 'https://example.com/' },
  { label: '微信号', value: 'weixin://dl/chat' },
  { label: 'WiFi 分享', value: 'WIFI:T:WPA;S:MyHome;P:12345678;;' },
  { label: '纯文本', value: 'Hello Glyphix RTOS Watch' }
]
