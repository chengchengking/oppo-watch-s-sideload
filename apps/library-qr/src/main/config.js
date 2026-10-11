/**
 * 合肥市中心图书馆 · 读者证码文配置
 *
 * 2026-10-11 破解（6月+10月共130+样本交叉验证）码文结构：
 *   JYB + DD + HHMMSS + 0000 + 尾号
 *   DD = 当日日号（6月6日=06、6月14日=14、10月11日=11 三天完全吻合）
 *   HHMMSS = 生成时刻（北京时间）
 *   0000   = 130+ 样本恒为 0000，含义未知，保持常量
 *   尾号   = 服务端随机数 1~6（同一秒出现 1 和 6）
 *
 * 应用为双模自适应：
 *   在线模式 —— 每次向服务器实时取签发码（回执可被后台反查身份，闸机/柜子
 *              任何校验方式都能过），token 缓存 12 小时；
 *   离线模式 —— 网络不可用时按上述结构本地生成，尾号 1~6 轮转。
 *   启动时先离线出码保证有显示，在线拉码成功后自动切换；连续失败 2 次
 *   降级离线，离线期间每 10 个周期重试在线。
 */
export default {
  prefix: 'JYB',
  suffix: '0000',
  tailCycle: 6,
  refreshMs: 3000,

  // ★★★ 个人凭据占位符 —— 填你自己的（从图书馆官方渠道获取），切勿提交真实值到公开仓库
  //     留空时应用以纯离线模式运行（按下方码文结构本地生成）。
  libToken: '',
  openid: '',
  secret: 'TCUWEI2018',
  authUrl: 'https://b.dataesb.com/api/v1/openidAuthorization/uweiVue',
  qrUrl: 'https://b.dataesb.com/api/v1/readerOperation/readerQrCode'
}
