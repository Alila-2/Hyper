<template>
    <div id="app">
        <div class="wrap">
            <!-- 左侧：伪彩图显示区域 -->
            <div class="left-panel">
                <div class="image-section">
                    <h2>伪彩图像</h2>
                    <!-- <div class="button-group">
                      <el-button type="primary" @click="loadFusionData">加载融合数据</el-button>
                      <el-button type="success" @click="showSpectrum1" :disabled="!selectedPixel">展示光谱1</el-button>
                      <el-button type="success" @click="showSpectrum2" :disabled="!selectedPixel">展示光谱2</el-button>
                      <el-button type="warning" @click="startDetection">开始检测</el-button>
                    </div> -->

                    <div class="image-container" v-if="fusionImageUrl">
                        <img :src="fusionImageUrl"
                             class="fusion-image"
                             alt="伪彩图像"
                             @click="onImageClick"
                             @mousemove="onImageMouseMove" />
                        <!-- 显示鼠标位置的像素坐标 -->
                        <div v-if="mousePixel" class="pixel-info">
                            鼠标位置: ({{ mousePixel.x }}, {{ mousePixel.y }})
                        </div>
                        <!-- 显示选中的像素点 -->
                        <div v-if="selectedPixel" class="selected-pixel-info">
                            选中像素: ({{ selectedPixel.x }}, {{ selectedPixel.y }})
                        </div>
                    </div>

                    <div v-if="!fusionImageUrl" class="placeholder">
                        <p>点击"加载融合数据"按钮加载图像</p>
                    </div>

                    <div class="button-group">
                        <el-button type="primary" @click="loadFusionData">加载融合数据</el-button>
                        <el-button type="success" @click="showSpectrum1" :disabled="!selectedPixel">展示光谱1</el-button>
                        <el-button type="success" @click="showSpectrum2" :disabled="!selectedPixel">展示光谱2</el-button>
                        <el-button type="warning" @click="startDetection">开始检测</el-button>
                    </div>
                </div>
            </div>

            <!-- 右侧：光谱曲线或检测结果 -->
            <div class="right-panel">
                <!-- 检测结果模式 -->
                <div v-if="showDetectionResult" class="detection-result-section">
                    <h2>检测结果</h2>
                    <div class="result-container">
                        <img v-if="detectionResultUrl"
                             :src="detectionResultUrl"
                             class="detection-result-image"
                             alt="检测结果" />
                        <div v-if="detectionMetrics" class="metrics-info">
                            <h3>检测指标</h3>
                            <p>处理时间: {{ detectionMetrics.time_consumes }}</p>
                            <p>检测性能: {{ detectionMetrics.auc_metrics }}</p>
                        </div>
                    </div>
                </div>

                <!-- 光谱曲线模式 -->
                <div v-else class="spectrum-section">
                    <div class="spectrum-container">
                        <!-- 光谱1 -->
                        <div class="spectrum-chart">
                            <h3>光谱1</h3>
                            <div class="chart-wrapper">
                                <canvas ref="spectrumChart1" width="400" height="200"></canvas>
                            </div>
                            <div v-if="!spectrumData1 && selectedPixel" class="chart-placeholder">
                                点击"展示光谱1"按钮查看光谱曲线
                            </div>
                        </div>

                        <!-- 光谱2 -->
                        <div class="spectrum-chart">
                            <h3>光谱2</h3>
                            <div class="chart-wrapper">
                                <canvas ref="spectrumChart2" width="400" height="200"></canvas>
                            </div>
                            <div v-if="!spectrumData2 && selectedPixel" class="chart-placeholder">
                                点击"展示光谱2"按钮查看光谱曲线
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- 加载提示 -->
        <el-dialog title="加载中" :visible.sync="loading" :close-on-click-modal="false" width="300px">
            <div style="text-align: center;">
                <i class="el-icon-loading" style="font-size: 24px;"></i>
                <p>{{ loadingMessage }}</p>
            </div>
        </el-dialog>
    </div>
</template>

