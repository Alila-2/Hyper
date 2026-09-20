<template>
    <div class="detection-root">
        <div class="detection-container">
            <h2 class="page-title">目标探测结果展示</h2>

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

                <!-- 目标探测 -->
                <div class="asset-container detection-container">
                    <!-- 标题在图片正上方 -->
                    <div class="controls top-controls">
                        <h3 class="asset-title">目标探测</h3>
                    </div>

                    <!-- 图片区域 -->
                    <div class="image-wrapper">
                        <div v-if="!detectionImageUrl" class="placeholder">
                            <p>选择检测模式并点击下方按钮执行检测</p>
                        </div>
                        <img v-else
                             :src="detectionImageUrl"
                             alt="目标探测结果"
                             class="display-image" />
                    </div>

                    <!-- 控件在图片正下方 -->
                    <div class="controls bottom-controls">
                        <!-- 检测模式选择 -->
                        <div class="mode-selector">
                            <label>
                                <input type="radio" value="single" v-model="detectionMode" />
                                单目标探测
                            </label>
                            <label>
                                <input type="radio" value="multi" v-model="detectionMode" />
                                多目标探测
                            </label>
                        </div>

                        <!-- 开始检测按钮 -->
                        <button @click="runDetection"
                                class="action-button"
                                :disabled="isDetecting">
                            {{ isDetecting ? '检测中...' : '执行检测' }}
                        </button>

                        <!-- 水平布局的检测结果展示 -->
                        <div v-if="detectionInfo" class="detection-result-panel horizontal-layout">
                            <div class="result-header">
                                <h3>检测结果</h3>
                            </div>

                            <div class="result-content horizontal-layout">
                                <!-- 处理时间 - 所有检测模式都有 -->
                                <div class="result-row">
                                    <div class="result-item horizontal-item">
                                        <div class="item-label">处理时间</div>
                                        <div class="item-value time-value">{{ detectionInfo.runtime.toFixed(4) }}秒</div>
                                    </div>
                                </div>

                                <!-- 单目标探测结果 -->
                                <div v-if="detectionMode === 'single' && detectionInfo.singleMetrics" class="single-metrics horizontal-row">
                                    <div class="result-item horizontal-item">
                                        <div class="item-label">检测置信度</div>
                                        <div class="item-value">{{ detectionInfo.singleMetrics.confidence.toFixed(4) }}</div>
                                    </div>
                                    <div class="result-item horizontal-item">
                                        <div class="item-label">目标数量</div>
                                        <div class="item-value">{{ detectionInfo.singleMetrics.targetCount }}</div>
                                    </div>
                                </div>

                                <!-- 多目标探测结果 -->
                                <div v-else-if="detectionMode === 'multi' && detectionInfo.multiMetrics" class="multi-metrics">
                                    <!-- 整体召回率 -->
                                    <div v-if="detectionInfo.multiMetrics.overall_recall !== null" class="result-row">
                                        <div class="result-item horizontal-item">
                                            <div class="item-label">整体召回率</div>
                                            <div class="item-value">{{ detectionInfo.multiMetrics.overall_recall.toFixed(4) }}</div>
                                        </div>
                                    </div>

                                    <!-- 各类别精度 - 水平排列 -->
                                    <div v-if="detectionInfo.multiMetrics.precisions && detectionInfo.multiMetrics.precisions.length > 0"
                                         class="precision-row horizontal-row">
                                        <div class="result-item horizontal-item" v-for="(precision, index) in detectionInfo.multiMetrics.precisions" :key="index">
                                            <div class="item-label">类别{{ index + 1 }}</div>
                                            <div class="item-value">{{ precision.toFixed(4) }}</div>
                                        </div>
                                    </div>

                                    <!-- 如果没有精度数据，显示检测统计 -->
                                    <div v-else class="multi-stats horizontal-row">
                                        <div class="result-item horizontal-item">
                                            <div class="item-label">检测类别数</div>
                                            <div class="item-value">{{ detectionInfo.multiMetrics.classCount || 6 }}</div>
                                        </div>
                                        <div class="result-item horizontal-item">
                                            <div class="item-label">阈值</div>
                                            <div class="item-value">0.7</div>
                                        </div>
                                    </div>
                                </div>

                                <!-- 默认显示（当没有特定指标时） -->
                                <div v-else class="default-metrics horizontal-row">
                                    <div class="result-item horizontal-item">
                                        <div class="item-label">检测状态</div>
                                        <div class="item-value">完成</div>
                                    </div>
                                    <div class="result-item horizontal-item">
                                        <div class="item-label">图像生成</div>
                                        <div class="item-value">成功</div>
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
                detectionMode: 'single',
                detectionInfo: null,
                imageDimensions: { width: 0, height: 0 },
                loadTime: 0,
                loadStartTime: 0
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
                        alert(response.data.message || '伪彩图加载失败')
                    }
                } catch (error) {
                    console.error('伪彩图加载出错:', error)
                    alert('调用后端接口失败')
                } finally {
                    this.isPseudoLoading = false
                }
            },

            // 目标探测
            async runDetection() {
                if (this.isDetecting) return
                this.isDetecting = true
                this.detectionInfo = null // 清空之前的结果

                try {
                    const url = this.detectionMode === 'single'
                        ? 'http://localhost:8000/api/single-detection'
                        : 'http://localhost:8000/api/multi-detection'

                    const response = await axios.post(url)
                    if (response.data.success) {
                        this.detectionImageUrl = `data:image/png;base64,${response.data.image}`

                        // 根据检测模式动态构建检测信息
                        if (this.detectionMode === 'single') {
                            this.detectionInfo = {
                                runtime: response.data.runtime,
                                singleMetrics: {
                                    confidence: response.data.confidence || 0.95, // 如果没有返回置信度，使用默认值
                                    targetCount: response.data.targetCount || 1
                                }
                            }
                        } else {
                            // 多目标探测
                            this.detectionInfo = {
                                runtime: response.data.runtime,
                                multiMetrics: {
                                    overall_recall: response.data.overall_recall !== undefined ? response.data.overall_recall : null,
                                    precisions: response.data.precisions !== undefined ? response.data.precisions : null,
                                    classCount: response.data.precisions ? response.data.precisions.length : 6
                                }
                            }
                        }
                    } else {
                        alert(response.data.message || '目标探测失败')
                    }
                } catch (error) {
                    console.error('目标检测出错:', error)
                    alert('调用后端接口失败')
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
            }
        }
    }
