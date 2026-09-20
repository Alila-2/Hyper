<template>
    <div id="app">
        <header>
            <h1>Vue IP访问工具</h1>
            <p class="description">通过此Vue组件，您可以在当前页面内直接访问指定的IP地址或网站，而无需打开新标签页。</p>
        </header>

        <div class="ip-controls">
            <input type="text" class="ip-input" v-model="ipAddress" placeholder="输入IP地址或URL (例如: 192.168.1.1)" @keyup.enter="visitIP">
            <button class="btn" @click="visitIP">
                <i>🔍</i> 访问IP
            </button>
            <button class="btn" @click="clearViewer">
                <i>🗑️</i> 清除
            </button>
        </div>

        <div class="quick-links">
            <div class="quick-link" @click="setQuickIP('192.168.1.123')">路由器管理 (192.168.1.123)</div>
            <div class="quick-link" @click="setQuickIP('192.168.0.1')">路由器管理 (192.168.0.1)</div>
            <div class="quick-link" @click="setQuickIP('10.0.0.1')">网络设备 (10.0.0.1)</div>
            <div class="quick-link" @click="setQuickIP('http://www.baidu.com')">示例网站</div>
        </div>

        <div class="viewer-container">
            <div class="viewer-header">
                <div class="viewer-title">IP内容查看器 - {{ currentUrl }}</div>
                <div class="viewer-actions">
                    <button class="action-btn" @click="refreshViewer">刷新</button>
                    <button class="action-btn" @click="openInNewTab">在新标签页打开</button>
                </div>
            </div>
            <div class="iframe-container">
                <div class="loading" v-if="loading">
                    <div class="spinner"></div>
                    <p>正在加载内容，请稍候...</p>
                </div>
                <iframe :src="iframeSrc" @load="onIframeLoad" @error="onIframeError"></iframe>
            </div>
        </div>

        <div class="footer">
            <p>© 2023 Vue IP访问工具 | 安全提示：请仅访问您信任的IP地址</p>
        </div>
    </div>
</template>

<script>
export default {
  name: 'App',
  data() {
    return {
       ipAddress: '192.168.1.123',
      iframeSrc: '',
      loading: false,
      currentUrl: '未访问任何页面'
    };
  },
  methods: {
    visitIP() {
      let ip = this.ipAddress.trim();

      if (!ip) {
        alert('请输入有效的IP地址或URL');
        return;
      }

      if (!ip.startsWith('http://') && !ip.startsWith('https://')) {
        ip = 'http://' + ip;
      }

      this.loading = true;
      this.iframeSrc = ip;
      this.currentUrl = ip;
    },

    clearViewer() {
      this.ipAddress = '';
      this.iframeSrc = '';
      this.currentUrl = '未访问任何页面';
    },

    refreshViewer() {
      if (!this.iframeSrc) {
        alert('没有可刷新的内容');
        return;
      }

      this.loading = true;
      // 通过先清空再重新赋值来触发刷新
      const currentSrc = this.iframeSrc;
      this.iframeSrc = '';

      // 使用 $nextTick 确保 DOM 更新后再重新赋值
      this.$nextTick(() => {
        this.iframeSrc = currentSrc;
      });
    },

    openInNewTab() {
      if (!this.iframeSrc) {
        alert('没有可打开的内容');
        return;
      }

      window.open(this.iframeSrc, '_blank');
    },

    setQuickIP(ip) {
      this.ipAddress = ip;
      this.visitIP();
    },

    onIframeLoad() {
      this.loading = false;
    },

    onIframeError() {
      this.loading = false;
      alert('无法加载该IP地址的内容。请检查IP地址是否正确，或目标服务器是否可访问。');
    }
  }
};
</script>

