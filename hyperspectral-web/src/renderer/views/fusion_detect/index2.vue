<template>
    <div class="fusion-page">
        <div class="fusion-container">
            <!-- 标题 -->
            <h2 class="page-title">融合计算成像</h2>

            <!-- 主要内容区域 -->
            <div class="fusion-content">
                <!-- 左侧：输入图像 -->
                <div class="input-section">
                    <!-- 模态一：高光谱图像 -->
                    <div class="input-card modal-one" ref="modalOne">
                        <div class="card-header">
                            <h3 class="card-title">模态一数据：高光谱图像</h3>
                        </div>
                        <div class="card-content">
                            <el-upload class="image-upload"
                                       :show-file-list="false"
                                       :before-upload="beforeUploadMat"
                                       :on-change="handleChange1"
                                       accept=".mat">
                                <el-button size="medium" type="primary" class="upload-btn">
                                    <i class="el-icon-upload"></i>
                                    选择数据
                                </el-button>
                            </el-upload>

                            <div class="image-preview" v-if="previewUrl1">
                                <img :src="previewUrl1" class="preview-image" alt="高光谱图像预览" />
                            </div>

                            <div class="file-info" v-if="imageName1">
                                <el-tag type="info">{{ imageName1 }}</el-tag>
                            </div>

                            <div class="image-info" v-if="matInfo1">
                                <el-card class="info-card">
                                    <div class="info-content">
                                        <div class="info-item">
                                            <span class="info-label">图像尺寸:</span>
                                            <span class="info-value">{{ matInfo1.resolution }}</span>
                                        </div>
                                        <div class="info-item">
                                            <span class="info-label">光谱波段数:</span>
                                            <span class="info-value">{{ matInfo1.bands }}</span>
                                        </div>
                                        <div class="info-item">
                                            <span class="info-label">数据维度:</span>
                                            <span class="info-value">{{ matInfo1.dimensions }}</span>
                                        </div>
                                        <div class="info-item" v-if="matInfo1.wavelength_range">
                                            <span class="info-label">波段范围:</span>
                                            <span class="info-value">{{ matInfo1.wavelength_range }}</span>
                                        </div>
                                    </div>
                                </el-card>
                            </div>
                        </div>
                    </div>

                    <!-- 模态二：多光谱图像 -->
                    <div class="input-card modal-two" ref="modalTwo">
                        <div class="card-header">
                            <h3 class="card-title">模态二数据：多光谱图像</h3>
                        </div>
                        <div class="card-content">
                            <el-upload class="image-upload"
                                       :show-file-list="false"
                                       :before-upload="beforeUploadImage"
                                       :on-change="handleChange2"
                                       accept="image/*">
                                <el-button size="medium" type="primary" class="upload-btn">
                                    <i class="el-icon-upload"></i>
                                    选择数据
                                </el-button>
                            </el-upload>

                            <div class="image-preview" v-if="imageUrl2">
                                <img :src="imageUrl2" class="preview-image" alt="多光谱图像" />
                            </div>

                            <div class="file-info" v-if="imageName2">
                                <el-tag type="info">{{ imageName2 }}</el-tag>
                            </div>

                            <div class="image-info" v-if="imageInfo2">
                                <el-card class="info-card">
                                    <div class="info-content">
                                        <div class="info-item">
                                            <span class="info-label">图像尺寸:</span>
                                            <span class="info-value">{{ imageInfo2.resolution }}</span>
                                        </div>
                                        <div class="info-item">
                                            <span class="info-label">文件格式:</span>
                                            <span class="info-value">{{ imageInfo2.format }}</span>
                                        </div>
                                        <div class="info-item">
                                            <span class="info-label">文件大小:</span>
                                            <span class="info-value">{{ imageInfo2.file_size }}</span>
                                        </div>
                                        <div class="info-item" v-if="imageInfo2.mode">
                                            <span class="info-label">色彩模式:</span>
                                            <span class="info-value">{{ imageInfo2.mode }}</span>
                                        </div>
                                    </div>
                                </el-card>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- 右侧：融合结果和探测结果 -->
                <div class="result-section">
                    <!-- 融合结果图像 - 与模态一对齐 -->
                    <div class="result-card fusion-result" ref="fusionResult" :style="resultCardStyle">
                        <div class="card-header">
                            <h3 class="card-title">融合结果</h3>
                        </div>
                        <div class="card-content">
                            <div class="scrollable-content" v-if="imageUrl3">
                                <div class="result-preview">
                                    <div class="image-wrapper">
                                        <img :src="imageUrl3" class="result-image" alt="融合结果" />
                                    </div>

                                    <!-- 融合结果参数信息 -->
                                    <div class="fusion-result-panel" v-if="fusionResultInfo">
                                        <div class="result-header">
                                            <h3>融合结果统计</h3>
                                        </div>
                                        <div class="result-content">
                                            <div class="result-item">
                                                <span class="item-label">图像尺寸:</span>
                                                <span class="item-value">{{ fusionResultInfo.resolution }}</span>
                                            </div>
                                            <div class="result-item">
                                                <span class="item-label">光谱波段数:</span>
                                                <span class="item-value">{{ fusionResultInfo.bands }}</span>
                                            </div>
                                            <div class="result-item">
                                                <span class="item-label">处理时间:</span>
                                                <span class="item-value">{{ fusionResultInfo.processing_time }}</span>
                                            </div>
                                            <div class="result-item" v-if="fusionResultInfo.file_size">
                                                <span class="item-label">文件大小:</span>
                                                <span class="item-value">{{ fusionResultInfo.file_size }}</span>
                                            </div>
                                            <div class="result-item full-width">
                                                <span class="item-label">融合状态:</span>
                                                <span class="item-value status-value">完成</span>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <div class="empty-state" v-else>
                                <div class="empty-icon">🖼️</div>
                                <h4>等待融合结果</h4>
                                <p>选择文件后点击开始融合</p>
                            </div>
                        </div>
                    </div>

                    <!-- 探测结果 - 与模板保持一致（名称 + 数量；统计面板） -->
                    <div class="result-card detection-result" ref="detectionResult" :style="resultCardStyle">
                        <div class="card-header">
                            <h3 class="card-title">探测结果</h3>
                        </div>
                        <div class="card-content">
                            <div class="scrollable-content" v-if="detectionUrl">
                                <div class="result-preview">
                                    <div class="image-wrapper">
                                        <img :src="detectionUrl" class="result-image" alt="探测结果" />
                                    </div>

                                    <!-- 检测结果（用 materials 驱动，合并 target_statistics 数量；即使为 0 也展示） -->
                                    <div v-if="detectionInfo" class="detected-targets-section">
                                        <h4 class="detected-targets-title">检测结果</h4>
                                        <div class="detected-targets-grid">
                                            <div v-for="t in displayTargets" :key="t.type" class="detected-target-item">
                                                <div class="target-color-indicator" :style="{ backgroundColor: t.color_hex }"></div>
                                                <span class="target-name">{{ t.name }}</span>
                                                <span class="target-count">{{ t.object_count }}个</span>
                                            </div>
                                        </div>
                                    </div>

                                    <!-- 探测结果信息（对齐模板） -->
                                    <div class="detection-result-panel" v-if="detectionInfo">
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
                                            <div class="result-item">
                                                <span class="item-label">发现目标:</span>
                                                <span class="item-value highlight">{{ totalObjectsCount }}个</span>
                                            </div>
                                            <div class="result-item full-width">
                                                <span class="item-label">检测状态:</span>
                                                <span class="item-value status-value">完成</span>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <div class="empty-state" v-else>
                                <div class="empty-icon">🔍</div>
                                <h4>等待探测结果</h4>
                                <p>融合完成后显示探测结果</p>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <!-- 融合按钮 -->
            <div class="action-section">
                <el-button type="primary"
                           @click="startFusion"
                           class="fusion-button"
                           :disabled="!selectedFile1 || !selectedFile2"
                           :loading="loading">
                    <i class="el-icon-magic-stick"></i>
                    {{ loading ? '融合中...' : '融合探测' }}
                </el-button>
            </div>
        </div>
    </div>
