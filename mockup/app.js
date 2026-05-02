/**
 * 文件用途：小程序页面展示工具 - 下载功能脚本
 * 功能说明：使用html2canvas将iPhone页面截图为透明背景PNG并下载
 * 作者：AI Assistant
 * 创建日期：2026-04-07
 */

/**
 * 下载指定手机页面为透明背景PNG
 * @param {string} containerId - 手机容器的DOM ID
 * @param {string} pageName - 页面名称，用于文件命名
 */
function downloadPhone(containerId, pageName) {
  var container = document.getElementById(containerId);
  if (!container) {
    alert('未找到页面容器: ' + containerId);
    return;
  }

  // 找到下载按钮，显示加载状态（兼容小程序和Web两种容器）
  var wrapper = container.closest('.phone-wrapper') || container.closest('.web-wrapper');
  var btn = wrapper ? wrapper.querySelector('.download-btn') : null;
  var originalText = btn ? btn.innerHTML : '';
  if (btn) {
    btn.innerHTML = '<span class="btn-loading"></span>生成中...';
    btn.disabled = true;
  }

  // 使用html2canvas截图，设置透明背景
  html2canvas(container, {
    backgroundColor: null, // 透明背景
    scale: 3,             // 3倍分辨率，保证PPT清晰度
    useCORS: true,
    allowTaint: true,
    logging: false
  }).then(function(canvas) {
    // 创建下载链接
    var link = document.createElement('a');
    link.download = pageName + '_页面.png';
    link.href = canvas.toDataURL('image/png');
    link.click();

    // 恢复按钮状态
    btn.innerHTML = originalText;
    btn.disabled = false;
  }).catch(function(err) {
    console.error('截图失败:', err);
    alert('下载失败，请重试');
    btn.innerHTML = originalText;
    btn.disabled = false;
  });
}
