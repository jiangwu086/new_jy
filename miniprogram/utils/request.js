/**
 * utils/request.js
 * 统一封装 wx.request：
 *   - 自动按环境切换 BASE_URL（开发/体验版 → 本机后端；正式版 → 线上域名）
 *   - 自动携带 Authorization: Bearer <token>
 *   - 统一解析 { code, msg, data } 响应结构
 *   - 401 时自动跳转登录页
 *   - 无网络时弹出离线提示，支持一键重试
 *   - 请求失败（网络异常/超时）时提供重试 Modal
 */

// ── 环境自动检测 ──────────────────────────────────────────────
// envVersion: 'develop'(开发版) | 'trial'(体验版) | 'release'(正式版)
const { envVersion } = wx.getAccountInfoSync().miniProgram;
const { platform }  = wx.getSystemInfoSync();   // 'devtools' | 'ios' | 'android'

// 开发者工具运行在 Mac 本机，直接用 127.0.0.1；真机调试时改为 Mac 局域网 IP
const DEV_URL = platform === 'devtools'
  ? 'http://127.0.0.1:8080'
  : 'http://192.168.31.114:8080';   // ← 真机调试时改成 Mac 当前局域网 IP

// 生产域名 — 已部署到 36.143.196.178，nginx 反代 + DigiCert HTTPS 证书
// ⚠️ 微信公众平台「开发-开发管理-服务器域名」必须把以下三项都配上 jysafety.yaozhifang.com：
//   request合法域名:     https://jysafety.yaozhifang.com
//   uploadFile合法域名:  https://jysafety.yaozhifang.com
//   downloadFile合法域名: https://jysafety.yaozhifang.com
const PROD_URL = 'https://jysafety.yaozhifang.com';

// envVersion === 'develop' → 用本机/局域网（仅开发者工具/Mac 调试）
// envVersion === 'trial' / 'release' → 体验版和正式版都连线上 HTTPS 域名
const BASE_URL = envVersion === 'develop' ? DEV_URL : PROD_URL;

// ── 网络状态缓存（app.js 中实时维护） ─────────────────────────
// 避免每次请求前都异步调用 getNetworkType，降低延迟
let _isConnected = true;

/**
 * 由 app.js 调用，实时同步网络状态
 */
function setNetworkConnected(connected) {
  _isConnected = connected;
}

/**
 * 弹出离线提示，用户点击"重试"后重新发起请求
 * @returns {Promise} 用户确认重试 → resolve；取消 → reject
 */
function showOfflineModal() {
  return new Promise((resolve, reject) => {
    wx.showModal({
      title: '网络不可用',
      content: '当前无网络连接，请检查 Wi-Fi 或移动数据后重试',
      confirmText: '重试',
      cancelText: '取消',
      success(res) {
        res.confirm ? resolve() : reject({ message: '网络不可用', offline: true });
      },
      fail() { reject({ message: '网络不可用', offline: true }); }
    });
  });
}

/**
 * 弹出请求失败重试 Modal（超时 / 服务器异常）
 */
function showRetryModal(msg) {
  return new Promise((resolve, reject) => {
    wx.showModal({
      title: '请求失败',
      content: msg || '网络异常，请稍后重试',
      confirmText: '重试',
      cancelText: '取消',
      success(res) {
        res.confirm ? resolve() : reject({ message: msg });
      },
      fail() { reject({ message: msg }); }
    });
  });
}

/**
 * 核心请求方法（含离线检测 + 失败重试）
 * @param {string} method   - HTTP 方法 (GET/POST/PUT/DELETE)
 * @param {string} url      - 接口路径，如 /api/v1/auth/wx-login
 * @param {object} data     - 请求体 / query 参数
 * @param {object} options  - { noAuth, noRetry } noRetry=true 不弹重试框
 * @returns {Promise<{code, msg, data}>}
 */
function request(method, url, data = {}, options = {}) {
  // 离线快速失败：先检查缓存状态，再异步确认一次
  const doRequest = () => new Promise((resolve, reject) => {
    const token = wx.getStorageSync('token');
    const header = { 'Content-Type': 'application/json' };
    if (token && !options.noAuth) {
      header['Authorization'] = 'Bearer ' + token;
    }

    wx.request({
      url: BASE_URL + url,
      method,
      data,
      header,
      timeout: 10000,
      success(res) {
        if (res.statusCode === 401) {
          wx.removeStorageSync('token');
          wx.removeStorageSync('isLogin');
          wx.navigateTo({ url: '/pages/login/login' });
          reject({ message: '登录已过期，请重新登录' });
          return;
        }
        const body = res.data;
        if (body && body.code === 200) {
          resolve(body);
        } else {
          reject({ message: (body && body.msg) || '请求失败' });
        }
      },
      fail(err) {
        const msg = (err && err.errMsg) || '';
        const errMsg = msg.includes('timeout')
          ? '请求超时，请检查网络连接'
          : '网络异常，请检查网络连接';
        reject({ message: errMsg, networkError: true });
      }
    });
  });

  // 带离线判断 + 重试的外层包装
  const runWithRetry = () => {
    // 如果缓存状态为离线，先确认再尝试
    if (!_isConnected) {
      return showOfflineModal().then(() => doRequest());
    }
    return doRequest().catch(err => {
      // 网络错误且允许重试
      if (err.networkError && !options.noRetry) {
        return showRetryModal(err.message).then(() => runWithRetry());
      }
      return Promise.reject(err);
    });
  };

  return runWithRetry();
}

const get  = (url, data, opts) => request('GET',    url, data, opts);
const post = (url, data, opts) => request('POST',   url, data, opts);
const put  = (url, data, opts) => request('PUT',    url, data, opts);
const del  = (url, data, opts) => request('DELETE', url, data, opts);

/**
 * 上传文件
 * @param {string} url       接口路径，如 /api/v1/checkin/upload-photo
 * @param {string} filePath  本地临时文件路径（wxfile://... 或 http://tmp/...）
 * @param {string} name      表单字段名（默认 file）
 * @returns {Promise<{url: string}>}  上传成功后返回 data 对象
 */
function uploadFile(url, filePath, name = 'file') {
  return new Promise((resolve, reject) => {
    const token = wx.getStorageSync('token');
    wx.uploadFile({
      url: BASE_URL + url,
      filePath,
      name,
      header: token ? { 'Authorization': 'Bearer ' + token } : {},
      timeout: 30000,
      success(res) {
        try {
          const body = JSON.parse(res.data);
          if (body && body.code === 200) resolve(body.data || {});
          else reject(new Error((body && body.msg) || '上传失败'));
        } catch (e) {
          reject(new Error('上传响应解析失败'));
        }
      },
      fail(err) {
        reject(new Error((err && err.errMsg) || '上传失败'));
      }
    });
  });
}

/**
 * 把后端返回的相对路径补全为完整 URL，专供 <image src> / <video> 等需要绝对地址的标签使用。
 * 已经是 http(s) 开头的原样返回，wxfile/data 等本地资源也不动。
 */
function resolveUrl(url) {
  if (!url) return ''
  if (typeof url !== 'string') return ''
  if (/^(https?:|wxfile:|data:|http:\/\/tmp\/|cloud:\/\/)/i.test(url)) return url
  if (url.startsWith('/')) return BASE_URL + url
  return url
}

module.exports = { request, get, post, put, del, uploadFile, resolveUrl, setNetworkConnected, BASE_URL };