</template>

<script>
    export default {
        name: "ImageFusion",
        data() {
            return {
                targetStatistics: {}, // 目标统计：{ type: { object_count } }
                loading: false,
                previewUrl1: "",
                imageUrl2: "",
                imageUrl3: "",
                detectionUrl: "",
                imageName1: "",
                imageName2: "",
                selectedFile1: null,
                selectedFile2: null,
                matInfo1: null,
                imageInfo2: null,
                fusionResultInfo: null,
                detectionInfo: null,
                detectedTargets: [], // 可选，后端可能不返回；不作为显示驱动
                inputSectionHeight: 0,
                // 本地材料字典（用它做显示驱动）
                materials: [
                    { name: "假草皮", color: "#008000", targets: ["d1"] },
                    { name: "木板", color: "#DAA520", targets: ["d2"] },
                    { name: "竹材", color: "#808000", targets: ["d3"] },
                    { name: "棕色迷彩服", color: "#8B4513", targets: ["d4"] },
                    { name: "蓝色迷彩服", color: "#0000FF", targets: ["d5"] },
                    { name: "绿色伪装服", color: "#00FF00", targets: ["d6"] },
                    { name: "棕色伪装服", color: "#A0522D", targets: ["d7"] },
                    { name: "伪装网", color: "#708090", targets: ["d8"] },
                    { name: "军事目标", color: "#FF0000", targets: ["d9"] }
                ]
            }
        },
        computed: {
            allMaterialNames() {
                return this.materials.map(function (m) { return m.name })
            },
            allTargets() {
                var set = {}
                this.materials.forEach(function (m) {
                    if (m.targets) {
                        m.targets.forEach(function (t) { set[t] = 1 })
                    }
                })
                return Object.keys(set)
            },
            detectedTargetsCount() {
                return this.detectedTargets ? this.detectedTargets.length : 0
            },
            resultCardStyle() {
                const gap = 25
                const height = (this.inputSectionHeight - gap) / 2
                return { height: height + 'px', minHeight: '200px' }
            },
            // 计算“发现目标”总数：对 target_statistics 的 object_count 求和
            totalObjectsCount() {
                if (!this.targetStatistics || typeof this.targetStatistics !== 'object') return 0
                var sum = 0
                var arr = Object.values(this.targetStatistics)
                for (var i = 0; i < arr.length; i++) {
                    var n = (arr[i] && typeof arr[i].object_count === 'number') ? arr[i].object_count : 0
                    sum += n
                }
                return sum
            },
            // 显示列表：以 materials 为基准，把 target_statistics 的数量合并进去（无则 0）
            displayTargets() {
                var out = []
                var stats = this.targetStatistics || {}
                for (var i = 0; i < this.materials.length; i++) {
                    var m = this.materials[i]
                    var count = 0
                    if (m.targets && m.targets.length) {
                        for (var j = 0; j < m.targets.length; j++) {
                            var key = m.targets[j]
                            if (stats[key] && typeof stats[key].object_count === 'number') {
                                count += stats[key].object_count
                            }
                        }
                    }
                    out.push({
                        name: m.name,
                        type: (m.targets && m.targets[0]) ? m.targets[0] : ('m' + i),
                        color_hex: m.color,
                        object_count: count
                    })
                }
                return out
            }
        },
        methods: {
            beforeUploadMat(file) {
                const isMat = file.name && file.name.slice(-4) === '.mat'
                if (!isMat) {
                    this.$message.error('只能上传.mat文件')
                    return false
                }
                return true
            },
            beforeUploadImage(file) {
                const isImage = file.type && file.type.indexOf('image/') === 0
                if (!isImage) {
                    this.$message.error('只能上传图片文件')
                    return false
                }
                return true
            },
            async handleChange1(file) {
                if (!file) return
                this.imageName1 = file.name
                this.selectedFile1 = file.raw
                this.matInfo1 = null
                try {
                    this.loading = true
                    const formData = new FormData()
                    formData.append("mat_file", this.selectedFile1)
                    const response = await fetch("http://localhost:8000/get_mat_preview", {
                        method: "POST",
                        body: formData
                    })
                    if (!response.ok) throw new Error("预览图生成失败")
                    const data = await response.json()
                    this.previewUrl1 = data.preview_url
                    this.matInfo1 = data.mat_info
                    this.$message.success("MAT文件加载成功")
                } catch (err) {
                    console.error(err)
                    this.$message.error("文件处理失败")
                    this.previewUrl1 = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='200' height='200' viewBox='0 0 200 200'%3E%3Crect width='200' height='200' fill='%23f0f0f0'/%3E%3Ctext x='50%25' y='50%25' dominant-baseline='middle' text-anchor='middle' font-family='Arial' font-size='14' fill='%23999'%3EMAT文件预览%3C/text%3E%3C/svg%3E"
                } finally {
                    this.loading = false
                    this.$nextTick(this.updateInputSectionHeight)
                }
            },
            async handleChange2(file) {
                this.imageName2 = file.name
                this.imageUrl2 = URL.createObjectURL(file.raw)
                this.selectedFile2 = file.raw
                this.imageInfo2 = null
                try {
                    const formData = new FormData()
                    formData.append("image_file", this.selectedFile2)
                    const response = await fetch("http://localhost:8000/get_image_info", {
                        method: "POST",
                        body: formData
                    })
                    if (response.ok) {
                        const data = await response.json()
                        this.imageInfo2 = data.image_info
                    }
                } catch (err) {
                    console.error("获取图片信息失败:", err)
                } finally {
                    this.$nextTick(this.updateInputSectionHeight)
                }
            },
            updateInputSectionHeight() {
                if (this.$refs.modalOne && this.$refs.modalTwo) {
                    const gap = 25
                    this.inputSectionHeight = this.$refs.modalOne.offsetHeight + this.$refs.modalTwo.offsetHeight + gap
                }
            },
            async startFusion() {
                if (!this.selectedFile1 || !this.selectedFile2) {
                    this.$message.warning("请先选择两个文件")
                    return
                }

                this.loading = true
                this.fusionResultInfo = null
                this.detectionInfo = null
                this.detectedTargets = []
                this.targetStatistics = {}

                try {
                    const formData = new FormData()
                    formData.append("file1", this.selectedFile1)
                    formData.append("file2", this.selectedFile2)
                    formData.append("targets", JSON.stringify(this.allTargets))

                    const response = await fetch("http://localhost:8000/start_fusion", {
                        method: "POST",
                        body: formData
                    })

                    if (!response.ok) throw new Error("融合检测失败")

                    const data = await response.json()

                    // 融合结果
                    this.imageUrl3 = data.image_url
                    this.fusionResultInfo = data.fusion_info

                    // 检测结果
                    if (data.image) {
                        this.detectionUrl = 'data:image/png;base64,' + data.image

                        var runtime = (typeof data.runtime !== 'undefined' && data.runtime !== null) ? data.runtime : '0.00'
                        this.detectionInfo = {
                            processingTime: runtime,
                            materialCount: this.allMaterialNames.length,
                            targetCount: this.allTargets.length
                        }

                        // 从后端获取统计与（可选）目标
                        this.targetStatistics = data.target_statistics && typeof data.target_statistics === 'object' ? data.target_statistics : {}
                        this.detectedTargets = Array.isArray(data.detected_targets) ? data.detected_targets : []

                        this.$message.success("融合与检测成功！")
                    } else {
                        this.$message.warning("融合成功，但检测结果为空！")
                    }
                } catch (err) {
                    console.error(err)
                    this.$message.error("融合或检测失败，请检查后端服务！")
                } finally {
                    this.loading = false
                }
            }
        },
        mounted() {
            this.$nextTick(this.updateInputSectionHeight)
            window.addEventListener('resize', this.updateInputSectionHeight)
        },
        beforeDestroy() {
            window.removeEventListener('resize', this.updateInputSectionHeight)
        }
    }
