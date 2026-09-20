<template>
    <div class="fusion-container">
        <!-- 标题 -->
        <h2 class="fusion-title">融合成像</h2>

        <!-- 控制区：按钮 + 错误提示 -->
        <div class="fusion-control">
            <button class="fusion-btn"
                    @click="startFusion"
                    :disabled="isLoading">
                <span v-if="isLoading" class="loading-icon">⏳</span>
                {{ isLoading ? '融合中...' : '融合成像可视化' }}
            </button>
            <!-- 错误提示 -->
            <div class="error-message" v-if="errorMsg">
                <div class="error-icon">⚠️</div>
                {{ errorMsg }}
            </div>
        </div>

        <!-- 图片展示框 -->
        <div class="image-display-section">
            <div class="image-container" v-if="imageUrl || isLoading">
                <div class="image-frame" :class="{ 'loading': isLoading }">
                    <!-- 加载中提示 -->
                    <div v-if="isLoading && !imageUrl" class="loading-state">
                        <div class="spinner"></div>
                        <p class="loading-text">正在处理图像融合</p>
                        <p class="loading-subtext">请耐心等待...</p>
                    </div>
                    <!-- 融合后图片 -->
                    <img v-if="imageUrl"
                         :src="imageUrl"
                         alt="融合后的图像"
                         class="fusion-image"
                         @load="handleImageLoad"
                         @error="handleImageError">
                </div>
            </div>

            <!-- 空状态 -->
            <div v-else class="empty-state">
                <div class="empty-icon">🖼️</div>
                <h3>等待融合结果</h3>
                <p>点击上方按钮开始图像融合处理</p>
            </div>

            <!-- 结果信息 -->
            <div class="result-info" v-if="imageUrl">
                <div class="info-item">
                    <span class="info-label">图像尺寸:</span>
                    <span class="info-value">{{ imageDimensions.width }} × {{ imageDimensions.height }}</span>
                </div>
                <div class="info-item">
                    <span class="info-label">处理时间:</span>
                    <span class="info-value">{{ processingTime }}秒</span>
                </div>
                <button class="download-btn" @click="downloadImage">
                    📥 下载图像
                </button>
            </div>
        </div>
    </div>
</template>

<script>
    import axios from 'axios'

    export default {
        data() {
            return {
                isLoading: false,
                errorMsg: '',
                imageUrl: '',
                processingTime: 0,
                imageDimensions: { width: 0, height: 0 },
                startTime: 0
            }
        },
        methods: {
            async startFusion() {
                this.errorMsg = ''
                this.imageUrl = ''
                this.isLoading = true
                this.startTime = Date.now()

                try {
                    const response = await axios.post('http://localhost:8000/fusion', {}, {
                        headers: { 'Content-Type': 'application/json' },
                        timeout: 300000
                    })

                    if (response.data && response.data.image_url) {
                        this.imageUrl = response.data.image_url
                        this.processingTime = ((Date.now() - this.startTime) / 1000).toFixed(2)
                    } else {
                        throw new Error('后端未返回有效的图片链接')
                    }

                } catch (error) {
                    if (error.response) {
                        this.errorMsg = `融合失败：${error.response.data || '服务器内部错误'}`
                    } else if (error.request) {
                        this.errorMsg = '网络错误：请检查后端服务是否正常'
                    } else {
                        this.errorMsg = `融合失败：${error.message}`
                    }
                    console.error('融合请求错误详情：', error)
                } finally {
                    this.isLoading = false
                }
            },

            handleImageLoad() {
                console.log('融合图片加载成功')
                const img = new Image()
                img.onload = () => {
                    this.imageDimensions = {
                        width: img.width,
                        height: img.height
                    }
                }
                img.src = this.imageUrl
            },

            handleImageError() {
                this.errorMsg = '图片加载失败，请点击按钮重新融合'
                this.imageUrl = ''
            },

            downloadImage() {
                if (!this.imageUrl) return
                const link = document.createElement('a')
                link.href = this.imageUrl
                link.download = `fusion_result_${new Date().getTime()}.png`
                document.body.appendChild(link)
                link.click()
                document.body.removeChild(link)
            }
        }
    }
</script>

