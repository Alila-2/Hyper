<template>
    <div id="app">
        <header>
            <h1>图像处理服务</h1>
            <p class="description">点击下方按钮访问图像处理服务器的操作界面</p>
        </header>

        <div class="button-container">
            <div class="function-buttons">
                <button class="func-btn primary" @click="visitFixedIP">
                    <i>🖼️</i> 图像处理控制台
                </button>
                <button class="func-btn secondary" @click="refreshViewer">
                    <i>🔄</i> 刷新页面
                </button>
                <button class="func-btn warning" @click="clearViewer">
                    <i>🗑️</i> 清除内容
                </button>
            </div>

            <div class="ip-info">
                <p>当前服务地址: <span class="ip-address">{{ fixedIP }}</span></p>
            </div>
        </div>

        <div class="viewer-container">
            <div class="viewer-header">
                <div class="viewer-title">图像处理服务界面 - {{ currentUrl }}</div>
                <div class="viewer-actions">
                    <button class="action-btn" @click="refreshViewer">
                        <i>🔄</i> 刷新
                    </button>
                    <button class="action-btn" @click="openInNewTab">
                        <i>↗️</i> 新标签页
                    </button>
                </div>
            </div>
            <div class="iframe-container">
                <div class="loading" v-if="loading">
                    <div class="spinner"></div>
                    <p>正在连接图像处理服务，请稍候...</p>
                </div>
                <div class="error-message" v-if="error">
                    <div class="error-icon">❌</div>
                    <h3>无法连接到图像处理服务</h3>
                    <p>请检查网络连接或联系管理员</p>
                    <button class="retry-btn" @click="visitFixedIP">重试</button>
                </div>
                <iframe v-show="!loading && !error"
                        :src="iframeSrc"
                        @load="onIframeLoad"
                        @error="onIframeError"
                        title="图像处理服务界面"></iframe>
            </div>
        </div>

        <div class="footer">
            <p>© 2023 图像处理服务 | 版本 1.0.0</p>
        </div>
    </div>
</template>

<script>
export default {
  name: 'ImageProcessingApp',
  data() {
    return {
      // 固定IP地址直接写在这里
        fixedIP: '192.168.1.123', // 替换为您的实际IP地址
      iframeSrc: '',
      loading: false,
      error: false,
      currentUrl: '未连接'
    };
  },
  methods: {
    // 访问固定IP
    visitFixedIP() {
      this.loading = true;
      this.error = false;

      // 构建完整的URL
      let url = this.fixedIP;
      if (!url.startsWith('http://') && !url.startsWith('https://')) {
        url = 'http://' + url;
      }

      this.iframeSrc = url;
      this.currentUrl = url;
    },

    // 清除内容
    clearViewer() {
      this.iframeSrc = '';
      this.currentUrl = '未连接';
      this.error = false;
    },

    // 刷新页面
    refreshViewer() {
      if (!this.iframeSrc) {
        this.visitFixedIP();
        return;
      }

      this.loading = true;
      this.error = false;

      // 强制刷新 iframe
      const currentSrc = this.iframeSrc;
      this.iframeSrc = '';
      this.$nextTick(() => {
        this.iframeSrc = currentSrc;
      });
    },

    // 在新标签页打开
    openInNewTab() {
      if (!this.iframeSrc) {
        alert('没有可打开的内容');
        return;
      }

      window.open(this.iframeSrc, '_blank');
    },

    // iframe加载完成
    onIframeLoad() {
      this.loading = false;
      this.error = false;
    },

    // iframe加载错误
    onIframeError() {
      this.loading = false;
      this.error = true;
    }
  },
  mounted() {
    // 组件加载后自动访问IP（可选）
    // this.visitFixedIP();
  }
};
</script>