<script>
    export default {
        data() {
            return {
                fusionImageUrl: "", // 伪彩图URL
                selectedPixel: null, // 选中的像素点坐标
                mousePixel: null, // 鼠标当前位置的像素坐标
                spectrumData1: null, // 光谱1数据
                spectrumData2: null, // 光谱2数据
                detectionResultUrl: "", // 检测结果图URL
                detectionMetrics: null, // 检测指标
                showDetectionResult: false, // 是否显示检测结果
                loading: false,
                loadingMessage: "",
                chart1: null, // Chart.js 实例1
                chart2: null, // Chart.js 实例2
            };
        },
        mounted() {
            // 初始化Chart.js（如果需要的话）
            this.initCharts();
        },
        methods: {
            // 加载融合数据
            async loadFusionData() {
                if (this.isPseudoLoading) return
                this.isPseudoLoading = true
                try {
                    const response = await axios.post('http://localhost:8000/pseudocolor')
                    if (response.data.success) {
                        this.pseudoImageUrl = `data:image/png;base64,${response.data.image}`
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

            // 图像点击事件
            onImageClick(event) {
                const rect = event.target.getBoundingClientRect();
                const x = Math.floor((event.clientX - rect.left) * (event.target.naturalWidth / rect.width));
                const y = Math.floor((event.clientY - rect.top) * (event.target.naturalHeight / rect.height));

                this.selectedPixel = { x, y };
                this.$message.success(`已选择像素点: (${x}, ${y})`);
            },

            // 图像鼠标移动事件
            onImageMouseMove(event) {
                const rect = event.target.getBoundingClientRect();
                const x = Math.floor((event.clientX - rect.left) * (event.target.naturalWidth / rect.width));
                const y = Math.floor((event.clientY - rect.top) * (event.target.naturalHeight / rect.height));

                this.mousePixel = { x, y };
            },

            // 展示光谱1
            async showSpectrum1() {
                if (!this.selectedPixel) {
                    this.$message.warning("请先点击图像选择一个像素点");
                    return;
                }

                this.loading = true;
                this.loadingMessage = "正在获取光谱1数据...";

                try {
                    const response = await fetch("http://localhost:8000/get_spectrum1", {
                        method: "POST",
                        headers: {
                            "Content-Type": "application/json",
                        },
                        body: JSON.stringify({
                            x: this.selectedPixel.x,
                            y: this.selectedPixel.y,
                        }),
                    });

                    if (!response.ok) throw new Error("获取光谱1数据失败");

                    const data = await response.json();
                    this.spectrumData1 = data.spectrum_data;
                    this.drawSpectrum(this.spectrumData1, 1);

                } catch (error) {
                    console.error(error);
                    this.$message.error("获取光谱1数据失败！");
                } finally {
                    this.loading = false;
                }
            },

            // 展示光谱2
            async showSpectrum2() {
                if (!this.selectedPixel) {
                    this.$message.warning("请先点击图像选择一个像素点");
                    return;
                }

                this.loading = true;
                this.loadingMessage = "正在获取光谱2数据...";

                try {
                    const response = await fetch("http://localhost:8000/get_spectrum2", {
                        method: "POST",
                        headers: {
                            "Content-Type": "application/json",
                        },
                        body: JSON.stringify({
                            x: this.selectedPixel.x,
                            y: this.selectedPixel.y,
                        }),
                    });

                    if (!response.ok) throw new Error("获取光谱2数据失败");

                    const data = await response.json();
                    this.spectrumData2 = data.spectrum_data;
                    this.drawSpectrum(this.spectrumData2, 2);

                } catch (error) {
                    console.error(error);
                    this.$message.error("获取光谱2数据失败！");
                } finally {
                    this.loading = false;
                }
            },

            // 开始检测
            async startDetection() {
                this.loading = true;
                this.loadingMessage = "正在运行检测算法...";

                try {
                    const response = await fetch("http://localhost:8000/start_detection", {
                        method: "POST",
                    });

                    if (!response.ok) throw new Error("检测失败");

                    const data = await response.json();
                    this.detectionResultUrl = data.result_image_url;
                    this.detectionMetrics = {
                        time_consumes: data.time_consumes,
                        auc_metrics: data.auc_metrics
                    };
                    this.showDetectionResult = true; // 切换到检测结果模式

                } catch (error) {
                    console.error(error);
                    this.$message.error("检测失败！");
                } finally {
                    this.loading = false;
                }
            },

            // 初始化图表
            initCharts() {
                // 这里可以初始化Chart.js或其他图表库
                // 暂时使用简单的Canvas绘制
            },

            // 绘制光谱曲线
            drawSpectrum(data, chartNumber) {
                const canvas = chartNumber === 1 ? this.$refs.spectrumChart1 : this.$refs.spectrumChart2;
                if (!canvas || !data) return;

                const ctx = canvas.getContext('2d');
                const width = canvas.width;
                const height = canvas.height;

                // 清空画布
                ctx.clearRect(0, 0, width, height);

                // 设置样式
                ctx.strokeStyle = chartNumber === 1 ? '#409EFF' : '#67C23A';
                ctx.lineWidth = 2;
                ctx.fillStyle = '#f5f5f5';
                ctx.fillRect(0, 0, width, height);

                // 绘制坐标轴
                ctx.strokeStyle = '#606266';
                ctx.lineWidth = 1;
                ctx.beginPath();
                ctx.moveTo(40, height - 40);
                ctx.lineTo(width - 20, height - 40);
                ctx.moveTo(40, height - 40);
                ctx.lineTo(40, 20);
                ctx.stroke();

                // 绘制光谱曲线
                if (data.length > 0) {
                    const maxVal = Math.max(...data);
                    const minVal = Math.min(...data);
                    const range = maxVal - minVal || 1;

                    ctx.strokeStyle = chartNumber === 1 ? '#409EFF' : '#67C23A';
                    ctx.lineWidth = 2;
                    ctx.beginPath();

                    for (let i = 0; i < data.length; i++) {
                        const x = 40 + (i / (data.length - 1)) * (width - 60);
                        const y = height - 40 - ((data[i] - minVal) / range) * (height - 60);

                        if (i === 0) {
                            ctx.moveTo(x, y);
                        } else {
                            ctx.lineTo(x, y);
                        }
                    }
                    ctx.stroke();

                    // 添加标签
                    ctx.fillStyle = '#606266';
                    ctx.font = '12px Arial';
                    ctx.fillText(`光谱${chartNumber} - 波段: ${data.length}`, 50, 35);
                    ctx.fillText(`最大值: ${maxVal.toFixed(2)}`, width - 150, 35);
                }
            },
        },
    };
</script>

<style scoped>
    .wrap {
        display: flex;
        width: 100%;
        height: 100vh;
        padding: 20px;
        box-sizing: border-box;
    }

    .left-panel {
        flex: 1;
        margin-right: 20px;
        border: 2px solid #e9e9e9;
        border-radius: 10px;
        padding: 20px;
        display: flex;
        flex-direction: column;
    }

    .right-panel {
        flex: 1;
        border: 2px solid #e9e9e9;
        border-radius: 10px;
        padding: 20px;
    }

    .image-section {
        height: 100%;
        display: flex;
        flex-direction: column;
    }

    .button-group {
        margin-bottom: 20px;
        display: flex;
        gap: 10px;
        flex-wrap: wrap;
    }

    .image-container {
        flex: 1;
        display: flex;
        flex-direction: column;
        align-items: center;
        position: relative;
    }

    .fusion-image {
        max-width: 100%;
        max-height: calc(100% - 60px);
        border: 2px solid #ddd;
        border-radius: 5px;
        cursor: crosshair;
        transition: transform 0.2s;
    }

        .fusion-image:hover {
            transform: scale(1.02);
        }

    .pixel-info, .selected-pixel-info {
        position: absolute;
        background: rgba(0, 0, 0, 0.8);
        color: white;
        padding: 5px 10px;
        border-radius: 3px;
        font-size: 12px;
    }

    .pixel-info {
        top: 10px;
        left: 10px;
    }

    .selected-pixel-info {
        top: 10px;
        right: 10px;
        background: rgba(64, 158, 255, 0.9);
    }

    .placeholder {
        flex: 1;
        display: flex;
        align-items: center;
        justify-content: center;
        color: #909399;
        font-size: 16px;
        border: 2px dashed #ddd;
        border-radius: 5px;
    }

    .spectrum-section {
        height: 100%;
        display: flex;
        flex-direction: column;
    }

    .spectrum-container {
        flex: 1;
        display: flex;
        flex-direction: column;
        gap: 20px;
    }

    .spectrum-chart {
        flex: 1;
        border: 1px solid #e4e7ed;
        border-radius: 5px;
        padding: 15px;
        display: flex;
        flex-direction: column;
    }

    .chart-wrapper {
        flex: 1;
        display: flex;
        align-items: center;
        justify-content: center;
    }

    .chart-placeholder {
        flex: 1;
        display: flex;
        align-items: center;
        justify-content: center;
        color: #909399;
        font-size: 14px;
        border: 2px dashed #ddd;
        border-radius: 5px;
    }

    .detection-result-section {
        height: 100%;
        display: flex;
        flex-direction: column;
    }

    .result-container {
        flex: 1;
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 20px;
    }

    .detection-result-image {
        max-width: 100%;
        max-height: 70%;
        border: 2px solid #ddd;
        border-radius: 5px;
    }

    .metrics-info {
        background: #f5f7fa;
        padding: 15px;
        border-radius: 5px;
        width: 100%;
        text-align: center;
    }

        .metrics-info h3 {
            margin-top: 0;
            color: #409EFF;
        }

    h2 {
        margin-top: 0;
        color: #303133;
        text-align: center;
        border-bottom: 2px solid #e4e7ed;
        padding-bottom: 10px;
    }

    h3 {
        margin-top: 0;
        color: #606266;
        font-size: 16px;
    }

    /* 响应式设计 */
    @media (max-width: 1200px) {
        .wrap {
            flex-direction: column;
        }

        .left-panel {
            margin-right: 0;
            margin-bottom: 20px;
            height: 50vh;
        }

        .right-panel {
            height: 40vh;
        }
    }
</style>