</script>

<style scoped>
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
        align-items: stretch; /* 改为stretch让两个容器高度一致 */
    }

    .asset-container {
        display: flex;
        flex-direction: column;
        border: 2px dashed #e2e8f0;
        border-radius: 12px;
        padding: 20px;
        background: #fafafa;
        height: auto; /* 改为auto让高度自适应 */
        min-height: 700px; /* 设置最小高度确保一致性 */
    }

    .pseudo-container {
        /* 移除单独的min-height，使用统一的min-height */
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
        min-height: 400px; /* 为图片区域设置最小高度 */
    }

        .image-wrapper.large-image {
            min-height: 450px; /* 增大伪彩图区域的最小高度 */
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

    .mode-selector {
        margin-bottom: 15px;
        font-size: 14px;
        color: #555;
        display: flex;
        gap: 20px;
        justify-content: center;
        width: 100%;
    }

        .mode-selector label {
            display: flex;
            align-items: center;
            gap: 6px;
            cursor: pointer;
        }

        .mode-selector input[type="radio"] {
            margin: 0;
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

    /* 水平布局的检测结果面板样式 */
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

    /* 水平布局样式 */
    .result-content.horizontal-layout {
        display: flex;
        flex-direction: column;
        gap: 12px;
    }

    .result-row {
        display: flex;
        justify-content: center;
    }

    .horizontal-row {
        display: flex;
        gap: 15px;
        justify-content: space-between;
        flex-wrap: wrap;
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

    /* 精度行的水平布局 */
    .precision-row.horizontal-row {
        gap: 10px;
    }

    .single-metrics.horizontal-row,
    .multi-stats.horizontal-row,
    .default-metrics.horizontal-row {
        gap: 15px;
    }

    /* 确保水平布局中的项目均匀分布 */
    .horizontal-row .result-item.horizontal-item {
        flex: 1;
        max-width: calc(50% - 8px);
    }

    .precision-row.horizontal-row .result-item.horizontal-item {
        flex: 1;
        max-width: calc(33.333% - 7px);
    }

    /* 响应式设计 */
    @media (max-width: 1024px) {
        .viewer {
            grid-template-columns: 1fr;
            gap: 20px;
        }

        .asset-container {
            min-height: auto; /* 在移动端取消固定高度 */
        }

        .image-wrapper.large-image {
            min-height: 400px;
        }

        .display-image.large {
            max-height: 380px;
        }

        .precision-row.horizontal-row .result-item.horizontal-item {
            max-width: calc(50% - 5px);
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

        .mode-selector {
            flex-direction: column;
            gap: 10px;
            align-items: center;
        }

        .horizontal-row {
            gap: 10px;
        }

            .horizontal-row .result-item.horizontal-item {
                max-width: calc(50% - 5px);
            }

        .precision-row.horizontal-row .result-item.horizontal-item {
            max-width: calc(50% - 5px);
        }
    }

    @media (max-width: 480px) {
        .horizontal-row .result-item.horizontal-item {
            max-width: 100%;
            flex: 1 1 100%;
        }

        .precision-row.horizontal-row .result-item.horizontal-item {
            max-width: 100%;
            flex: 1 1 100%;
        }

        .image-wrapper.large-image {
            min-height: 300px;
        }

        .display-image.large {
            max-height: 280px;
        }
    }
</style>