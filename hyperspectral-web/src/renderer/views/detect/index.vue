<template>
    <div id="app">
        <div class="wrap">
            <div class="box">
                <!-- 图片1 -->
                <div class="block1">
                    <h2>图片1</h2>
                    <img v-if="imageUrl1"
                         :src="imageUrl1"
                         :class="['image-input', { active: isActive }]"
                         alt="图片1" />
                    <div v-else class="placeholder">
                        暂无图片
                    </div>
                    <el-card class="title-card" v-if="imageUrl1 && !isActive">
                        分辨率<span class="font-bold">128*128</span>， 光谱波段数<span class="font-bold">176</span>， 波段范围<span class="font-bold">300.0-2500.0</span>nm
                    </el-card>
                </div>

                <!-- 图片2 -->
                <div class="block2">
                    <h2>图片2</h2>
                    <img v-if="imageUrl2"
                         :src="imageUrl2"
                         :class="['image-input', { active: isActive }]"
                         alt="图片2" />
                    <div v-else-if="isProcessingImage2" class="loading">
                        正在将ENVI转为PNG...
                    </div>
                    <div v-else class="placeholder">
                        暂无图片
                    </div>
                    <el-card class="title-card" v-if="imageUrl2 && !isActive">
                        分辨率<span class="font-bold">512*512</span>， 光谱波段数<span class="font-bold">1</span>
                    </el-card>
                </div>
            </div>

            <!-- 融合结果 -->
            <div :class="['block3', { active: isActive }]" v-if="isActive">
                <h2>融合结果</h2>
                <div class="image-container">
                    <img v-if="fusionResultUrl"
                         :src="fusionResultUrl"
                         :class="['image-input3', { active: isActive }]"
                         alt="融合结果图片" />
                    <div v-else class="loading">
                        处理中...
                    </div>
                </div>
                <el-card class="title-card" v-if="fusionResultUrl">
                    分辨率<span class="font-bold">512*512</span>，光谱波段数<span class="font-bold">176</span>
                </el-card>
            </div>
        </div>

        <!-- 按钮 -->
        <div class="button-container">
            <el-button type="primary"
                       @click="startCameraScan"
                       :loading="scanLoading"
                       size="large">
                {{ scanLoading ? '扫描中...' : '开始相机扫描' }}
            </el-button>

            <el-button type="success"
                       @click="startFusion"
                       :disabled="!canStartFusion"
                       :loading="fusionLoading"
                       size="large">
                {{ fusionLoading ? '融合中...' : '开始融合' }}
            </el-button>
        </div>

        <!-- 状态提示 -->
        <div class="status-info" v-if="statusMessage">
            <el-alert :title="statusMessage"
                      :type="statusType"
                      :closable="false"
                      show-icon />
        </div>
    </div>
</template>

<script>
    export default {
        data() {
            return {
                isActive: false,
                imageUrl1: "",
                imageUrl2: "",
                fusionResultUrl: "",
                scanLoading: false,
                fusionLoading: false,
                isProcessingImage2: false,
                currentSessionId: null,
                statusMessage: "",
                statusType: "info",
            };
        },
        computed: {
            canStartFusion() {
                return this.imageUrl1 && this.imageUrl2;
            }
        },
        methods: {
            // 开始相机扫描
            async startCameraScan() {
                this.scanLoading = true;
                this.resetImages();
                this.setStatus("开始相机扫描...", "info");

                try {
                    const response = await fetch("http://localhost:8000/start_camera_scan", {
                        method: "POST",
                        headers: {
                            'Content-Type': 'application/json',
                        }
                    });

                    if (!response.ok) {
                        throw new Error("相机扫描启动失败！");
                    }

                    const data = await response.json();

                    this.imageUrl1 = data.image1_url;
                    this.currentSessionId = data.session_id;
                    this.setStatus(data.message, "success");

                    this.startEnviProcessing();

                } catch (error) {
                    console.error("扫描错误:", error);
                    this.setStatus("相机扫描失败！", "error");
                    this.$message.error("相机扫描失败！");
                } finally {
                    this.scanLoading = false;
                }
            },

            // 开始ENVI图片处理
            async startEnviProcessing() {
                if (!this.currentSessionId) return;

                this.isProcessingImage2 = true;

                try {
                    const response = await fetch(`http://localhost:8000/process_envi_images/${this.currentSessionId}`, {
                        method: "POST",
                        headers: {
                            'Content-Type': 'application/json',
                        }
                    });

                    if (!response.ok) {
                        throw new Error("ENVI图片处理失败！");
                    }

                    const data = await response.json();

                    if (data.image2_status === "success" && data.image2_url) {
                        this.imageUrl2 = data.image2_url;
                        this.isProcessingImage2 = false;
                    } else {
                        this.setStatus(`图片2处理失败: ${data.image2_error || '未知错误'}`, "warning");
                        this.isProcessingImage2 = false;
                    }

                    if (this.imageUrl1 && this.imageUrl2) {
                        this.setStatus("图片处理完成，可以开始融合！", "success");
                        this.$message.success("图片处理完成！");
                    }

                } catch (error) {
                    console.error("ENVI处理错误:", error);
                    this.setStatus("ENVI图片处理失败！", "error");
                    this.isProcessingImage2 = false;
                    this.$message.error("ENVI图片处理失败！");
                }
            },

            // 开始融合
            async startFusion() {
                if (!this.canStartFusion) {
                    this.$message.warning("请先完成图片的扫描和处理！");
                    return;
                }

                this.fusionLoading = true;
                this.isActive = true;
                this.setStatus("正在进行图像融合...", "info");

                try {
                    const response = await fetch("http://localhost:8000/start_fusion", {
                        method: "POST",
                        headers: {
                            'Content-Type': 'application/json',
                        }
                    });

                    if (!response.ok) {
                        const errorText = await response.text();
                        throw new Error(`融合失败: ${errorText}`);
                    }

                    const data = await response.json();
                    console.log("融合响应数据:", data);

                    if (data.image_url) {
                        this.fusionResultUrl = data.image_url;
                        this.setStatus("图像融合完成！", "success");
                        this.$message.success("融合完成！");
                    } else {
                        throw new Error("后端未返回融合结果URL");
                    }

                } catch (error) {
                    console.error("融合错误:", error);
                    this.setStatus("图像融合失败！", "error");
                    this.$message.error(`融合失败: ${error.message}`);
                    this.isActive = false;
                } finally {
                    this.fusionLoading = false;
                }
            },

            resetImages() {
                this.imageUrl1 = "";
                this.imageUrl2 = "";
                this.fusionResultUrl = "";
                this.isProcessingImage2 = false;
                this.isActive = false;
                this.currentSessionId = null;
            },

            setStatus(message, type) {
                this.statusMessage = message;
                this.statusType = type;

                if (type !== "error") {
                    setTimeout(() => {
                        if (this.statusMessage === message) {
                            this.statusMessage = "";
                        }
                    }, 5000);
                }
            }
        },

        beforeDestroy() {
            this.resetImages();
        }
    };
