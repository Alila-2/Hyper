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
                    <div class="input-card">
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
                                            <span class="info-value">320</span>
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

                    <!-- 模态二：全色图像 -->
                    <div class="input-card">
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
                                <img :src="imageUrl2" class="preview-image" alt="全色图像" />
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

                <!-- 右侧：融合结果和光谱可视化 -->
                <div class="result-section">
                    <!-- 融合结果图像 -->
                    <div class="result-card">
                        <div class="card-header">
                            <h3 class="card-title">融合结果</h3>
                        </div>
                        <div class="card-content">
                            <div class="result-preview" v-if="imageUrl3">
                                <div class="image-wrapper" ref="imgWrapper">
                                    <img :src="imageUrl3"
                                         class="result-image"
                                         alt="融合结果"
                                         ref="fusionImg"
                                         @mousemove="onMouseMove"
                                         @mouseleave="onMouseLeave" />
                                    <div v-if="showCrosshair"
                                         class="crosshair"
                                         :style="crosshairStyle"></div>
                                </div>
                                <div class="coordinate-info">
                                    <el-tag type="info">
                                        坐标: ({{ currentCoords ? currentCoords.x : '-' }}, {{ currentCoords ? currentCoords.y : '-' }})
                                    </el-tag>
                                </div>
                            </div>

                            <div class="empty-state" v-else>
                                <div class="empty-icon">🖼️</div>
                                <h4>等待融合结果</h4>
                                <p>选择文件后点击开始融合</p>
                            </div>

                            <!-- 光谱曲线区域 -->
                            <div class="spectrum-section" v-if="imageUrl3">
                                <div class="spectrum-info" v-if="spectrumData.length > 0">
                                    波段数: {{ spectrumData.length }}
                                </div>
                                <div ref="chart" class="spectrum-chart"></div>
                                <div class="spectrum-footer">
                                    <div class="spectrum-stats">
                                        <div class="stat-item">
                                            <span class="stat-label">最小值:</span>
                                            <span class="stat-value">{{ minSpectrumValue.toFixed(4) }}</span>
                                        </div>
                                        <div class="stat-item">
                                            <span class="stat-label">最大值:</span>
                                            <span class="stat-value">{{ maxSpectrumValue.toFixed(4) }}</span>
                                        </div>
                                        <div class="stat-item">
                                            <span class="stat-label">平均值:</span>
                                            <span class="stat-value">{{ avgSpectrumValue.toFixed(4) }}</span>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <div class="result-info" v-if="fusionResultInfo">
                                <el-card class="info-card">
                                    <div class="info-content">
                                        <div class="info-item">
                                            <span class="info-label">图像尺寸:</span>
                                            <span class="info-value">{{ fusionResultInfo.resolution }}</span>
                                        </div>
                                        <div class="info-item">
                                            <span class="info-label">光谱波段数:</span>
                                            <span class="info-value">{{ fusionResultInfo.bands }}</span>
                                        </div>
                                        <!--div class="info-item">
                                            <span class="info-label">处理时间:</span>
                                            <span class="info-value">{{ fusionResultInfo.processing_time }}</span>
                                        </div-->
                                        <div class="info-item">
                                            <span class="info-label">文件大小:</span>
                                            <span class="info-value">{{ fusionResultInfo.file_size }}</span>
                                        </div>
                                    </div>
                                </el-card>
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
                    {{ loading ? '融合中...' : '开始融合' }}
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
                loading: false,
                previewUrl1: "",
                imageUrl2: "",
                imageUrl3: "",
                imageName1: "",
                imageName2: "",
                selectedFile1: null,
                selectedFile2: null,
                matInfo1: null,
                imageInfo2: null,
                fusionResultInfo: null,

                // 光谱可视化相关
                showCrosshair: false,
                crosshairStyle: {},
                currentCoords: null,
                spectrumData: [],
                minSpectrumValue: 0,
                maxSpectrumValue: 0,
                avgSpectrumValue: 0,
                chart: null,
                bands: [],
                fusionWidth: 0,
                fusionHeight: 0,
                echarts: null,
                totalBands: 0
            };
        },
        methods: {
            beforeUploadMat(file) {
                const isMat = file.name.endsWith('.mat');
                if (!isMat) {
                    this.$message.error('只能上传.mat文件');
                    return false;
                }
                return true;
            },

            beforeUploadImage(file) {
                const isImage = file.type.startsWith("image/");
                if (!isImage) {
                    this.$message.error('只能上传图片文件');
                    return false;
                }
                return isImage;
            },

            async handleChange1(file, targetVar = 'hyperspectral_data') {
                if (!file) return;

                this.imageName1 = file.name;
                this.selectedFile1 = file.raw;
                this.matInfo1 = null;

                try {
                    this.loading = true;
                    const formData = new FormData();
                    formData.append("target_var", targetVar);
                    formData.append("mat_file", this.selectedFile1);
                    

                    const response = await fetch("http://localhost:8000/get_mat_preview", {
                        method: "POST",
                        body: formData,
                    });

                    if (!response.ok) throw new Error("预览图生成失败");

                    const data = await response.json();
                    this.previewUrl1 = data.preview_url;
                    this.matInfo1 = data.mat_info;
                    this.$message.success("MAT文件加载成功");
                } catch (err) {
                    console.error(err);
                    this.$message.error("文件处理失败");
                    this.previewUrl1 = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='200' height='200' viewBox='0 0 200 200'%3E%3Crect width='200' height='200' fill='%23f0f0f0'/%3E%3Ctext x='50%25' y='50%25' dominant-baseline='middle' text-anchor='middle' font-family='Arial' font-size='14' fill='%23999'%3EMAT文件预览%3C/text%3E%3C/svg%3E";
                } finally {
                    this.loading = false;
                }
            },

            async handleChange2(file) {
                this.imageName2 = file.name;
                this.imageUrl2 = URL.createObjectURL(file.raw);
                this.selectedFile2 = file.raw;
                this.imageInfo2 = null;

                try {
                    const formData = new FormData();
                    formData.append("image_file", this.selectedFile2);

                    const response = await fetch("http://localhost:8000/get_image_info", {
                        method: "POST",
                        body: formData,
                    });

                    if (response.ok) {
                        const data = await response.json();
                        this.imageInfo2 = data.image_info;
                    }
                } catch (err) {
                    console.error("获取图片信息失败:", err);
                }
            },

            async startFusion() {
                if (!this.selectedFile1 || !this.selectedFile2) {
                    this.$message.warning("请先选择两个文件");
                    return;
                }

                this.loading = true;
                this.fusionResultInfo = null;
                this.spectrumData = []; // 清空之前的光谱数据

                const formData = new FormData();
                formData.append("file1", this.selectedFile1);
                formData.append("file2", this.selectedFile2);

                try {
                    this.$message.info("开始图像融合处理...");

                    const response = await fetch("http://localhost:8000/start_fusion_1", {
                        method: "POST",
                        body: formData,
                    });

                    if (!response.ok) throw new Error("融合失败");

                    const data = await response.json();
                    this.imageUrl3 = data.image_url;
                    this.fusionResultInfo = data.fusion_info;

                    // 获取融合图像尺寸信息
                    await this.loadFusionInfo();

                    // 初始化图表
                    this.$nextTick(() => {
                        this.initChart();
                    });

                    this.$message.success("图像融合成功！");
                } catch (err) {
                    console.error(err);
                    this.$message.error("融合失败，请检查文件格式或网络连接！");
                } finally {
                    this.loading = false;
                }
            },

            async loadFusionInfo() {
                try {
                    const response = await fetch('http://localhost:8000/api/fusion-info');
                    if (response.ok) {
                        const data = await response.json();
                        this.fusionWidth = data.width;
                        this.fusionHeight = data.height;
                        this.totalBands = data.bands;
                        // 生成波段数组 - 与高光谱界面保持一致
                        const step = Math.round(1301 / this.totalBands);
                        this.bands = Array.from({ length: this.totalBands }, (_, i) => 400 + i * step);
                    }
                } catch (err) {
                    console.error('获取融合信息失败:', err);
                }
            },

            async initChart() {
                // 动态导入echarts
                if (!this.echarts) {
                    try {
                        this.echarts = await import('echarts');
                    } catch (error) {
                        console.error('Failed to load echarts:', error);
                        return;
                    }
                }

                if (this.chart) {
                    this.chart.dispose();
                }

                this.chart = this.echarts.init(this.$refs.chart);
                // 使用与高光谱界面完全相同的图表配置
                const option = {
                    title: {
                        text: '光谱强度',
                        left: 'center',
                        textStyle: {
                            fontSize: 16,
                            fontWeight: '500',
                            color: '#2c3e50'
                        }
                    },
                    tooltip: {
                        trigger: 'axis',
                        backgroundColor: 'rgba(255, 255, 255, 0.95)',
                        borderColor: '#e1e8ed',
                        borderWidth: 1,
                        textStyle: {
                            color: '#2c3e50'
                        },
                        formatter: function (params) {
                            if (params && params.length > 0) {
                                return `波段 ${params[0].axisValue}: ${params[0].value.toFixed(4)}`;
                            }
                            return '';
                        }
                    },
                    grid: {
                        left: '60px',
                        right: '30px',
                        top: '60px',
                        bottom: '60px'
                    },
                    xAxis: {
                        name: '光谱范围',
                        nameLocation: 'middle',
                        nameGap: 30,
                        nameTextStyle: {
                            fontSize: 14,
                            color: '#666'
                        },
                        type: 'category',
                        data: [],
                        axisLine: {
                            lineStyle: { color: '#d1d5db' }
                        },
                        axisTick: {
                            lineStyle: { color: '#d1d5db' }
                        },
                        axisLabel: {
                            color: '#666',
                            fontSize: 12
                        }
                    },
                    yAxis: {
                        name: '光强',
                        nameLocation: 'middle',
                        nameGap: 40,
                        nameTextStyle: {
                            fontSize: 14,
                            color: '#666'
                        },
                        type: 'value',
                        axisLine: {
                            lineStyle: { color: '#d1d5db' }
                        },
                        axisTick: {
                            lineStyle: { color: '#d1d5db' }
                        },
                        axisLabel: {
                            color: '#666',
                            fontSize: 12,
                            formatter: '{value}'
                        },
                        splitLine: {
                            lineStyle: {
                                color: '#f3f4f6',
                                type: 'dashed'
                            }
                        }
                    },
                    series: [{
                        name: '光谱反射率',
                        type: 'line',
                        data: [],
                        smooth: false,
                        lineStyle: {
                            width: 2,
                            color: '#3b82f6'
                        },
                        itemStyle: {
                            color: '#3b82f6'
                        },
                        symbol: 'circle',
                        symbolSize: 3,
                        showSymbol: true,
                        emphasis: {
                            focus: 'series',
                            itemStyle: {
                                borderColor: '#3b82f6',
                                borderWidth: 2
                            }
                        }
                    }]
                };
                this.chart.setOption(option);
            },

            onMouseMove(event) {
                if (!this.imageUrl3) return;

                const imgEl = this.$refs.fusionImg;
                const wrapperEl = this.$refs.imgWrapper;

                if (!imgEl || !wrapperEl) return;

                const imgRect = imgEl.getBoundingClientRect();
                const wrapperRect = wrapperEl.getBoundingClientRect();

                const relativeX = event.clientX - imgRect.left;
                const relativeY = event.clientY - imgRect.top;

                if (relativeX < 0 || relativeY < 0 || relativeX > imgRect.width || relativeY > imgRect.height) {
                    return;
                }

                // 转换为融合图像原始坐标
                const x = Math.floor((relativeX / imgRect.width) * this.fusionWidth);
                const y = Math.floor((relativeY / imgRect.height) * this.fusionHeight);

                const validX = Math.max(0, Math.min(x, this.fusionWidth - 1));
                const validY = Math.max(0, Math.min(y, this.fusionHeight - 1));

                // 更新十字线位置 - 使用与高光谱界面相同的样式
                this.showCrosshair = true;
                this.crosshairStyle = {
                    left: `${event.clientX - wrapperRect.left}px`,
                    top: `${event.clientY - wrapperRect.top}px`
                };

                this.currentCoords = { x: validX, y: validY };
                this.fetchFusionSpectrum(validX, validY);
            },

            onMouseLeave() {
                this.showCrosshair = false;
                this.currentCoords = null;
                // 不清空光谱数据，保持最后一次的光谱显示
            },

            async fetchFusionSpectrum(x, y) {
                try {
                    const response = await fetch(`http://localhost:8000/api/fusion-spectrum?x=${x}&y=${y}`);
                    if (response.ok) {
                        const data = await response.json();
                        if (data.spectrum && data.spectrum.length > 0) {
                            this.spectrumData = data.spectrum;

                            // 计算光谱数据统计
                            this.minSpectrumValue = Math.min(...this.spectrumData);
                            this.maxSpectrumValue = Math.max(...this.spectrumData);
                            this.avgSpectrumValue = this.spectrumData.reduce((a, b) => a + b, 0) / this.spectrumData.length;

                            if (this.chart) {
                                this.chart.setOption({
                                    xAxis: {
                                        data: this.bands
                                    },
                                    series: [{
                                        data: this.spectrumData
                                    }]
                                });
                            }
                        }
                    }
                } catch (err) {
                    console.error('获取融合光谱数据失败:', err);
                }
            },

            handleResize() {
                if (this.chart) {
                    this.chart.resize();
                }
            }
        },

        beforeDestroy() {
            if (this.chart) {
                this.chart.dispose();
            }
        }
    };
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
        box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
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
        align-items: start; /* 改为start确保顶部对齐 */
    }

    .input-section {
        display: flex;
        flex-direction: column;
        gap: 25px;
    }

    .input-card {
        background: #fafafa;
        border: 2px dashed #e2e8f0;
        border-radius: 12px;
        padding: 25px;
        transition: all 0.3s ease;
    }

    .result-section {
        height: 100%; /* 确保右侧容器高度100% */
    }

    .result-card {
        background: #fafafa;
        border: 2px dashed #e2e8f0;
        border-radius: 12px;
        padding: 25px;
        transition: all 0.3s ease;
        height: calc(100% - 0px); /* 减去gap的影响，让高度等于左侧两个框之和 */
        display: flex;
        flex-direction: column;
        box-sizing: border-box;
    }

        .input-card:hover, .result-card:hover {
            border-color: #667eea;
            box-shadow: 0 4px 12px rgba(102, 126, 234, 0.1);
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
        gap: 20px;
        flex: 1; /* 让内容区域填充剩余高度 */
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
        max-height: 250px;
        border-radius: 8px;
        box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
        border: 1px solid #e9ecef;
    }

    .result-preview {
        width: 100%;
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 10px;
        flex: 1; /* 填充可用空间 */
    }

    .image-wrapper {
        position: relative;
        display: flex;
        justify-content: center;
        width: 100%;
    }

    .result-image {
        max-width: 100%;
        max-height: 400px;
        border-radius: 8px;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
        border: 2px solid #e9ecef;
        cursor: crosshair;
        transition: transform 0.2s ease;
    }

        .result-image:hover {
            transform: scale(1.02);
        }

    /* 与高光谱界面相同的十字准星样式 */
    .crosshair {
        position: absolute;
        width: 20px;
        height: 20px;
        border: 2px solid #ef4444;
        border-radius: 50%;
        transform: translate(-50%, -50%);
        pointer-events: none;
        background: rgba(239, 68, 68, 0.1);
        animation: pulse 1.5s infinite;
    }

    @keyframes pulse {
        0% {
            box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.7);
        }

        70% {
            box-shadow: 0 0 0 10px rgba(239, 68, 68, 0);
        }

        100% {
            box-shadow: 0 0 0 0 rgba(239, 68, 68, 0);
        }
    }

    .coordinate-info {
        text-align: center;
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
        opacity: 0.7;
    }

    .empty-state h4 {
        margin: 0 0 10px 0;
        color: #495057;
        font-size: 1.2em;
    }

    .empty-state p {
        margin: 0;
        font-size: 0.9em;
    }

    /* 光谱曲线区域 - 与高光谱界面保持一致 */
    .spectrum-section {
        width: 100%;
        margin-top: 20px;
        padding: 20px;
        background: #f8fafc;
        border-radius: 12px;
        border: 1px solid #e2e8f0;
    }

    .spectrum-info {
        font-size: 14px;
        color: #718096;
        padding: 4px 12px;
        border-radius: 20px;
        font-weight: 500;
        align-self: flex-end;
        margin-bottom: 16px;
        background: #e2e8f0;
    }

    .spectrum-chart {
        width: 100%;
        height: 400px;
        border-radius: 12px;
        background: #ffffff;
        box-shadow: inset 0 2px 4px rgba(0, 0, 0, 0.05);
    }

    .spectrum-footer {
        display: flex;
        justify-content: center;
        align-items: center;
        margin-top: 15px;
        padding-top: 15px;
        border-top: 1px solid #e2e8f0;
        width: 100%;
    }

    .spectrum-stats {
        display: flex;
        align-items: center;
        justify-content: space-between; /* 让统计值均匀分布 */
        gap: 30px;
        width: 100%;
        max-width: 500px;
    }

    .stat-item {
        display: flex;
        flex-direction: column;
        align-items: center;
        flex: 1; /* 每个统计项均匀分布 */
    }

    .stat-label {
        font-size: 11px;
        color: #6c757d;
        margin-bottom: 2px;
    }

    .stat-value {
        font-size: 12px;
        font-weight: 600;
        color: #495057;
    }

    .image-info, .result-info {
        width: 100%;
    }

    .info-card {
        background: #f8f9fa;
        border: 1px solid #e9ecef;
        border-radius: 8px;
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
    }

    .info-value {
        color: #495057;
        font-weight: 600;
        font-size: 14px;
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
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        border: none;
        box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
        transition: all 0.3s ease;
        color: white;
    }

        .fusion-button:hover:not(:disabled) {
            transform: translateY(-2px);
            box-shadow: 0 6px 20px rgba(102, 126, 234, 0.5);
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

        .result-card {
            height: auto; /* 响应式时恢复自动高度 */
        }

        .spectrum-stats {
            flex-wrap: wrap;
            justify-content: space-around;
            gap: 20px;
        }

        .spectrum-chart {
            height: 350px;
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
        }

        .preview-image {
            max-height: 200px;
        }

        .result-image {
            max-height: 300px;
        }

        .spectrum-section {
            padding: 15px;
        }

        .spectrum-footer {
            flex-direction: column;
            gap: 10px;
        }

        .spectrum-stats {
            width: 100%;
            justify-content: space-between;
        }

        .spectrum-chart {
            height: 300px;
        }
    }

    @media (max-width: 480px) {
        .card-title {
            font-size: 16px;
        }

        .spectrum-info {
            font-size: 12px;
        }

        .stat-label {
            font-size: 10px;
        }

        .stat-value {
            font-size: 11px;
        }

        .spectrum-stats {
            flex-direction: column;
            gap: 10px;
        }
    }
</style>