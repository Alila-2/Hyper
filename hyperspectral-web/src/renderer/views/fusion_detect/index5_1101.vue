<template>
    <div class="fusion-page">
        <div class="fusion-container">

            <!-- 新增：顶部切换按钮，不改原样式 -->
            <div class="toggle-bar" style="display:flex;justify-content:center;margin-bottom:16px;">
                <el-button-group>
                    <el-button :type="currentTab==='sample' ? 'primary' : 'default'"
                               @click="currentTab='sample'">
                        样例演示
                    </el-button>
                    <el-button :type="currentTab==='batch' ? 'primary' : 'default'"
                               @click="currentTab='batch'">
                        数据批处理
                    </el-button>
                </el-button-group>
            </div>

            <!-- ====== 样例演示页（你的原页面，样式/结构不改） ====== -->
            <div v-if="currentTab==='sample'">
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
                                                <span class="item-label">图像尺寸:</span>
                                                <span class="item-value">{{ imageInfo2.resolution }}</span>
                                            </div>
                                            <div class="info-item">
                                                <span class="item-label">文件格式:</span>
                                                <span class="item-value">{{ imageInfo2.format }}</span>
                                            </div>
                                            <div class="info-item">
                                                <span class="item-label">文件大小:</span>
                                                <span class="item-value">{{ imageInfo2.file_size }}</span>
                                            </div>
                                            <div class="info-item" v-if="imageInfo2.mode">
                                                <span class="item-label">色彩模式:</span>
                                                <span class="item-value">{{ imageInfo2.mode }}</span>
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
                                            <img :src="imageUrl3"
                                                 class="result-image"
                                                 alt="融合结果" />
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
                                                    <span class="item-value">320</span>
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

                        <!-- 探测结果 - 与模态二对齐 -->
                        <div class="result-card detection-result" ref="detectionResult" :style="resultCardStyle">
                            <div class="card-header">
                                <h3 class="card-title">探测结果</h3>
                            </div>
                            <div class="card-content">
                                <div class="scrollable-content" v-if="detectionUrl">
                                    <div class="result-preview">
                                        <div class="image-wrapper">
                                            <img :src="detectionUrl"
                                                 class="result-image"
                                                 alt="探测结果" />
                                        </div>

                                        <div class="detected-targets-section">
                                            <h4 class="detected-targets-title">检测结果</h4>
                                            <div class="detected-targets-grid">
                                                <div v-for="target in detectedTargetsWithCount"
                                                     :key="target.type"
                                                     class="detected-target-item">
                                                    <div class="target-color-indicator"
                                                         :style="{ backgroundColor: target.color_hex }"></div>
                                                    <span class="target-name">{{ target.name }}</span>
                                                    <span class="target-count">{{ target.object_count }}个</span>
                                                    <!--span class="target-count">R:{{ target.sbl }}%</span-->
                                                </div>
                                            </div>
                                        </div>

                                        <!-- 探测结果信息 -->
                                        <div class="detection-result-panel" v-if="detectionInfo">
                                            <div class="result-header">
                                                <h3>检测结果统计</h3>
                                            </div>
                                            <div class="result-content">
                                                <!--div class="result-item">
                                                    <span class="item-label">处理时间:</span>
                                                    <span class="item-value">{{ detectionInfo.processingTime }}秒</span>
                                                </div>
                                                <div class="result-item">
                                                    <span class="item-label">检测材料:</span>
                                                    <span class="item-value">{{ detectionInfo.materialCount }}种</span>
                                                </div-->
                                                <div class="result-item">
                                                    <span class="item-label">发现目标:</span>
                                                    <span class="item-value highlight">{{ totalObjectsCount }}个</span>
                                                </div>
                                                <div class="result-item">
                                                    <span class="item-label">伪装目标:</span>
                                                    <span class="item-value highlight">{{ this.wzml }}个</span>
                                                </div>
                                                <div class="result-item">
                                                    <span class="item-label">军事目标:</span>
                                                    <span class="item-value highlight">{{ this.jsml }}个</span>
                                                </div>
                                                <!-- div class="result-item">
                                                    <span class="item-label">伪装目标破译率:</span>
                                                    <span class="item-value highlight">{{ this.pyl }}</span>
                                                </div>
                                                <div class="result-item">
                                                    <span class="item-label">军事目标检测精度:</span>
                                                    <span class="item-value highlight">{{ this.jsjd }}</span>
                                                </div-->
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

            <!-- ====== 数据批处理页（子组件） ====== -->
            <div v-else>
                <BatchProcessEmbed />
            </div>

        </div>
    </div>
</template>