<style scoped>
    .fusion-container {
        max-width: 1000px;
        margin: 0 auto;
        padding: 30px;
        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
    }

    .fusion-title {
        color: #2c3e50;
        text-align: center;
        margin-bottom: 40px;
        font-size: 2.2em;
        font-weight: 700;
    }

    /* 控制区域 */
    .fusion-control {
        text-align: center;
        margin-bottom: 40px;
    }

    .fusion-btn {
        padding: 16px 40px;
        font-size: 1.1em;
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        color: white;
        border: none;
        border-radius: 12px;
        cursor: pointer;
        transition: all 0.3s ease;
        font-weight: 600;
        box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
    }

        .fusion-btn:disabled {
            opacity: 0.7;
            cursor: not-allowed;
            transform: none;
        }

        .fusion-btn:hover:not(:disabled) {
            transform: translateY(-2px);
            box-shadow: 0 6px 20px rgba(102, 126, 234, 0.5);
        }

    .loading-icon {
        margin-right: 8px;
    }

    .error-message {
        display: inline-flex;
        align-items: center;
        gap: 8px;
        background: #fee;
        border: 1px solid #f5c6cb;
        color: #721c24;
        padding: 12px 20px;
        border-radius: 8px;
        margin-top: 15px;
        font-weight: 500;
    }

    /* 图像显示区域 */
    .image-display-section {
        background: white;
        border-radius: 16px;
        padding: 30px;
        box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
        border: 1px solid #e9ecef;
    }

    .image-container {
        margin-bottom: 25px;
    }

    .image-frame {
        width: 512px;
        height: 512px;
        margin: 0 auto;
        border: 3px solid #e9ecef;
        border-radius: 12px;
        display: flex;
        align-items: center;
        justify-content: center;
        overflow: hidden;
        background: #f8f9fa;
        transition: all 0.3s ease;
    }

        .image-frame.loading {
            border-color: #667eea;
            background: #f0f4ff;
        }

    .loading-state {
        text-align: center;
    }

    .spinner {
        width: 40px;
        height: 40px;
        border: 4px solid #e9ecef;
        border-top: 4px solid #667eea;
        border-radius: 50%;
        animation: spin 1s linear infinite;
        margin: 0 auto 15px;
    }

    @keyframes spin {
        0% {
            transform: rotate(0deg);
        }

        100% {
            transform: rotate(360deg);
        }
    }

    .loading-text {
        color: #495057;
        font-weight: 600;
        margin-bottom: 5px;
    }

    .loading-subtext {
        color: #6c757d;
        font-size: 0.9em;
    }

    .empty-state {
        text-align: center;
        padding: 60px 20px;
        color: #6c757d;
    }

    .empty-icon {
        font-size: 4em;
        margin-bottom: 20px;
        opacity: 0.7;
    }

    .empty-state h3 {
        margin: 0 0 10px 0;
        font-size: 1.4em;
        color: #495057;
    }

    .empty-state p {
        margin: 0;
        font-size: 1em;
    }

    .fusion-image {
        width: 100%;
        height: 100%;
        object-fit: cover;
    }

    /* 结果信息 */
    .result-info {
        display: grid;
        grid-template-columns: 1fr 1fr auto;
        gap: 20px;
        align-items: center;
        padding-top: 20px;
        border-top: 1px solid #e9ecef;
    }

    .info-item {
        display: flex;
        flex-direction: column;
        gap: 5px;
    }

    .info-label {
        color: #6c757d;
        font-size: 0.9em;
        font-weight: 500;
    }

    .info-value {
        color: #495057;
        font-weight: 600;
        font-size: 1.1em;
    }

    .download-btn {
        padding: 12px 24px;
        background: #28a745;
        color: white;
        border: none;
        border-radius: 8px;
        cursor: pointer;
        font-weight: 600;
        transition: all 0.3s ease;
    }

        .download-btn:hover {
            background: #218838;
            transform: translateY(-1px);
        }

    /* 响应式设计 */
    @media (max-width: 768px) {
        .fusion-container {
            padding: 20px;
        }

        .image-frame {
            width: 100%;
            height: 300px;
            max-width: 400px;
        }

        .result-info {
            grid-template-columns: 1fr;
            gap: 15px;
            text-align: center;
        }

        .fusion-title {
            font-size: 1.8em;
        }
    }
</style>