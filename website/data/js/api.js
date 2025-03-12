async function loadRandomContent() {
  try {
    const response = await fetch("https://api.vvhan.com/api/ian/rand", {
      mode: "cors", // 尝试启用跨域支持
    });
    if (!response.ok) {
      throw new Error(`HTTP 错误：${response.status}`);
    }

    // 根据实际格式处理 API 数据
    const randomText = await response.text();

    // 更新页面内容
    document.getElementById("randomContent").innerHTML = `
      <h2>
        <div class="circle">
          <span></span> <span></span> <span></span>
        </div>
        ${randomText}
      </h2>
    `;
  } catch (error) {
    console.error("加载随机内容失败:", error);
    document.getElementById("randomContent").innerHTML = `
      <h2>
        <div class="circle">
          <span></span> <span></span> <span></span>
        </div>
        加载失败，请稍后再试。
      </h2>
    `;
  }
}

// 页面加载时调用函数
window.onload = loadRandomContent;
