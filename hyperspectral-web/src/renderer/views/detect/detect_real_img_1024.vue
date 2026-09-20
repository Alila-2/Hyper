<template>
    <div class="detection-root">
        <div class="detection-container">
            <h2 class="page-title">目标探测结果展示</h2>

            <div class="viewer">
                <!-- 文件上传和预览区域 -->
                <div class="asset-container pseudo-container">
                    <div class="controls top-controls">
                        <h3 class="asset-title">文件上传与预览</h3>
                    </div>

                    <div class="image-wrapper large-image">
                        <div v-if="!previewImageUrl" class="placeholder">
                            <p>上传文件进行预览和检测</p>
                            <p class="file-hint">支持 .mat, .png, .jpg, .jpeg 格式</p>
                        </div>
                        <img v-else
                             :src="previewImageUrl"
                             alt="文件预览"
                             class="display-image large" />
                    </div>

                    <div class="controls bottom-controls">
                        <div class="file-upload-section">
                            <input type="file"
                                   ref="fileInput"
                                   @change="handleFileUpload"
                                   accept=".mat,.png,.jpg,.jpeg"
                                   class="file-input"
                                   id="fileInput" />
                            <label for="fileInput" class="file-upload-button">
                                📁 选择文件
                            </label>
                            <span v-if="uploadedFile" class="file-name">{{ uploadedFile.name }}</span>
                        </div>

                        <div v-if="previewImageUrl" class="pseudo-additional-info">
                            <div class="info-item">
                                <span class="info-label">文件类型:</span>
                                <span class="info-value">{{ fileType }}</span>
                            </div>
                            <div class="info-item">
                                <span class="info-label">文件大小:</span>
                                <span class="info-value">{{ fileSize }}</span>
                            </div>
                            <div class="info-item">
                                <span class="info-label">图像尺寸:</span>
                                <span class="info-value">{{ imageDimensions.width }} × {{ imageDimensions.height }}</span>
                            </div>
                            <div class="info-item">
                                <span class="info-label">处理时间:</span>
                                <span class="info-value">{{ processTime }}秒</span>
                            </div>
                            <button @click="downloadPreviewImage" class="secondary-button">
                                📥 下载预览图
                            </button>
                        </div>
                    </div>
                </div>

                <!-- 材料检测结果 -->
                <div class="asset-container detection-container">
                    <div class="controls top-controls">
                        <h3 class="asset-title">材料检测结果</h3>
                    </div>

                    <div class="image-wrapper">
                        <div v-if="!detectionImageUrl" class="placeholder">
                            <p v-if="!uploadedFile">请先上传文件</p>
                            <p v-else-if="isDetecting">检测中...</p>
                            <p v-else>等待执行检测</p>
                        </div>
                        <img v-else
                             :src="detectionImageUrl"
                             alt="材料检测结果"
                             class="display-image" />
                    </div>

                    <div class="controls bottom-controls">
                        <button @click="runMaterialDetection"
                                class="action-button"
                                :disabled="isDetecting || !uploadedFile">
                            {{ isDetecting ? '检测中...' : '执行材料检测' }}
                        </button>

                        <!-- 调试信息 -->
                        <div v-if="debugInfo" class="debug-section">
                            <pre>{{ debugInfo }}</pre>
                        </div>

                        <!-- 错误信息显示 -->
                        <div v-if="errorMessage" class="error-message">
                            <div class="error-alert">{{ errorMessage }}</div>
                        </div>

                        <!-- 检测到的目标展示 -->
                        <div v-if="detectedTargets && detectedTargets.length" class="detected-targets-section">
                            <h4 class="detected-targets-title">检测到的目标</h4>
                            <div class="detected-targets-grid">
                                <div v-for="target in detectedTargets"
                                     :key="target.type"
                                     class="detected-target-item">
                                    <div class="target-color-indicator"
                                         :style="{ backgroundColor: target.color_hex }"></div>
                                    <span class="target-name">{{ target.name }}</span>
                                    <span class="target-type">({{ target.type }})</span>
                                </div>
                            </div>
                        </div>

                        <!-- 检测结果信息 -->
                        <div v-if="detectionInfo" class="detection-result-panel">
                            <div class="result-header">
                                <h3>检测结果统计</h3>
                            </div>
                            <div class="result-content">
                                <div class="result-item">
                                    <span class="item-label">处理时间:</span>
                                    <span class="item-value">{{ detectionInfo.processingTime }}秒</span>
                                </div>
                                <div class="result-item">
                                    <span class="item-label">检测材料:</span>
                                    <span class="item-value">{{ detectionInfo.materialCount }}种</span>
                                </div>
                                <!-- div class="result-item">
                                    <span class="item-label">检测目标:</span>
                                    <span class="item-value">{{ detectionInfo.targetCount }}个</span>
                                </div-->
                                <div class="result-item">
                                    <span class="item-label">发现目标:</span>
                                    <span class="item-value">{{ detectedTargetsCount }}个</span>
                                </div>
                                <div class="result-item full-width">
                                    <span class="item-label">检测状态:</span>
                                    <span class="item-value status-value">完成</span>
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
                uploadedFile: null,
                previewImageUrl: null,
                detectionImageUrl: null,
                isUploading: false,
                isDetecting: false,
                debugInfo: '',
                errorMessage: '',
                detectionInfo: null,
                detectedTargets: [],
                imageDimensions: { width: 0, height: 0 },
                processTime: 0,
                processStartTime: 0,
                fileType: '',
                fileSize: '',
                materials: [
                    {
                        name: "假草皮",
                        color: "#008000",  // 深绿色
                        targets: ["d1"]
                    },
                    {
                        name: "木板",
                        color: "#DAA520",  // 金色
                        targets: ["d2"]
                    },
                    {
                        name: "竹材",
                        color: "#808080",  // 灰色
                        targets: ["d3"]
                    },
                    {
                        name: "棕色迷彩服",
                        color: "#8B4513",  // 棕色
                        targets: ["d4"]
                    },
                    {
                        name: "蓝色迷彩服",
                        color: "#0000FF",  // 蓝色
                        targets: ["d5"]
                    },
                    {
                        name: "绿色伪装服",
                        color: "#ADD8E6",  // 浅蓝色
                        targets: ["d6"]
                    },
                    {
                        name: "棕色伪装服",
                        color: "#F0E68C",  // 浅黄色
                        targets: ["d7"]
                    },
                    {
                        name: "伪装网",
                        color: "#800080",  // 紫色
                        targets: ["d8"]
                    },
                    {
                        name: "军事目标",
                        color: "#FFFFFF",  // 白色
                        targets: ["d9"]
                    }
                ]
            }
        },
        computed: {
            allMaterialNames() {
                return this.materials.map(material => material.name)
            },
            allTargets() {
                const targets = new Set()
                this.materials.forEach(material => {
                    if (material.targets) {
                        material.targets.forEach(target => targets.add(target))
                    }
                })
                return Array.from(targets)
            },
            detectedTargetsCount() {
                return this.detectedTargets ? this.detectedTargets.length : 0
            }
        },
        methods: {
            async handleFileUpload(event) {
                const file = event.target.files[0]
                if (file) {
                    const allowedTypes = ['.mat', '.png', '.jpg', '.jpeg']
                    const fileExtension = '.' + file.name.split('.').pop().toLowerCase()

                    if (!allowedTypes.includes(fileExtension)) {
                        this.errorMessage = '不支持的文件格式，请选择 .mat, .png, .jpg, .jpeg 文件'
                        this.$refs.fileInput.value = ''
                        return
                    }

                    this.uploadedFile = file
                    this.fileType = this.getFileType(fileExtension)
                    this.fileSize = this.formatFileSize(file.size)
                    this.errorMessage = ''
                    this.detectionImageUrl = null
                    this.detectedTargets = []
                    this.detectionInfo = null

                    // 自动上传并预览文件
                    await this.uploadAndPreview()
                }
            },

            getFileType(extension) {
                const types = {
                    '.mat': 'MATLAB 数据文件',
                    '.png': 'PNG 图像',
                    '.jpg': 'JPEG 图像',
                    '.jpeg': 'JPEG 图像'
                }
                return types[extension] || '未知文件'
            },

            formatFileSize(bytes) {
                if (bytes === 0) return '0 Bytes'
                const k = 1024
                const sizes = ['Bytes', 'KB', 'MB', 'GB']
                const i = Math.floor(Math.log(bytes) / Math.log(k))
                return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
            },

            async uploadAndPreview() {
                if (this.isUploading || !this.uploadedFile) return

                this.isUploading = true
                this.processStartTime = Date.now()
                this.errorMessage = ''

                try {
                    const formData = new FormData()
                    formData.append('file', this.uploadedFile)

                    const response = await axios.post('http://localhost:8000/upload_and_preview', formData, {
                        headers: {
                            'Content-Type': 'multipart/form-data'
                        },
                        timeout: 30000
                    })

                    if (response.data.success) {
                        this.previewImageUrl = `data:image/png;base64,${response.data.image}`
                        this.processTime = (Date.now() - this.processStartTime) / 1000

                        this.$nextTick(() => {
                            const img = new Image()
                            img.onload = () => {
                                this.imageDimensions = {
                                    width: img.width,
                                    height: img.height
                                }
                            }
                            img.src = this.previewImageUrl
                        })
                    } else {
                        this.errorMessage = response.data.message || '文件处理失败'
                    }
                } catch (error) {
                    console.error('文件上传出错:', error)
                    this.handleError(error, '上传')
                } finally {
                    this.isUploading = false
                }
            },

            async runMaterialDetection() {
                if (this.isDetecting || !this.uploadedFile) return

                this.isDetecting = true
                this.detectionInfo = null
                this.detectedTargets = []
                this.errorMessage = ''
                this.debugInfo = ''
                this.detectionImageUrl = null

                const startTime = Date.now()

                try {
                    const formData = new FormData()
                    formData.append('file', this.uploadedFile)
                    formData.append('targets', JSON.stringify(this.allTargets))

                    this.debugInfo = `发送检测目标: ${this.allTargets.join(', ')}\n`

                    const response = await axios.post(
                        'http://localhost:8000/fusion/detect_real2',
                        formData,
                        {
                            timeout: 60000,
                            headers: {
                                'Content-Type': 'multipart/form-data'
                            }
                        }
                    )

                    this.debugInfo += `响应状态: ${response.status}\n`

                    if (response.data.success) {
                        this.detectionImageUrl = `data:image/png;base64,${response.data.image}`
                        this.detectedTargets = response.data.detected_targets || []

                        const processingTime = (Date.now() - startTime) / 1000
                        this.detectionInfo = {
                            //processingTime: processingTime.toFixed(2),
                            materialCount: this.allMaterialNames.length,
                            processingTime: response.data.runtime,
                            targetCount: this.allTargets.length
                        }

                        this.debugInfo += `检测成功！发现 ${this.detectedTargets.length} 个目标\n`
                    } else {
                        this.errorMessage = response.data.message || '检测失败'
                    }

                } catch (error) {
                    console.error('材料检测出错:', error)
                    this.debugInfo += `错误: ${error.message}\n`
                    this.handleError(error, '检测')
                } finally {
                    this.isDetecting = false
                }
            },

            handleError(error, operation) {
                if (error.response) {
                    if (error.response.data && error.response.data.message) {
                        this.errorMessage = error.response.data.message
                    } else {
                        this.errorMessage = `${operation}请求失败，状态码: ${error.response.status}`
                    }
                } else if (error.request) {
                    this.errorMessage = `无法连接到服务器，请检查后端服务是否启动 (${operation})`
                } else {
                    this.errorMessage = `${operation}错误: ${error.message}`
                }
            },

            downloadPreviewImage() {
                if (!this.previewImageUrl) return

                const link = document.createElement('a')
                link.href = this.previewImageUrl
                link.download = `preview_${new Date().getTime()}.png`
                document.body.appendChild(link)
                link.click()
                document.body.removeChild(link)
            }
        },

        beforeUnmount() {
            if (this.detectionImageUrl && this.detectionImageUrl.startsWith('blob:')) {
                URL.revokeObjectURL(this.detectionImageUrl)
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
        min-height: 100vh;
    }

    .detection-container {
        width: 100%;
        max-width: 1400px;
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
        grid-template-columns: 1fr 1fr;
        gap: 30px;
        align-items: start;
    }

    .asset-container {
        display: flex;
        flex-direction: column;
        border: 2px dashed #e2e8f0;
        border-radius: 12px;
        padding: 20px;
        background: #fafafa;
        min-height: 600px;
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
        width: 100%;
    }

    .image-wrapper {
        width: 100%;
        display: flex;
        align-items: center;
        justify-content: center;
        border-radius: 12px;
        background: #f9fafb;
        flex: 1;
        min-height: 300px;
        max-height: 400px;
        overflow: hidden;
    }

        .image-wrapper.large-image {
            min-height: 350px;
            max-height: 450px;
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
        object-fit: contain;
    }

        .display-image.large {
            max-height: 430px;
        }

    .asset-title {
        font-size: 20px;
        margin-bottom: 0;
        color: #2d3748;
        text-align: center;
        font-weight: 600;
    }

    .file-upload-section {
        display: flex;
        flex-direction: column;
        align-items: center;
        margin-bottom: 15px;
        width: 100%;
    }

    .file-input {
        display: none;
    }

    .file-upload-button {
        padding: 10px 20px;
        background: #4CAF50;
        color: white;
        border: none;
        border-radius: 20px;
        cursor: pointer;
        font-size: 14px;
        transition: background 0.3s;
        margin-bottom: 10px;
    }

        .file-upload-button:hover {
            background: #45a049;
        }

    .file-name {
        font-size: 12px;
        color: #666;
        word-break: break-all;
        text-align: center;
    }

    .file-hint {
        font-size: 12px;
        color: #888;
        margin-top: 5px;
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

    .debug-section {
        width: 100%;
        margin: 10px 0;
    }

        .debug-section pre {
            text-align: left;
            background: #f5f5f5;
            padding: 10px;
            border-radius: 4px;
            font-size: 12px;
            max-height: 100px;
            overflow-y: auto;
            white-space: pre-wrap;
            word-break: break-all;
        }

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

    .detected-targets-section {
        width: 100%;
        margin: 15px 0;
        padding: 15px;
        background: #f8f9fa;
        border-radius: 8px;
        border: 1px solid #e9ecef;
    }

    .detected-targets-title {
        text-align: center;
        margin-bottom: 12px;
        color: #2c3e50;
        font-size: 16px;
        font-weight: 600;
    }

    .detected-targets-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
        gap: 10px;
    }

    .detected-target-item {
        display: flex;
        align-items: center;
        padding: 8px 12px;
        background: white;
        border-radius: 6px;
        border: 1px solid #dee2e6;
    }

    .target-color-indicator {
        width: 16px;
        height: 16px;
        border-radius: 3px;
        margin-right: 8px;
        border: 1px solid #ccc;
    }

    .target-name {
        font-size: 14px;
        color: #2c3e50;
        font-weight: 500;
        margin-right: 4px;
    }

    .target-type {
        font-size: 12px;
        color: #6c757d;
    }

    .detection-result-panel {
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

    .result-content {
        display: flex;
        flex-direction: column;
        gap: 10px;
    }

    .result-item {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 8px 12px;
        background: #f8f9fa;
        border-radius: 6px;
    }

        .result-item.full-width {
            flex-direction: column;
            align-items: flex-start;
            gap: 5px;
        }

    .item-label {
        color: #5a6c7d;
        font-size: 13px;
        font-weight: 500;
    }

    .item-value {
        color: #2c3e50;
        font-size: 14px;
        font-weight: 600;
    }

    .status-value {
        color: #27ae60;
        font-weight: 700;
    }

    @media (max-width: 1024px) {
        .viewer {
            grid-template-columns: 1fr;
            gap: 20px;
        }

        .asset-container {
            min-height: 500px;
        }
    }

    @media (max-width: 768px) {
        .detection-container {
            padding: 20px;
        }

        .image-wrapper.large-image {
            min-height: 300px;
            max-height: 350px;
        }
    }

    @media (max-width: 480px) {
        .detection-root {
            padding: 10px;
        }

        .detection-container {
            padding: 15px;
        }

        .detected-targets-grid {
            grid-template-columns: 1fr;
        }
    }
</style>