</script>

<style scoped>
    .wrap {
        display: flex;
        width: 100%;
        height: 900px;
        background-image: url('../../assets/bg.jpg');  
        background-size: cover;       /* 图片覆盖整个容器 */  
        background-position: center;  /* 图片居中显示 */  
        background-repeat: no-repeat; /* 不重复平铺 */  
        background-attachment: fixed; /* 背景固定，不随滚动移动 */  
        font-family: 'Helvetica Neue', 'Segoe UI', Arial, sans-serif;  
        min-height: 100vh; /* 确保背景至少覆盖整个视口高度 */
    }

    .box {
        flex: 1;
        display: flex;
        flex-wrap: wrap;
        padding: 20px;
    }

    .block1,
    .block2 {
        flex: 1;
        flex-shrink: 0;
        min-width: 250px;
        margin: 15px;
        border: 1px;
        border-radius: 10px;
        border: 5px solid rgb(233, 233, 233);
        padding: 15px;
        display: flex;
        flex-direction: column;
        justify-content: center;
        align-items: center;
    }

    .block3 {
        width: 0;
        height: 0;
        padding: 30px;
        overflow: hidden;
        transition: all ease-in-out 0.3s;
    }

        .block3.active {
            width: 80%;
            height: 100%;
        }

    .image-input {
        max-height: 300px;
        max-width: 100%;
        margin: 10px;
        border-radius: 5px;
        box-shadow: 0 0 10px rgba(0, 0, 0, 0.1);
        object-fit: contain;
    }

        .image-input.active {
            max-height: 200px;
        }

    .image-input3 {
        max-height: 400px;
        max-width: 100%;
        border-radius: 5px;
        box-shadow: 0 0 15px rgba(0, 0, 0, 0.2);
        object-fit: contain;
    }

    .placeholder,
    .loading {
        display: flex;
        justify-content: center;
        align-items: center;
        height: 200px;
        border: 2px dashed #ddd;
        border-radius: 5px;
        color: #999;
        font-size: 16px;
        margin: 10px;
        width: 100%;
    }

    .loading {
        border-color: #409EFF;
        color: #409EFF;
        animation: pulse 1.5s ease-in-out infinite alternate;
    }

    @keyframes pulse {
        from {
            opacity: 0.6;
        }

        to {
            opacity: 1;
        }
    }

    .title-card {
        width: 100%;
        display: flex;
        justify-content: center;
        align-items: center;
        font-size: 16px;
        margin-top: 10px;
    }

    .button-container {
        display: flex;
        flex-direction: row;
        justify-content: center;
        align-items: center;
        gap: 30px;
        margin: 20px;
        min-width: 200px;
        flex-wrap: wrap;
    }

    @media (max-width: 600px) {
        .button-container {
            flex-direction: column;
            gap: 20px;
        }
    }

    .image-container {
        padding: 40px;
        display: flex;
        justify-content: center;
        align-items: center;
        width: 100%;
    }

    .status-info {
        position: fixed;
        bottom: 20px;
        left: 50%;
        transform: translateX(-50%);
        z-index: 1000;
        min-width: 300px;
    }

    .font-bold {
        font-weight: bold;
        color: #409EFF;
    }

    h2 {
        margin-bottom: 20px;
        color: #333;
        font-size: 20px;
    }
</style>
