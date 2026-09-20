<template>
    <div class="detection-root">
        <div class="detection-container">
            <h2 class="page-title">目标探测识别</h2>

            <div class="viewer">
                <!-- 伪彩图展示 -->
                <div class="asset-container pseudo-container">
                    <!-- 标题在图片正上方 -->
                    <div class="controls top-controls">
                        <h3 class="asset-title">伪彩图</h3>
                    </div>

                    <!-- 图片区域 -->
                    <div class="image-wrapper large-image">
                        <div v-if="!pseudoImageUrl" class="placeholder">
                            <p>点击下方按钮加载数据</p>
                        </div>
                        <img v-else
                             :src="pseudoImageUrl"
                             alt="伪彩图"
                             class="display-image large" />
                    </div>

                    <!-- 控件在图片正下方 -->
                    <div class="controls bottom-controls">
                        <button @click="loadPseudocolor"
                                class="action-button"
                                :disabled="isPseudoLoading">
                            {{ isPseudoLoading ? '加载中...' : '加载数据' }}
                        </button>

                        <!-- 额外信息 -->
                        <div v-if="pseudoImageUrl" class="pseudo-additional-info">
                            <div class="info-item">
                                <span class="info-label">图像尺寸:</span>
                                <span class="info-value">{{ imageDimensions.width }} × {{ imageDimensions.height }}</span>
                            </div>
                            <div class="info-item">
                                <span class="info-label">加载时间:</span>
                                <span class="info-value">{{ loadTime }}秒</span>
                            </div>
                            <button @click="downloadPseudoImage" class="secondary-button">
                                📥 下载图像
                            </button>
                        </div>
                    </div>
                </div>

                <!-- 材料选择检测 -->
                <div class="asset-container detection-container">
                    <!-- 标题在图片正上方 -->
                    <div class="controls top-controls">
                        <h3 class="asset-title">材料选择检测</h3>
                    </div>

                    <!-- 图片区域 -->
                    <div class="image-wrapper">
                        <div v-if="!detectionImageUrl" class="placeholder">
                            <p>选择材料并点击下方按钮执行检测</p>
                        </div>
                        <img v-else
                             :src="detectionImageUrl"
                             alt="材料检测结果"
                             class="display-image" />
                    </div>

                    <!-- 控件在图片正下方 -->
                    <div class="controls bottom-controls">
                        <!-- 材料选择区域 -->
                        <div class="material-selection">
                            <h4>请选择伪装材料：</h4>
                            <div class="material-grid">
                                <div v-for="material in materials"
                                     :key="material.code"
                                     class="material-item">
                                    <label class="material-checkbox">
                                        <input type="checkbox"
                                               :value="material.code"
                                               v-model="selectedMaterials" />
                                        <span class="color-indicator"
                                              :style="{ backgroundColor: material.color }"></span>
                                        {{ material.name }}
                                    </label>
                                </div>
                            </div>
                        </div>

                        <!-- 开始检测按钮 -->
                        <button @click="runMaterialDetection"
                                class="action-button"
                                :disabled="isDetecting || selectedMaterials.length === 0">
                            {{ isDetecting ? '检测中...' : '执行材料检测' }}
                        </button>

                        <!-- 调试信息 - 修改样式 -->
                        <div v-if="debugInfo" class="debug-section">
                            <div class="debug-info-content">
                                <div class="debug-info-item">
                                    <span class="debug-info-label">发送材料代码:</span>
                                    <span class="debug-info-value">{{ getDebugValue('发送材料代码') }}</span>
                                </div>
                                <div class="debug-info-item">
                                    <span class="debug-info-label">数据大小:</span>
                                    <span class="debug-info-value">{{ getDebugValue('数据大小') }}</span>
                                </div>
                                <div class="debug-info-item">
                                    <span class="debug-info-label">图片尺寸:</span>
                                    <span class="debug-info-value">{{ getDebugValue('图片加载成功') }}</span>
                                </div>
                            </div>
                        </div>

                        <!-- 错误信息显示 -->
                        <div v-if="errorMessage" class="error-message">
                            <div class="error-alert">{{ errorMessage }}</div>
                        </div>

                        <!-- 检测结果信息 -->
                        <div v-if="detectionInfo" class="detection-result-panel horizontal-layout">
                            <div class="result-header">
                                <h3>检测结果</h3>
                            </div>

                            <div class="result-content horizontal-layout">
                                <!-- 处理时间 -->
                                <div class="result-row">
                                    <div class="result-item horizontal-item">
                                        <div class="item-label">处理时间</div>
                                        <div class="item-value time-value">{{ detectionInfo.processingTime }}秒</div>
                                    </div>
                                </div>

                                <!-- 选择的材料 -->
                                <div class="result-row">
                                    <div class="result-item horizontal-item full-width">
                                        <div class="item-label">检测材料</div>
                                        <div class="item-value materials-value">
                                            {{ detectionInfo.selectedMaterials.join(', ') }}
                                        </div>
                                    </div>
                                </div>

                                <!-- 检测状态 -->
                                <div class="result-row">
                                    <div class="result-item horizontal-item">
                                        <div class="item-label">检测状态</div>
                                        <div class="item-value status-value">完成</div>
                                    </div>
                                    <div class="result-item horizontal-item">
                                        <div class="item-label">材料数量</div>
                                        <div class="item-value">{{ detectionInfo.materialCount }}</div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script>
    import axios from 'axios'

    export default {
        name: 'Detection',
        data() {
            return {
                pseudoImageUrl: null,
                detectionImageUrl: null,
                isPseudoLoading: false,
                isDetecting: false,
                debugInfo: '',
                errorMessage: '',
                detectionInfo: null,
                imageDimensions: { width: 0, height: 0 },
                loadTime: 0,
                loadStartTime: 0,
                selectedMaterials: [],
                materials: [
                    { name: "叶绿伪装布", color: "#00FF00", code: "d1" },
                    { name: "沙漠迷彩布", color: "#DAA520", code: "d2" },
                    { name: "雪地迷彩布", color: "#ADD8E6", code: "d3" },
                    { name: "城市灰伪装", color: "#808080", code: "d4" },
                    { name: "丛林迷彩布", color: "#006400", code: "d5" },
                    { name: "夜间伪装布", color: "#4B0082", code: "d6" }
                ]
            }
        },
        methods: {
            // 加载伪彩图
            async loadPseudocolor() {
                if (this.isPseudoLoading) return
                this.isPseudoLoading = true
                this.loadStartTime = Date.now()
                try {
                    const response = await axios.post('http://localhost:8000/pseudocolor')
                    if (response.data.success) {
                        this.pseudoImageUrl = `data:image/png;base64,${response.data.image}`

                        // 计算加载时间
                        this.loadTime = (Date.now() - this.loadStartTime) / 1000

                        // 获取图像尺寸
                        this.$nextTick(() => {
                            const img = new Image()
                            img.onload = () => {
                                this.imageDimensions = {
                                    width: img.width,
                                    height: img.height
                                }
                            }
                            img.src = this.pseudoImageUrl
                        })
                    } else {
                        this.errorMessage = response.data.message || '伪彩图加载失败'
                    }
                } catch (error) {
                    console.error('伪彩图加载出错:', error)
                    this.errorMessage = '调用后端接口失败: ' + error.message
                } finally {
                    this.isPseudoLoading = false
                }
            },

            // 材料检测
            async runMaterialDetection() {
                if (this.isDetecting || this.selectedMaterials.length === 0) return

                this.isDetecting = true
                this.detectionInfo = null
                this.errorMessage = ''
                this.debugInfo = ''
                this.detectionImageUrl = null

                const startTime = Date.now()

                try {
                    this.debugInfo += `发送材料代码: ${this.selectedMaterials.join(', ')}\n`

                    const response = await axios.post(
                        'http://localhost:8000/fusion/detect',
                        { materials: this.selectedMaterials },
                        {
                            responseType: 'blob',
                            timeout: 60000
                        }
                    )

                    this.debugInfo += `响应状态: ${response.status}\n`
                    this.debugInfo += `内容类型: ${response.headers['content-type']}\n`
                    this.debugInfo += `数据大小: ${response.data.size} 字节\n`

                    if (response.data.size === 0) {
                        throw new Error('服务器返回了空文件')
                    }

                    // 检查响应是否是图片
                    if (response.data instanceof Blob && response.data.type.startsWith('image/')) {
                        const imgUrl = URL.createObjectURL(response.data)
                        this.detectionImageUrl = imgUrl

                        // 测试图片是否能正常加载
                        const img = new Image()
                        img.onload = () => {
                            this.debugInfo += `图片加载成功: ${img.width} x ${img.height}\n`
                        }
                        img.onerror = () => {
                            this.debugInfo += '图片加载失败\n'
                        }
                        img.src = imgUrl

                        // 构建检测结果信息
                        const processingTime = (Date.now() - startTime) / 1000
                        const selectedMaterialNames = this.materials
                            .filter(m => this.selectedMaterials.includes(m.code))
                            .map(m => m.name)

                        this.detectionInfo = {
                            processingTime: processingTime.toFixed(2),
                            selectedMaterials: selectedMaterialNames,
                            materialCount: this.selectedMaterials.length
                        }

                    } else {
                        // 如果不是图片，可能是错误信息
                        const text = await response.data.text()
                        try {
                            const errorData = JSON.parse(text)
                            this.errorMessage = errorData.message || '检测失败'
                        } catch (e) {
                            this.errorMessage = '返回的数据格式不正确'
                        }
                    }

                } catch (error) {
                    console.error('材料检测出错:', error)
                    this.debugInfo += `错误: ${error.message}\n`

                    if (error.response) {
                        // 服务器返回了错误状态码
                        if (error.response.data instanceof Blob) {
                            const errorText = await error.response.data.text()
                            try {
                                const errorData = JSON.parse(errorText)
                                this.errorMessage = errorData.detail || errorData.message || '服务器错误'
                            } catch (e) {
                                this.errorMessage = errorText || '服务器返回了错误信息'
                            }
                        } else {
                            this.errorMessage = error.response.data.detail || error.response.data.message || '请求失败'
                        }
                    } else if (error.request) {
                        this.errorMessage = '无法连接到服务器，请检查后端服务是否启动'
                    } else {
                        this.errorMessage = error.message
                    }
                } finally {
                    this.isDetecting = false
                }
            },

            // 下载伪彩图
            downloadPseudoImage() {
                if (!this.pseudoImageUrl) return

                const link = document.createElement('a')
                link.href = this.pseudoImageUrl
                link.download = `pseudocolor_${new Date().getTime()}.png`
                document.body.appendChild(link)
                link.click()
                document.body.removeChild(link)
            },

            // 从调试信息中提取特定值
            getDebugValue(key) {
                if (!this.debugInfo) return ''
                const lines = this.debugInfo.split('\n')
                const line = lines.find(l => l.includes(key))
                if (line) {
                    return line.split(': ')[1] || ''
                }
                return ''
            }
        },

        beforeUnmount() {
            // 清理对象URL避免内存泄漏
            if (this.detectionImageUrl) {
                URL.revokeObjectURL(this.detectionImageUrl)
            }
        }
    }