</script>

<style scoped>
    .fusion-page {
        display: flex;
        justify-content: center;
        padding: 20px;
        background: #f5f7fa;
        min-height: 100vh;
        font-family: 'Helvetica Neue', 'Segoe UI', Arial, sans-serif;
    }

    .fusion-container {
        width: 100%;
        max-width: 1400px;
        background: #fff;
        border-radius: 16px;
        padding: 30px;
        box-shadow: 0 10px 30px rgba(0,0,0,0.1);
    }

    .page-title {
        font-size: 32px;
        color: #1a202c;
        text-align: center;
        margin-bottom: 40px;
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        -webkit-background-clip: text;
        -webkit-text-fill-color: transparent;
        background-clip: text;
    }

    .fusion-content {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 30px;
        margin-bottom: 30px;
        align-items: start;
    }

    .input-section {
        display: flex;
        flex-direction: column;
        gap: 25px;
    }

    .result-section {
        display: flex;
        flex-direction: column;
        gap: 25px;
        height: 100%;
    }

    .input-card, .result-card {
        background: #fafafa;
        border: 2px dashed #e2e8f0;
        border-radius: 12px;
        padding: 25px;
        transition: all 0.3s ease;
        display: flex;
        flex-direction: column;
        box-sizing: border-box;
    }

    /* 输入卡片高度 */
    .modal-one, .modal-two {
        min-height: 400px;
        height: auto;
    }
    /* 结果卡片高度由 JS 控制 */
    .fusion-result, .detection-result {
        min-height: 200px;
    }

    .input-card:hover, .result-card:hover {
        border-color: #667eea;
        box-shadow: 0 4px 12px rgba(102,126,234,0.1);
    }

    .card-header {
        margin-bottom: 20px;
        text-align: center;
    }

    .card-title {
        font-size: 18px;
        color: #2d3748;
        font-weight: 600;
        margin: 0;
    }

    .card-content {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 15px;
        flex: 1;
        overflow: hidden;
        position: relative;
    }

    /* 滚动区域 */
    .scrollable-content {
        width: 100%;
        height: 100%;
        overflow-y: auto;
        overflow-x: hidden;
        padding-right: 5px;
    }

        .scrollable-content::-webkit-scrollbar {
            width: 6px;
        }

        .scrollable-content::-webkit-scrollbar-track {
            background: #f1f1f1;
            border-radius: 3px;
        }

        .scrollable-content::-webkit-scrollbar-thumb {
            background: #c1c1c1;
            border-radius: 3px;
        }

            .scrollable-content::-webkit-scrollbar-thumb:hover {
                background: #a8a8a8;
            }

    .image-upload {
        width: 100%;
        text-align: center;
    }

    .upload-btn {
        padding: 12px 24px;
        border-radius: 8px;
        font-weight: 600;
    }

    .image-preview {
        width: 100%;
        max-width: 300px;
        display: flex;
        justify-content: center;
    }

    .preview-image {
        max-width: 100%;
        max-height: 200px;
        border-radius: 8px;
        box-shadow: 0 4px 8px rgba(0,0,0,0.1);
        border: 1px solid #e9ecef;
    }

    .result-preview {
        width: 100%;
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 15px;
        padding-bottom: 10px;
    }

    .image-wrapper {
        position: relative;
        display: flex;
        justify-content: center;
        width: 100%;
        max-height: 280px;
        min-height: 250px;
    }

    .result-image {
        max-width: 100%;
        max-height: 260px;
        width: auto;
        height: auto;
        border-radius: 8px;
        box-shadow: 0 4px 12px rgba(0,0,0,0.15);
        border: 2px solid #e9ecef;
        transition: transform 0.2s ease;
        object-fit: contain;
    }

        .result-image:hover {
            transform: scale(1.02);
        }

    .empty-state {
        text-align: center;
        padding: 40px 20px;
        color: #6c757d;
        flex: 1;
        display: flex;
        flex-direction: column;
        justify-content: center;
        align-items: center;
        height: 100%;
    }

    .empty-icon {
        font-size: 3em;
        margin-bottom: 15px;
        opacity: .7;
    }

    .empty-state h4 {
        margin: 0 0 10px 0;
        color: #495057;
        font-size: 1.2em;
    }

    .empty-state p {
        margin: 0;
        font-size: .9em;
    }

    /* 结果面板样式 */
    .fusion-result-panel, .detection-result-panel {
        background: #ffffff;
        border-radius: 12px;
        box-shadow: 0 4px 12px rgba(0,0,0,0.1);
        padding: 15px;
        width: 100%;
        font-family: 'Arial', sans-serif;
        border: 1px solid #e8e8e8;
        margin-top: 5px;
    }

    .result-header {
        text-align: center;
        margin-bottom: 12px;
        padding-bottom: 8px;
        border-bottom: 2px solid #f0f0f0;
    }

        .result-header h3 {
            margin: 0;
            color: #2c3e50;
            font-size: 15px;
            font-weight: 600;
        }

    .result-content {
        display: flex;
        flex-direction: column;
        gap: 8px;
    }

    .result-item {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 6px 10px;
        background: #f8f9fa;
        border-radius: 6px;
        font-size: 12px;
    }

        .result-item.full-width {
            flex-direction: column;
            align-items: flex-start;
            gap: 4px;
        }

    .item-label {
        color: #5a6c7d;
        font-size: 14px;
        font-weight: 500;
    }

    .item-value {
        color: #2c3e50;
        font-size: 14px;
        font-weight: 600;
    }

        .item-value.highlight {
            color: #e74c3c;
            font-weight: 700;
        }

    .status-value {
        color: #27ae60;
        font-weight: 700;
    }

    /* 检测到的目标展示（与模板一致：名称 + 数量徽标） */
    .detected-targets-section {
        width: 100%;
        margin: 5px 0;
        padding: 12px;
        background: #f8f9fa;
        border-radius: 8px;
        border: 1px solid #e9ecef;
    }

    .detected-targets-title {
        text-align: center;
        margin-bottom: 10px;
        color: #2c3e50;
        font-size: 14px;
        font-weight: 600;
    }

    .detected-targets-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
        gap: 8px;
    }

    .detected-target-item {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 10px 12px;
        background: #fff;
        border-radius: 8px;
        border: 1px solid #dee2e6;
        font-size: 13px;
        transition: all 0.3s ease;
    }

        .detected-target-item:hover {
            box-shadow: 0 2px 8px rgba(0,0,0,0.08);
            transform: translateY(-1px);
        }

    .target-color-indicator {
        width: 18px;
        height: 18px;
        border-radius: 4px;
        margin-right: 10px;
        border: 1px solid #ccc;
        flex-shrink: 0;
    }

    .target-name {
        font-size: 13px;
        color: #2c3e50;
        font-weight: 600;
        flex-grow: 1;
    }

    .target-count {
        font-size: 13px;
        color: #e74c3c;
        font-weight: 700;
        background: #ffeaea;
        padding: 3px 8px;
        border-radius: 12px;
        min-width: 42px;
        text-align: center;
    }

    /* 其他 */
    .image-info {
        width: 100%;
        max-width: 600px;
    }

    .info-card {
        background: #f8f9fa;
        border: 1px solid #e9ecef;
        border-radius: 8px;
        width: 100%;
    }

    .info-content {
        padding: 15px;
    }

    .info-item {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 8px;
        padding: 5px 0;
        border-bottom: 1px solid #f1f3f4;
    }

        .info-item:last-child {
            margin-bottom: 0;
            border-bottom: none;
        }

    .info-label {
        color: #6c757d;
        font-weight: 500;
        font-size: 14px;
        white-space: nowrap;
    }

    .info-value {
        color: #495057;
        font-weight: 600;
        font-size: 14px;
        text-align: right;
        white-space: nowrap;
        margin-left: 10px;
    }

    .file-info {
        margin-top: 10px;
    }

    .action-section {
        display: flex;
        justify-content: center;
        margin-top: 20px;
    }

    .fusion-button {
        padding: 14px 40px;
        font-size: 16px;
        font-weight: 600;
        border-radius: 10px;
        background: linear-gradient(135deg,#667eea 0%,#764ba2 100%);
        border: none;
        box-shadow: 0 4px 15px rgba(102,126,234,0.4);
        transition: all 0.3s ease;
        color: white;
    }

        .fusion-button:hover:not(:disabled) {
            transform: translateY(-2px);
            box-shadow: 0 6px 20px rgba(102,126,234,0.5);
        }

        .fusion-button:disabled {
            opacity: 0.6;
            cursor: not-allowed;
            transform: none;
        }

    /* 响应式设计 */
    @media (max-width: 1024px) {
        .fusion-content {
            grid-template-columns: 1fr;
            gap: 20px;
        }

        .fusion-container {
            padding: 20px;
        }

        .input-card, .result-card {
            height: auto;
            min-height: 400px;
        }

        .image-info {
            max-width: 100%;
        }

        .detected-targets-grid {
            grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
        }

        .image-wrapper {
            max-height: 220px;
        }

        .result-image {
            max-height: 200px;
        }
    }

    @media (max-width: 768px) {
        .fusion-page {
            padding: 10px;
        }

        .page-title {
            font-size: 24px;
        }

        .input-card, .result-card {
            padding: 20px;
            min-height: 400px;
        }

        .preview-image {
            max-height: 180px;
        }

        .result-image {
            max-height: 180px;
        }

        .image-wrapper {
            max-height: 200px;
        }

        .fusion-result-panel, .detection-result-panel {
            padding: 12px;
        }
    }

    @media (max-width: 480px) {
        .card-title {
            font-size: 16px;
        }

        .info-label, .info-value {
            font-size: 12px;
        }

        .detected-targets-grid {
            grid-template-columns: 1fr;
        }

        .item-label, .item-value {
            font-size: 11px;
        }

        .result-item {
            padding: 5px 8px;
        }
    }
</style>
