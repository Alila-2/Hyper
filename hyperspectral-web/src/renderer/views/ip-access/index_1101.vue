<template>
    <div class="main-container1">
        <iframe id="ipFrame" src="http://192.168.1.121/" frameborder="0" width="100%" height="100%"></iframe>

        <!-- 专门遮挡logo的覆盖层 -->
        <div class="logo-overlay"></div>

        <button @click="autoCapture" class="capture-btn" :disabled="isCapturing">
            {{ isCapturing ? '拍摄中...' : '一键拍摄' }}
        </button>
    </div>
</template>

<script>
    import axios from "axios";

    export default {
        name: "IpFrameView",
        data() {
            return {
                isCapturing: false
            }
        },
        methods: {
            async autoCapture() {
                if (this.isCapturing) return;

                this.isCapturing = true;
                const baseURL = "http://192.168.1.121:8020";

                try {
                    this.$message.info("开始成像");

                    // 1. 切换到彩色相机
                    await axios.post(`${baseURL}/camera/change`, null, {
                        params: { camera: "color" }
                    });

                    await this.delay(4000);

                    // 2. 彩色拍照
                    await axios.put(`${baseURL}/camera/colorCamera`, null, {
                        params: { operate: "snapshot" }
                    });
                    // 3. 切换到高光谱相机
                    await axios.post(`${baseURL}/camera/change`, null, {
                        params: { camera: "spectral" }
                    });

                    // 4. 居中
                    await axios.put(`${baseURL}/camera/spectralCamera`, null, {
                        params: { operate: "center" }
                    });
                    await this.delay(3000);

                    // 5. 高光谱拍摄
                    await axios.put(`${baseURL}/camera/spectralCamera`, null, {
                        params: { operate: "start" }
                    });

                    

                } catch (error) {
                    console.error("❌ 拍摄出错:", error);
                    let errorMsg = "自动拍摄失败";

                    if (error.response) {
                        errorMsg = `服务器错误: ${error.response.status}`;
                    } else if (error.request) {
                        errorMsg = "网络错误：无法连接到相机服务";
                    } else {
                        errorMsg = `请求错误: ${error.message}`;
                    }

                    this.$message.error(errorMsg);
                } finally {
                    this.isCapturing = false;
                }
            },

            delay(ms) {
                return new Promise(resolve => setTimeout(resolve, ms));
            }
        }
    };
</script>

<style scoped>
    .main-container1 {
        flex: 1;
        display: flex;
        justify-content: center;
        align-items: center;
        padding: 0;
        position: relative;
        height: 100vh;
        overflow: hidden;
        background: #f5f5f5; /* 设置背景色以匹配遮罩 */
    }

    iframe {
        width: 100%;
        height: 100%;
        border: 0;
        border-radius: 0;
    }

    .logo-overlay {
        position: absolute;
        top: 0px; /* 根据logo实际位置调整 */
        left: 15px; /* 根据logo实际位置调整 */
        width: 180px; /* 根据logo大小调整 */
        height: 35px; /* 根据logo大小调整 */
        background: #08132a; /* 与背景色一致 */
        /*border: 1px solid #d0d0d0; /* 可选边框，使过渡更自然 */
        pointer-events: none; /* 重要：让鼠标事件穿透到iframe */
        z-index: 10;
        border-radius: 4px; /* 可选圆角 */
        box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1); /* 可选阴影，使过渡更自然 */
    }

    .capture-btn {
        position: absolute;
        bottom: 40px;
        left: 50%;
        transform: translateX(-50%);
        z-index: 1000;
        padding: 14px 32px;
        border: none;
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        color: white;
        border-radius: 12px;
        cursor: pointer;
        font-weight: 600;
        font-size: 16px;
        box-shadow: 0 6px 20px rgba(0, 0, 0, 0.3);
        transition: all 0.3s ease;
        min-width: 120px;
    }

        .capture-btn:hover:not(:disabled) {
            transform: translateX(-50%) translateY(-3px);
            box-shadow: 0 8px 25px rgba(0, 0, 0, 0.4);
            background: linear-gradient(135deg, #5a67d8 0%, #6b46c1 100%);
        }

        .capture-btn:disabled {
            opacity: 0.6;
            cursor: not-allowed;
            transform: translateX(-50%);
        }

        .capture-btn:active:not(:disabled) {
            transform: translateX(-50%) translateY(-1px);
        }
</style>