</script>

<style scoped>
    /* 原有的样式保持不变，只添加新的调试信息样式 */

    /* 调试信息样式 - 与其他文字样式保持一致 */
    .debug-section {
        width: 100%;
        margin: 15px 0;
        padding: 15px;
        background: #f8f9fa;
        border-radius: 8px;
        border: 1px solid #e9ecef;
    }

    .debug-info-content {
        display: flex;
        flex-direction: column;
        gap: 8px;
    }

    .debug-info-item {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 6px 0;
        border-bottom: 1px solid #e9ecef;
    }

        .debug-info-item:last-child {
            border-bottom: none;
        }

    .debug-info-label {
        color: #6c757d;
        font-size: 13px;
        font-weight: 500;
        flex-shrink: 0;
    }

    .debug-info-value {
        color: #495057;
        font-size: 13px;
        font-weight: 600;
        text-align: right;
        flex: 1;
        margin-left: 10px;
        word-break: break-all;
    }

    /* 其他原有样式保持不变 */
    .detection-root {
        display: flex;
        justify-content: center;
        padding: 20px;
        background: #f5f7fa;
    }

    .detection-container {
        width: 100%;
        background: #fff;
        border-radius: 16px;
        padding: 30px;
        box-shadow: 0 10px 30px rgba(0,0,0,0.1);
    }

    .page-title {
        font-size: 28px;
        text-align: center;
        margin-bottom: 30px;
        color: #333;
    }

    .viewer {
        display: grid;
        grid-template-columns: 1.2fr 0.8fr;
        gap: 30px;
        align-items: stretch;
    }

    .asset-container {
        display: flex;
        flex-direction: column;
        border: 2px dashed #e2e8f0;
        border-radius: 12px;
        padding: 20px;
        background: #fafafa;
        height: auto;
        min-height: 700px;
    }

    .controls {
        width: 100%;
        display: flex;
        flex-direction: column;
        align-items: center;
    }

    .top-controls {
        margin-bottom: 15px;
    }

    .bottom-controls {
        margin-top: 15px;
    }

    .image-wrapper {
        width: 100%;
        display: flex;
        align-items: center;
        justify-content: center;
        border-radius: 12px;
        background: #f9fafb;
        flex: 1;
        min-height: 400px;
    }

        .image-wrapper.large-image {
            min-height: 450px;
        }

    .placeholder {
        text-align: center;
        color: #a0aec0;
        padding: 40px 20px;
        width: 100%;
    }

    .display-image {
        max-width: 100%;
        max-height: 100%;
        border-radius: 8px;
    }

        .display-image.large {
            max-height: 430px;
            max-width: 95%;
        }

    .asset-title {
        font-size: 20px;
        margin-bottom: 0;
        color: #2d3748;
        text-align: center;
        font-weight: 600;
    }

    /* 材料选择样式 */
    .material-selection {
        width: 100%;
        margin-bottom: 20px;
    }

        .material-selection h4 {
            text-align: center;
            margin-bottom: 15px;
            color: #4a5568;
            font-size: 16px;
        }

    .material-grid {
        display: grid;
        grid-template-columns: repeat(2, 1fr);
        gap: 12px;
        justify-items: start;
    }

    .material-item {
        display: flex;
        align-items: center;
    }

    .material-checkbox {
        display: flex;
        align-items: center;
        cursor: pointer;
        font-size: 14px;
        color: #4a5568;
    }

        .material-checkbox input[type="checkbox"] {
            margin-right: 8px;
        }

    .color-indicator {
        display: inline-block;
        width: 16px;
        height: 16px;
        margin-right: 6px;
        border-radius: 3px;
        border: 1px solid #ccc;
        vertical-align: middle;
    }

    .action-button {
        padding: 12px 24px;
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        border: none;
        border-radius: 20px;
        color: #fff;
        font-size: 14px;
        cursor: pointer;
        transition: 0.3s;
        margin-bottom: 15px;
        min-width: 140px;
    }

        .action-button:hover:not(:disabled) {
            transform: translateY(-2px);
            background: linear-gradient(135deg, #5a67d8 0%, #6b46c1 100%);
        }

        .action-button:disabled {
            opacity: 0.6;
            cursor: not-allowed;
        }

    .secondary-button {
        padding: 8px 16px;
        background: #f8f9fa;
        border: 1px solid #dee2e6;
        border-radius: 6px;
        color: #495057;
        font-size: 12px;
        cursor: pointer;
        transition: all 0.3s ease;
        margin-top: 10px;
    }

        .secondary-button:hover {
            background: #e9ecef;
            border-color: #adb5bd;
        }

    .pseudo-additional-info {
        margin-top: 15px;
        padding: 15px;
        background: #f8f9fa;
        border-radius: 8px;
        border: 1px solid #e9ecef;
        width: 100%;
    }

    .info-item {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 8px;
        font-size: 13px;
    }

        .info-item:last-child {
            margin-bottom: 0;
        }

    .info-label {
        color: #6c757d;
        font-weight: 500;
    }

    .info-value {
        color: #495057;
        font-weight: 600;
    }

    /* 错误信息样式 */
    .error-message {
        width: 100%;
        margin: 10px 0;
    }

    .error-alert {
        background: #fed7d7;
        color: #c53030;
        padding: 10px 15px;
        border-radius: 6px;
        border: 1px solid #feb2b2;
        font-size: 14px;
    }

    /* 检测结果面板样式 */
    .detection-result-panel.horizontal-layout {
        background: #ffffff;
        border-radius: 12px;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
        padding: 20px;
        width: 100%;
        font-family: 'Arial', sans-serif;
        border: 1px solid #e8e8e8;
        margin-top: 15px;
    }

    .result-header {
        text-align: center;
        margin-bottom: 15px;
        padding-bottom: 10px;
        border-bottom: 2px solid #f0f0f0;
    }

        .result-header h3 {
            margin: 0;
            color: #2c3e50;
            font-size: 16px;
            font-weight: 600;
        }

    .result-content.horizontal-layout {
        display: flex;
        flex-direction: column;
        gap: 12px;
    }

    .result-row {
        display: flex;
        justify-content: center;
    }

    .result-item.horizontal-item {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        padding: 12px 15px;
        border-radius: 8px;
        background: #f8f9fa;
        min-width: 100px;
        flex: 1;
        min-height: 70px;
        border: 1px solid #e9ecef;
        transition: all 0.3s ease;
    }

        .result-item.horizontal-item.full-width {
            flex: 1 1 100%;
            max-width: 100%;
        }

        .result-item.horizontal-item:hover {
            background-color: #e9ecef;
            transform: translateY(-2px);
            box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
        }

        .result-item.horizontal-item .item-label {
            color: #5a6c7d;
            font-size: 12px;
            font-weight: 500;
            margin-bottom: 6px;
            text-align: center;
        }

        .result-item.horizontal-item .item-value {
            color: #2c3e50;
            font-size: 14px;
            font-weight: 600;
            text-align: center;
        }

        .result-item.horizontal-item .time-value {
            color: #3498db;
            font-weight: 700;
            font-size: 15px;
        }

        .result-item.horizontal-item .status-value {
            color: #27ae60;
            font-weight: 700;
        }

        .result-item.horizontal-item .materials-value {
            font-size: 13px;
            line-height: 1.4;
        }

    /* 响应式设计 */
    @media (max-width: 1024px) {
        .viewer {
            grid-template-columns: 1fr;
            gap: 20px;
        }

        .asset-container {
            min-height: auto;
        }

        .image-wrapper.large-image {
            min-height: 400px;
        }

        .display-image.large {
            max-height: 380px;
        }

        .material-grid {
            grid-template-columns: 1fr;
        }
    }

    @media (max-width: 768px) {
        .detection-container {
            padding: 20px;
        }

        .image-wrapper.large-image {
            min-height: 350px;
        }

        .display-image.large {
            max-height: 330px;
        }
    }

    @media (max-width: 480px) {
        .image-wrapper.large-image {
            min-height: 300px;
        }

        .display-image.large {
            max-height: 280px;
        }
    }
</style>