<style>
    /* 这里保留所有样式代码 */
    * {
        margin: 0;
        padding: 0;
        box-sizing: border-box;
        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
    }

    body {
        background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
        min-height: 100vh;
        padding: 20px;
    }

    #app {
        max-width: 1200px;
        margin: 0 auto;
        width: 100%;
    }

    header {
        text-align: center;
        margin-bottom: 30px;
        padding: 20px;
        background: white;
        border-radius: 10px;
        box-shadow: 0 4px 15px rgba(0, 0, 0, 0.1);
    }

    h1 {
        color: #2c3e50;
        margin-bottom: 10px;
    }

    .description {
        color: #7f8c8d;
        max-width: 800px;
        margin: 0 auto;
        line-height: 1.6;
    }

    .ip-controls {
        display: flex;
        justify-content: center;
        margin-bottom: 30px;
        flex-wrap: wrap;
        gap: 15px;
        padding: 20px;
        background: white;
        border-radius: 10px;
        box-shadow: 0 4px 15px rgba(0, 0, 0, 0.1);
    }

    .ip-input {
        flex: 1;
        min-width: 250px;
        padding: 12px 15px;
        border: 2px solid #ddd;
        border-radius: 6px;
        font-size: 16px;
        transition: border-color 0.3s;
    }

        .ip-input:focus {
            border-color: #3498db;
            outline: none;
        }

    .btn {
        padding: 12px 25px;
        background: #3498db;
        color: white;
        border: none;
        border-radius: 6px;
        cursor: pointer;
        font-size: 16px;
        font-weight: 600;
        transition: all 0.3s;
        display: flex;
        align-items: center;
        gap: 8px;
    }

        .btn:hover {
            background: #2980b9;
            transform: translateY(-2px);
            box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
        }

        .btn:active {
            transform: translateY(0);
        }

        .btn i {
            font-size: 18px;
        }

    .viewer-container {
        background: white;
        border-radius: 10px;
        overflow: hidden;
        box-shadow: 0 4px 15px rgba(0, 0, 0, 0.1);
        display: flex;
        flex-direction: column;
        height: 600px;
    }

    .viewer-header {
        padding: 15px 20px;
        background: #2c3e50;
        color: white;
        display: flex;
        justify-content: space-between;
        align-items: center;
    }

    .viewer-title {
        font-weight: 600;
    }

    .viewer-actions {
        display: flex;
        gap: 10px;
    }

    .action-btn {
        background: rgba(255, 255, 255, 0.2);
        color: white;
        border: none;
        border-radius: 4px;
        padding: 6px 12px;
        cursor: pointer;
        transition: background 0.3s;
    }

        .action-btn:hover {
            background: rgba(255, 255, 255, 0.3);
        }

    .iframe-container {
        flex: 1;
        position: relative;
    }

    iframe {
        width: 100%;
        height: 100%;
        border: none;
    }

    .loading {
        position: absolute;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        display: flex;
        justify-content: center;
        align-items: center;
        background: rgba(255, 255, 255, 0.9);
        flex-direction: column;
        gap: 20px;
    }

    .spinner {
        width: 50px;
        height: 50px;
        border: 5px solid #f3f3f3;
        border-top: 5px solid #3498db;
        border-radius: 50%;
        animation: spin 1s linear infinite;
    }

    @keyframes spin {
        0% {
            transform: rotate(0deg);
        }

        100% {
            transform: rotate(360deg);
        }
    }

    .footer {
        text-align: center;
        margin-top: 30px;
        padding: 20px;
        color: #7f8c8d;
        font-size: 14px;
    }

    .quick-links {
        display: flex;
        justify-content: center;
        gap: 15px;
        margin-top: 20px;
        flex-wrap: wrap;
    }

    .quick-link {
        padding: 10px 20px;
        background: #3498db;
        color: white;
        border-radius: 6px;
        cursor: pointer;
        transition: all 0.3s;
    }

        .quick-link:hover {
            background: #2980b9;
            transform: translateY(-2px);
        }

    @media (max-width: 768px) {
        .ip-controls {
            flex-direction: column;
        }

        .viewer-container {
            height: 400px;
        }

        .quick-links {
            flex-direction: column;
            align-items: center;
        }
    }
</style>