<script>
    import BatchProcessEmbed from './BatchProcessEmbed.vue'

    export default {
        name: "ImageFusion",
        components: {
            BatchProcessEmbed
        },
        data() {
            return {
                // 新增：切换状态
                currentTab: 'sample',

                // —— 以下为你原有 data，不改 ——
                targetStatistics: {}, // 新增：目标统计信息
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
                detectedTargets: [],
                inputSectionHeight: 0,
                // jsml / wzml 改为 computed，不再放 data
                pyl: null,
                jsjd: null,
                materials: [
                    { name: "假草皮", color: "#008000", targets: ["d1"] },
                    { name: "木板", color: "#DAA520", targets: ["d2"] },
                    { name: "竹材", color: "#808000", targets: ["d3"] },
                    { name: "棕色迷彩服", color: "#8B4513", targets: ["d4"] },
                    { name: "绿色迷彩服", color: "#228B22", targets: ["d5"] }, /* 修正：原为“蓝色迷彩服” */
                    { name: "绿色伪装服", color: "#00FF00", targets: ["d6"] },
                    { name: "棕色伪装服", color: "#A0522D", targets: ["d7"] },
                    { name: "伪装网", color: "#708090", targets: ["d8"] },
                    { name: "火炮", color: "#FF6B6B", targets: ["d9"] },
                    { name: "坦克", color: "#4ECDC4", targets: ["d10"] },
                    { name: "装甲车", color: "#FFD166", targets: ["d11"] }, /* 去重：只保留一条 */
                    { name: "哨塔", color: "#FFAEC9", targets: ["d12"] },
                    { name: "碉堡", color: "#A0522D", targets: ["d13"] }
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
            },
            resultCardStyle() {
                // 每个结果卡片的高度为输入区域总高度的一半减去间隙
                const gap = 25; // 与CSS中的gap一致
                const height = (this.inputSectionHeight - gap) / 2;
                return {
                    height: `${height}px`,
                    minHeight: '200px' // 设置最小高度避免过小
                };
            },
            // 目标总数
            totalObjectsCount() {
                if (!this.targetStatistics) return 0
                return Object.values(this.targetStatistics).reduce((total, stats) => {
                    return total + (stats.object_count || 0)
                }, 0)
            },
            // 合并检测目标和数量信息
            detectedTargetsWithCount() {
                if (!this.detectedTargets || !this.targetStatistics) return []
                return this.detectedTargets.map(target => {
                    const stats = this.targetStatistics[target.type]
                    return {
                        ...target,
                        object_count: stats ? stats.object_count : 0,
                        sbl: stats ? stats.sbl : 0
                    }
                })
            },

            /* ===== 新增：分类清单 & 前端汇总计数 ===== */
            camouflageNames() {
                // 伪装目标分类（按你的要求）
                return ["假草皮", "木板", "竹材", "棕色迷彩服", "绿色迷彩服", "绿色伪装服", "棕色伪装服", "伪装网"]
            },
            militaryNames() {
                // 军事目标分类（按你的要求）
                return ["火炮", "坦克", "装甲车", "哨塔", "碉堡"]
            },
            // 伪装目标数量（前端计算）
            wzml() {
                if (!this.detectedTargets || !this.targetStatistics) return 0
                let sum = 0
                this.detectedTargets.forEach(t => {
                    const name = t.name || this.getMaterialNameByTarget(t.type)
                    if (this.camouflageNames.indexOf(name) !== -1) {
                        const stats = this.targetStatistics[t.type]
                        sum += (stats && stats.object_count) ? stats.object_count : 0
                    }
                })
                return sum
            },
            // 军事目标数量（前端计算）
            jsml() {
                if (!this.detectedTargets || !this.targetStatistics) return 0
                let sum = 0
                this.detectedTargets.forEach(t => {
                    const name = t.name || this.getMaterialNameByTarget(t.type)
                    if (this.militaryNames.indexOf(name) !== -1) {
                        const stats = this.targetStatistics[t.type]
                        sum += (stats && stats.object_count) ? stats.object_count : 0
                    }
                })
                return sum
            }
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
                const isImage = file.type && file.type.indexOf("image/") === 0;
                if (!isImage) {
                    this.$message.error('只能上传图片文件');
                    return false;
                }
                return isImage;
            },

            async handleChange1(file) {
                if (!file) return;

                this.imageName1 = file.name;
                this.selectedFile1 = file.raw;
                this.matInfo1 = null;

                try {
                    this.loading = true;
                    const formData = new FormData();
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
                    this.$nextTick(() => {
                        this.updateInputSectionHeight();
                    });
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
                } finally {
                    this.$nextTick(() => {
                        this.updateInputSectionHeight();
                    });
                }
            },

            updateInputSectionHeight() {
                // 计算左侧输入区域的总高度
                if (this.$refs.modalOne && this.$refs.modalTwo) {
                    const modalOneHeight = this.$refs.modalOne.offsetHeight;
                    const modalTwoHeight = this.$refs.modalTwo.offsetHeight;
                    const gap = 25; // 与CSS中的gap一致
                    this.inputSectionHeight = modalOneHeight + modalTwoHeight + gap;
                }
            },

            async startFusion() {
                if (!this.selectedFile1 || !this.selectedFile2) {
                    this.$message.warning("请先选择两个文件");
                    return;
                }

                this.loading = true;
                this.fusionResultInfo = null;
                this.detectionInfo = null;
                this.detectedTargets = [];
                this.targetStatistics = {} // 清空统计信息

                try {
                    this.$message.info("开始图像融合与检测处理...");

                    const formData = new FormData();
                    formData.append("file1", this.selectedFile1);
                    formData.append("file2", this.selectedFile2);
                    formData.append("targets", JSON.stringify(this.allTargets));

                    const response = await fetch("http://localhost:8000/start_fusion", {
                        method: "POST",
                        body: formData,
                    });

                    if (!response.ok) throw new Error("融合检测失败");

                    const data = await response.json();

                    // 显示融合结果
                    this.imageUrl3 = data.image_url;

                    // 设置融合结果信息
                    this.fusionResultInfo = data.fusion_info;

                    // 检测结果
                    if (data.image) {
                        this.detectionUrl = `data:image/png;base64,${data.image}`;

                        // 设置检测结果信息
                        this.detectionInfo = {
                            processingTime: data.runtime || "1.23",
                            materialCount: this.allMaterialNames.length,
                            targetCount: this.allTargets.length
                        };

                        // 接收统计信息与目标列表
                        this.targetStatistics = data.target_statistics || {}
                        this.detectedTargets = data.detected_targets || this.getMockDetectedTargets()

                        // 不再从后端取 jsml / wzml
                        this.pyl = data.pyl || null
                        this.jsjd = data.jsjd || null

                        this.$message.success("融合与检测成功！");
                    } else {
                        this.$message.warning("融合成功，但检测结果为空！");
                    }

                } catch (err) {
                    console.error(err);
                    this.$message.error("融合或检测失败，请检查后端服务！");
                } finally {
                    this.loading = false;
                }
            },

            getMockDetectedTargets() {
                // 模拟检测到的目标数据
                return [
                    { name: "假草皮", type: "d1", color_hex: "#008000" },
                    { name: "火炮", type: "d9", color_hex: "#FF6B6B" },
                    { name: "坦克", type: "d10", color_hex: "#4ECDC4" }
                ];
            },

            // 兼容：若后端的 detectedTargets 只有 type，则由 type 反查中文名
            getMaterialNameByTarget(targetCode) {
                const found = this.materials.find(m => (m.targets || []).includes(targetCode))
                return found ? found.name : targetCode
            }
        },
        mounted() {
            // 初始计算高度
            this.$nextTick(() => {
                this.updateInputSectionHeight();
            });

            // 监听窗口大小变化
            window.addEventListener('resize', this.updateInputSectionHeight);
        },
        beforeDestroy() {
            window.removeEventListener('resize', this.updateInputSectionHeight);
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

    /* 输入卡片保持原有高度设置 */
    .modal-one, .modal-two {
        min-height: 400px;
        height: auto;
    }

    /* 结果卡片高度由JavaScript动态控制 */
    .fusion-result, .detection-result {
        /* 高度由JavaScript动态设置 */
        min-height: 200px;
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
        box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
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

    /* 放大图片显示区域 */
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
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
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

    /* 融合结果面板样式 */
    .fusion-result-panel, .detection-result-panel {
        background: #ffffff;
        border-radius: 12px;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
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

    .status-value {
        color: #27ae60;
        font-weight: 700;
    }

    /* 检测到的目标展示样式 */
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
        padding: 6px 10px;
        background: white;
        border-radius: 6px;
        border: 1px solid #dee2e6;
        font-size: 12px;
    }

    .target-color-indicator {
        width: 14px;
        height: 14px;
        border-radius: 3px;
        margin-right: 6px;
        border: 1px solid #ccc;
    }

    .target-name {
        font-size: 12px;
        color: #2c3e50;
        font-weight: 500;
        margin-right: 4px;
    }

    .target-type {
        font-size: 11px;
        color: #6c757d;
    }

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