<style>
    * {
        margin: 0;
        padding: 0;
        box-sizing: border-box;
        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
    }

    body {
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
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
        padding: 30px;
        background: rgba(255, 255, 255, 0.95);
        border-radius: 15px;
        box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
        backdrop-filter: blur(10px);
    }

    h1 {
        color: #2c3e50;
        margin-bottom: 15px;
        font-size: 2.5em;
    }

    .description {
        color: #7f8c8d;
        font-size: 1.1em;
        max-width: 600px;
        margin: 0 auto;
        line-height: 1.6;
    }

    .button-container {
        background: rgba(255, 255, 255, 0.95);
        border-radius: 15px;
        padding: 25px;
        margin-bottom: 30px;
        box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
        backdrop-filter: blur(10px);
    }

    .function-buttons {
        display: flex;
        justify-content: center;
        gap: 20px;
        margin-bottom: 20px;
        flex-wrap: wrap;
    }

    .func-btn {
        padding: 15px 30px;
        border: none;
        border-radius: 10px;
        cursor: pointer;
        font-size: 16px;
        font-weight: 600;
        transition: all 0.3s ease;
        display: flex;
        align-items: center;
        gap: 10px;
        min-width: 200px;
        justify-content: center;
    }

        .func-btn.primary {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
        }

        .func-btn.secondary {
            background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
            color: white;
        }

        .func-btn.warning {
            background: linear-gradient(135deg, #ff6b6b 0%, #ee5a52 100%);
            color: white;
        }

        .func-btn:hover {
            transform: translateY(-3px);
            box-shadow: 0 10px 25px rgba(0, 0, 0, 0.2);
        }

        .func-btn:active {
            transform: translateY(-1px);
        }

    .ip-info {
        text-align: center;
        padding: 15px;
        background: rgba(52, 152, 219, 0.1);
        border-radius: 8px;
        margin-top: 15px;
    }

    .ip-address {
        font-weight: bold;
        color: #2c3e50;
        font-family: 'Courier New', monospace;
        background: #f8f9fa;
        padding: 5px 10px;
        border-radius: 4px;
        border: 1px solid #e9ecef;
    }

    .viewer-container {
        background: white;
        border-radius: 15px;
        overflow: hidden;
        box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
        display: flex;
        flex-direction: column;
        height: 600px;
    }

    .viewer-header {
        padding: 15px 25px;
        background: linear-gradient(135deg, #2c3e50 0%, #34495e 100%);
        color: white;
        display: flex;
        justify-content: space-between;
        align-items: center;
    }

    .viewer-title {
        font-weight: 600;
        font-size: 1.1em;
    }

    .viewer-actions {
        display: flex;
        gap: 10px;
    }

    .action-btn {
        background: rgba(255, 255, 255, 0.2);
        color: white;
        border: none;
        border-radius: 6px;
        padding: 8px 15px;
        cursor: pointer;
        transition: background 0.3s;
        display: flex;
        align-items: center;
        gap: 5px;
    }

        .action-btn:hover {
            background: rgba(255, 255, 255, 0.3);
        }

    .iframe-container {
        flex: 1;
        position: relative;
        background: #f8f9fa;
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
        background: rgba(255, 255, 255, 0.95);
        flex-direction: column;
        gap: 20px;
    }

    .spinner {
        width: 60px;
        height: 60px;
        border: 5px solid #f3f3f3;
        border-top: 5px solid #667eea;
        border-radius: 50%;
        animation: spin 1s linear infinite;
    }

    .error-message {
        position: absolute;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        display: flex;
        justify-content: center;
        align-items: center;
        background: rgba(255, 255, 255, 0.95);
        flex-direction: column;
        gap: 20px;
        text-align: center;
    }

    .error-icon {
        font-size: 4em;
        color: #ff6b6b;
    }

    .error-message h3 {
        color: #2c3e50;
        margin-bottom: 10px;
    }

    .error-message p {
        color: #7f8c8d;
        margin-bottom: 20px;
    }

    .retry-btn {
        padding: 12px 25px;
        background: #667eea;
        color: white;
        border: none;
        border-radius: 6px;
        cursor: pointer;
        font-size: 16px;
        font-weight: 600;
        transition: all 0.3s;
    }

        .retry-btn:hover {
            background: #5a6fd8;
            transform: translateY(-2px);
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
        color: rgba(255, 255, 255, 0.8);
        font-size: 14px;
    }

    @media (max-width: 768px) {
        .function-buttons {
            flex-direction: column;
            align-items: center;
        }

        .func-btn {
            width: 100%;
            max-width: 300px;
        }

        .viewer-container {
            height: 500px;
        }

        .viewer-header {
            flex-direction: column;
            gap: 15px;
            text-align: center;
        }
    }
</style>