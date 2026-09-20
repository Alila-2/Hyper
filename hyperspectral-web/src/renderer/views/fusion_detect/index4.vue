<template>
    <div class="fusion-page">
        <div class="fusion-container">
            <!-- 标题 -->
            <h2 class="page-title">融合探测试验</h2>

            <!-- 页内切换条：样例演示 / 数据批处理 -->
            <div class="page-switcher">
                <button class="switch-btn"
                        :class="{ active: mode === 'sample' }"
                        @click="switchMode('sample')">
                    样例演示
                </button>
                <button class="switch-btn"
                        :class="{ active: mode === 'batch' }"
                        @click="switchMode('batch')">
                    数据批处理
                </button>
            </div>

            <!-- ===================== 样例演示面板（你原来的内容） ===================== -->
            <section v-show="mode === 'sample'" class="panel-sample">
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
                                                    <span class="item-value">{{ fusionResultInfo.bands }}</span>
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

                                        <!-- 检测结果简版 -->
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
                                                    <span class="target-count">R:{{ target.sbl }}%</span>
                                                </div>
                                            </div>
                                        </div>

                                        <!-- 探测结果信息 -->
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
                                                <div class="result-item">
                                                    <span class="item-label">伪装目标:</span>
                                                    <span class="item-value highlight">{{ this.wzml }}个</span>
                                                </div>
                                                <div class="result-item">
                                                    <span class="item-label">军事目标:</span>
                                                    <span class="item-value highlight">{{ this.jsml }}个</span>
                                                </div>
                                                <div class="result-item">
                                                    <span class="item-label">伪装目标破译率:</span>
                                                    <span class="item-value highlight">{{ this.pyl }}</span>
                                                </div>
                                                <div class="result-item">
                                                    <span class="item-label">军事目标检测精度:</span>
                                                    <span class="item-value highlight">{{ this.jsjd }}</span>
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
            </section>
            <!-- ===================== /样例演示面板 ===================== -->
            <!-- ===================== 数据批处理面板（嵌入你的源码） ===================== -->
            <section v-show="mode === 'batch'" class="panel-batch">
                <div class="batch-process-root">
                    <div class="batch-process-container">
                        <!-- 页面标题 -->
                        <div class="page-header">
                            <h2 class="page-title">批量目标检测系统</h2>
                            <div class="page-subtitle">军事目标与伪装材料智能识别分析平台</div>
                        </div>

                        <!-- 控制面板 -->
                        <div class="control-panel">
                            <div class="button-group">
                                <button @click="startBatchProcess"
                                        :disabled="isProcessing"
                                        class="start-button">
                                    <span class="button-icon">🚀</span>
                                    {{ isProcessing ? '处理中...' : '开始批量处理' }}
                                </button>
                                <button @click="resetProcess"
                                        :disabled="isProcessing"
                                        class="reset-button">
                                    <span class="button-icon">🔄</span>
                                    重置
                                </button>
                            </div>

                            <!-- 处理状态 -->
                            <div class="status-info" v-if="isProcessing">
                                <div class="progress-header">
                                    <div class="progress-text">
                                        <span class="progress-label">处理进度</span>
                                        <span class="progress-numbers">{{ currentProgress }}/{{ totalFiles }} ({{ progressPercentage }}%)</span>
                                    </div>
                                    <div class="current-file-info">
                                        <span class="file-label">当前文件:</span>
                                        <span class="file-name">{{ currentFile }}</span>
                                    </div>
                                </div>
                                <div class="progress-bar-container">
                                    <div class="progress-fill" :style="{ width: progressPercentage + '%' }"></div>
                                </div>
                            </div>
                        </div>

                        <!-- 总体统计/明细 -->
                        <div class="overall-metrics" v-if="overallStatistics">
                            <div class="metrics-header">
                                <h3 class="metrics-title">总体检测指标</h3>
                                <div class="metrics-summary">共处理 {{ overallStatistics.total_files }} 个数据集</div>
                            </div>

                            <!-- 基本信息卡片 -->
                            <div class="basic-info-cards">
                                <div class="info-card file-count">
                                    <div class="info-icon">📊</div>
                                    <div class="info-content">
                                        <div class="info-value">{{ overallStatistics.total_files }}</div>
                                        <div class="info-label">处理文件总数</div>
                                    </div>
                                </div>
                                <div class="info-card avg-time">
                                    <div class="info-icon">⚡</div>
                                    <div class="info-content">
                                        <div class="info-value">{{ averageDetectionTime.toFixed(2) }}s</div>
                                        <div class="info-label">平均检测时间</div>
                                    </div>
                                </div>
                                <div class="info-card total-targets">
                                    <div class="info-icon">🎯</div>
                                    <div class="info-content">
                                        <div class="info-value">{{ overallStatistics.total_expected_targets }}</div>
                                        <div class="info-label">应检测目标总数</div>
                                    </div>
                                </div>
                                <div class="info-card correct-detections">
                                    <div class="info-icon">✅</div>
                                    <div class="info-content">
                                        <div class="info-value">{{ overallStatistics.total_correct_detections }}</div>
                                        <div class="info-label">正确检测总数</div>
                                    </div>
                                </div>
                            </div>

                            <!-- 总体数量统计 -->
                            <div class="total-stats-section">
                                <h4 class="section-title">总体数量统计</h4>
                                <div class="total-stats-grid">
                                    <div class="total-stat-card">
                                        <div class="total-stat-icon">📈</div>
                                        <div class="total-stat-value">{{ overallStatistics.total_detected_targets }}</div>
                                        <div class="total-stat-label">总检测目标数</div>
                                    </div>
                                    <div class="total-stat-card warning">
                                        <div class="total-stat-icon">⚠️</div>
                                        <div class="total-stat-value">{{ overallStatistics.total_false_positives }}</div>
                                        <div class="total-stat-label">总误报数量</div>
                                    </div>
                                    <div class="total-stat-card danger">
                                        <div class="total-stat-icon">❌</div>
                                        <div class="total-stat-value">{{ overallStatistics.total_false_negatives }}</div>
                                        <div class="total-stat-label">总漏报数量</div>
                                    </div>
                                </div>
                            </div>

                            <!-- 伪装材料统计 -->
                            <div class="category-section camouflage-section">
                                <div class="section-header">
                                    <div class="section-title-group">
                                        <h4 class="section-title">伪装材料统计</h4>
                                        <div class="targets-count-badge">
                                            {{ overallStatistics.camouflage_targets_count }} 种材料
                                        </div>
                                    </div>
                                    <div class="section-actions">
                                        <span class="material-count">共 {{ overallStatistics.camouflage_expected_total }} 个目标</span>
                                    </div>
                                </div>

                                <div class="category-content">
                                    <!-- 伪装材料列表 -->
                                    <div class="targets-list">
                                        <h5 class="targets-title">伪装材料列表</h5>
                                        <div class="targets-grid">
                                            <span v-for="target in overallStatistics.camouflage_targets_list"
                                                  :key="target"
                                                  class="target-tag camouflage-tag">{{ target }}</span>
                                        </div>
                                    </div>

                                    <!-- 伪装材料数量统计 -->
                                    <div class="category-stats-grid">
                                        <div class="category-stat">
                                            <div class="stat-icon">🎯</div>
                                            <div class="stat-content">
                                                <span class="stat-label">应有总数</span>
                                                <span class="stat-value">{{ overallStatistics.camouflage_expected_total }}</span>
                                            </div>
                                        </div>
                                        <div class="category-stat success">
                                            <div class="stat-icon">✅</div>
                                            <div class="stat-content">
                                                <span class="stat-label">正确检测</span>
                                                <span class="stat-value">{{ overallStatistics.camouflage_correct_total }}</span>
                                            </div>
                                        </div>
                                        <div class="category-stat warning">
                                            <div class="stat-icon">⚠️</div>
                                            <div class="stat-content">
                                                <span class="stat-label">误报总数</span>
                                                <span class="stat-value">{{ overallStatistics.camouflage_false_positives_total }}</span>
                                            </div>
                                        </div>
                                        <div class="category-stat danger">
                                            <div class="stat-icon">❌</div>
                                            <div class="stat-content">
                                                <span class="stat-label">漏报总数</span>
                                                <span class="stat-value">{{ overallStatistics.camouflage_false_negatives_total }}</span>
                                            </div>
                                        </div>
                                    </div>

                                    <!-- 伪装材料关键指标 -->
                                    <div class="key-metrics">
                                        <div class="key-metric primary">
                                            <div class="metric-icon">🔍</div>
                                            <div class="metric-content">
                                                <div class="metric-value">{{ (overallStatistics.camouflage_decipher_rate * 100).toFixed(1) }}%</div>
                                                <div class="metric-label">伪装材料破译率</div>
                                            </div>
                                        </div>
                                    </div>

                                    <!-- 伪装材料各目标详细统计 -->
                                    <div class="detailed-stats-subsection camouflage-detailed">
                                        <div class="subsection-header">
                                            <h4 class="subsection-title">伪装材料各目标详细统计</h4>
                                            <div class="table-summary">共 {{ camouflageTargetsCount }} 个伪装材料类型</div>
                                        </div>
                                        <div class="detailed-stats-table">
                                            <table>
                                                <thead>
                                                    <tr>
                                                        <th>目标名称</th>
                                                        <th>应有数量</th>
                                                        <th>正确检测</th>
                                                        <th>误报数量</th>
                                                        <th>漏报数量</th>
                                                        <th>检测数量</th>
                                                        <th>识别率</th>
                                                        <th>查准率</th>
                                                    </tr>
                                                </thead>
                                                <tbody>
                                                    <tr v-for="(stats, targetType) in camouflageDetailedStats"
                                                        :key="targetType"
                                                        class="camouflage-row">
                                                        <td class="target-name-cell">
                                                            <span class="target-color" :style="{ backgroundColor: getTargetColor(targetType) }"></span>
                                                            {{ stats.target_name }}
                                                        </td>
                                                        <td>{{ stats.expected_count }}</td>
                                                        <td class="success-cell">{{ stats.correct_detections }}</td>
                                                        <td class="warning-cell">{{ stats.false_positives }}</td>
                                                        <td class="danger-cell">{{ stats.false_negatives }}</td>
                                                        <td>{{ stats.detected_count }}</td>
                                                        <td class="rate-cell">{{ (stats.recognition_rate * 100).toFixed(1) }}%</td>
                                                        <td class="rate-cell">{{ (stats.precision * 100).toFixed(1) }}%</td>
                                                    </tr>
                                                </tbody>
                                            </table>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <!-- 军事目标统计 -->
                            <div class="category-section military-section">
                                <div class="section-header">
                                    <div class="section-title-group">
                                        <h4 class="section-title">军事目标统计</h4>
                                        <div class="targets-count-badge">
                                            {{ overallStatistics.military_targets_count }} 种目标
                                        </div>
                                    </div>
                                    <div class="section-actions">
                                        <span class="material-count">共 {{ overallStatistics.military_expected_total }} 个目标</span>
                                    </div>
                                </div>

                                <div class="category-content">
                                    <!-- 军事目标列表 -->
                                    <div class="targets-list">
                                        <h5 class="targets-title">军事目标列表</h5>
                                        <div class="targets-grid">
                                            <span v-for="target in overallStatistics.military_targets_list"
                                                  :key="target"
                                                  class="target-tag military-tag">{{ target }}</span>
                                        </div>
                                    </div>

                                    <!-- 军事目标数量统计 -->
                                    <div class="category-stats-grid">
                                        <div class="category-stat">
                                            <div class="stat-icon">🎯</div>
                                            <div class="stat-content">
                                                <span class="stat-label">应有总数</span>
                                                <span class="stat-value">{{ overallStatistics.military_expected_total }}</span>
                                            </div>
                                        </div>
                                        <div class="category-stat success">
                                            <div class="stat-icon">✅</div>
                                            <div class="stat-content">
                                                <span class="stat-label">正确检测</span>
                                                <span class="stat-value">{{ overallStatistics.military_correct_total }}</span>
                                            </div>
                                        </div>
                                        <div class="category-stat warning">
                                            <div class="stat-icon">⚠️</div>
                                            <div class="stat-content">
                                                <span class="stat-label">误报总数</span>
                                                <span class="stat-value">{{ overallStatistics.military_false_positives_total }}</span>
                                            </div>
                                        </div>
                                        <div class="category-stat danger">
                                            <div class="stat-icon">❌</div>
                                            <div class="stat-content">
                                                <span class="stat-label">漏报总数</span>
                                                <span class="stat-value">{{ overallStatistics.military_false_negatives_total }}</span>
                                            </div>
                                        </div>
                                    </div>

                                    <!-- 军事目标关键指标 -->
                                    <div class="key-metrics">
                                        <div class="key-metric secondary">
                                            <div class="metric-icon">🎯</div>
                                            <div class="metric-content">
                                                <div class="metric-value">{{ (overallStatistics.military_precision * 100).toFixed(1) }}%</div>
                                                <div class="metric-label">军事目标查准率</div>
                                            </div>
                                        </div>
                                        <div class="key-metric secondary">
                                            <div class="metric-icon">📊</div>
                                            <div class="metric-content">
                                                <div class="metric-value">{{ (overallStatistics.military_recall * 100).toFixed(1) }}%</div>
                                                <div class="metric-label">军事目标召回率</div>
                                            </div>
                                        </div>
                                    </div>

                                    <!-- 军事目标各目标详细统计 -->
                                    <div class="detailed-stats-subsection military-detailed">
                                        <div class="subsection-header">
                                            <h4 class="subsection-title">军事目标各目标详细统计</h4>
                                            <div class="table-summary">共 {{ militaryTargetsCount }} 个军事目标类型</div>
                                        </div>
                                        <div class="detailed-stats-table">
                                            <table>
                                                <thead>
                                                    <tr>
                                                        <th>目标名称</th>
                                                        <th>应有数量</th>
                                                        <th>正确检测</th>
                                                        <th>误报数量</th>
                                                        <th>漏报数量</th>
                                                        <th>检测数量</th>
                                                        <th>查准率</th>
                                                        <th>召回率</th>
                                                    </tr>
                                                </thead>
                                                <tbody>
                                                    <tr v-for="(stats, targetType) in militaryDetailedStats"
                                                        :key="targetType"
                                                        class="military-row">
                                                        <td class="target-name-cell">
                                                            <span class="target-color" :style="{ backgroundColor: getTargetColor(targetType) }"></span>
                                                            {{ stats.target_name }}
                                                        </td>
                                                        <td>{{ stats.expected_count }}</td>
                                                        <td class="success-cell">{{ stats.correct_detections }}</td>
                                                        <td class="warning-cell">{{ stats.false_positives }}</td>
                                                        <td class="danger-cell">{{ stats.false_negatives }}</td>
                                                        <td>{{ stats.detected_count }}</td>
                                                        <td class="rate-cell">{{ (stats.precision * 100).toFixed(1) }}%</td>
                                                        <td class="rate-cell">{{ (stats.recall * 100).toFixed(1) }}%</td>
                                                    </tr>
                                                </tbody>
                                            </table>
                                        </div>
                                    </div>

                                </div>
                            </div>
                        </div>

                        <!-- 错误信息 -->
                        <div v-if="errorMessage" class="error-message">
                            <div class="error-alert">
                                <span class="error-icon">⚠️</span>
                                <div class="error-content">
                                    <div class="error-title">处理错误</div>
                                    <div class="error-text">{{ errorMessage }}</div>
                                </div>
                            </div>
                        </div>

                    </div>
                </div>
            </section>
            <!-- ===================== /数据批处理面板 ===================== -->

        </div>
    </div>
</template>

<script>
    import axios from 'axios'

    export default {
        name: "ImageFusion",
        data() {
            return {
                // —— 样例演示原有状态 —— //
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
                jsml: null,
                wzml: null,
                pyl: null,
                jsjd: null,
                materials: [
                    { name: "假草皮", color: "#008000", targets: ["d1"] },
                    { name: "木板", color: "#DAA520", targets: ["d2"] },
                    { name: "竹材", color: "#808000", targets: ["d3"] },
                    { name: "棕色迷彩服", color: "#8B4513", targets: ["d4"] },
                    { name: "蓝色迷彩服", color: "#0000FF", targets: ["d5"] },
                    { name: "绿色伪装服", color: "#00FF00", targets: ["d6"] },
                    { name: "棕色伪装服", color: "#A0522D", targets: ["d7"] },
                    { name: "伪装网", color: "#708090", targets: ["d8"] },
                    { name: "大炮", color: "#FF6B6B", targets: ["d9"] },
                    { name: "坦克", color: "#4ECDC4", targets: ["d10"] },
                    { name: "装甲车", color: "#FFD166", targets: ["d11"] }
                ],

                // —— 页内切换状态 —— //
                mode: sessionStorage.getItem('fusion_idx3_mode') || 'sample',

                // —— 批处理状态（来自你提供的源码） —— //
                isProcessing: false,
                currentProgress: 0,
                totalFiles: 0,
                currentFile: '',
                completedResults: [],
                overallStatistics: null,
                currentPage: 1,
                pageSize: 1,
                errorMessage: '',
                pollInterval: null,
                TARGET_COLORS: {
                    "d1": "#008000", "d2": "#DAA520", "d3": "#808080", "d4": "#8B4513",
                    "d5": "#0000FF", "d6": "#ADD8E6", "d7": "#F0E68C", "d8": "#800080",
                    "d9": "#FF0000", "d10": "#00FFFF", "d11": "#FFFF00"
                }
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
                const gap = 25
                const height = (this.inputSectionHeight - gap) / 2
                return { height: `${height}px`, minHeight: '200px' }
            },
            // 新增：计算目标总数
            totalObjectsCount() {
                if (!this.targetStatistics) return 0
                return Object.values(this.targetStatistics).reduce((total, stats) => {
                    return total + (stats.object_count || 0)
                }, 0)
            },
            // 新增：合并检测目标和数量信息
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

            // —— 批处理计算属性 —— //
            progressPercentage() {
                if (this.totalFiles === 0) return 0
                return Math.round((this.currentProgress / this.totalFiles) * 100)
            },
            totalPages() {
                return this.completedResults.length
            },
            currentDataset() {
                if (this.completedResults.length === 0) return null
                return this.completedResults[this.currentPage - 1]
            },
            averageDetectionTime() {
                if (!this.overallStatistics || this.overallStatistics.total_files === 0) return 0
                return this.overallStatistics.total_detection_time / this.overallStatistics.total_files
            },
            camouflageDetailedStats() {
                if (!this.overallStatistics || !this.overallStatistics.target_detailed_stats) return {}
                const result = {}
                for (const [targetType, stats] of Object.entries(this.overallStatistics.target_detailed_stats)) {
                    if (stats.target_type === '伪装材料') result[targetType] = stats
                }
                return result
            },
            militaryDetailedStats() {
                if (!this.overallStatistics || !this.overallStatistics.target_detailed_stats) return {}
                const result = {}
                for (const [targetType, stats] of Object.entries(this.overallStatistics.target_detailed_stats)) {
                    if (stats.target_type === '军事目标') result[targetType] = stats
                }
                return result
            },
            camouflageTargetsCount() {
                return Object.keys(this.camouflageDetailedStats).length
            },
            militaryTargetsCount() {
                return Object.keys(this.militaryDetailedStats).length
            }
        },
        methods: {
            // —— 切换面板 —— //
            switchMode(next) {
                this.mode = next
                sessionStorage.setItem('fusion_idx3_mode', next)
            },

            // —— 样例演示原有方法 —— //
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
            async handleChange1(file) {
                if (!file) return;
                this.imageName1 = file.name;
                this.selectedFile1 = file.raw;
                this.matInfo1 = null;
                try {
                    this.loading = true;
                    const formData = new FormData();
                    formData.append("mat_file", this.selectedFile1);
                    const response = await fetch("http://localhost:8000/get_mat_preview", { method: "POST", body: formData });
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
                    this.$nextTick(() => { this.updateInputSectionHeight(); });
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
                    const response = await fetch("http://localhost:8000/get_image_info", { method: "POST", body: formData });
                    if (response.ok) {
                        const data = await response.json();
                        this.imageInfo2 = data.image_info;
                    }
                } catch (err) {
                    console.error("获取图片信息失败:", err);
                } finally {
                    this.$nextTick(() => { this.updateInputSectionHeight(); });
                }
            },
            updateInputSectionHeight() {
                if (this.$refs.modalOne && this.$refs.modalTwo) {
                    const modalOneHeight = this.$refs.modalOne.offsetHeight;
                    const modalTwoHeight = this.$refs.modalTwo.offsetHeight;
                    const gap = 25;
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
                this.targetStatistics = {}
                try {
                    this.$message.info("开始图像融合与检测处理...");
                    const formData = new FormData();
                    formData.append("file1", this.selectedFile1);
                    formData.append("file2", this.selectedFile2);
                    formData.append("targets", JSON.stringify(this.allTargets));
                    const response = await fetch("http://localhost:8000/start_fusion", { method: "POST", body: formData });
                    if (!response.ok) throw new Error("融合检测失败");
                    const data = await response.json();

                    this.imageUrl3 = data.image_url;
                    this.fusionResultInfo = data.fusion_info;

                    if (data.image) {
                        this.detectionUrl = `data:image/png;base64,${data.image}`;
                        this.detectionInfo = {
                            processingTime: data.runtime || "1.23",
                            materialCount: this.allMaterialNames.length,
                            targetCount: this.allTargets.length
                        };
                        this.targetStatistics = data.target_statistics || {}
                        this.detectedTargets = data.detected_targets || this.getMockDetectedTargets();
                        this.jsml = data.jsml || null
                        this.wzml = data.wzml || null
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
                return [
                    { name: "假草皮", type: "d1", color_hex: "#008000" },
                    { name: "军事目标", type: "d9", color_hex: "#FF0000" }
                ];
            },

            // —— 批处理：辅助 —— //
            getTargetColor(targetType) {
                return this.TARGET_COLORS[targetType] || '#cccccc'
            },

            // —— 批处理：流程 —— //
            async startBatchProcess() {
                this.errorMessage = ''
                this.isProcessing = true
                try {
                    const response = await axios.post('http://localhost:8000/api/batch-process')
                    if (response.data && response.data.success) {
                        this.startPolling()
                    } else {
                        this.errorMessage = (response.data && response.data.message) || '启动处理失败'
                        this.isProcessing = false
                    }
                } catch (error) {
                    this.handleError(error, '启动批量处理')
                    this.isProcessing = false
                }
            },
            startPolling() {
                if (this.pollInterval) clearInterval(this.pollInterval)
                this.pollInterval = setInterval(async () => {
                    try {
                        const response = await axios.get('http://localhost:8000/api/processing-status')
                        const status = response.data
                        this.updateStatus(status)
                        if (!status.is_processing && status.current_progress >= status.total_files) {
                            this.stopPolling()
                        }
                    } catch (error) {
                        console.error('轮询状态失败:', error)
                    }
                }, 2000)
            },
            stopPolling() {
                if (this.pollInterval) {
                    clearInterval(this.pollInterval)
                    this.pollInterval = null
                }
                this.isProcessing = false
            },
            updateStatus(status) {
                this.isProcessing = status.is_processing
                this.currentProgress = status.current_progress
                this.totalFiles = status.total_files
                this.currentFile = status.current_file
                this.completedResults = status.completed_results || []
                this.overallStatistics = status.overall_statistics
                if (this.completedResults.length > 0 && this.currentPage === this.totalPages) {
                    this.currentPage = this.completedResults.length
                }
            },
            resetProcess() {
                this.stopPolling()
                this.isProcessing = false
                this.currentProgress = 0
                this.totalFiles = 0
                this.currentFile = ''
                this.completedResults = []
                this.overallStatistics = null
                this.currentPage = 1
                this.errorMessage = ''
            },
            handleError(error, operation) {
                if (error && error.response) {
                    this.errorMessage =
                        (error.response && error.response.data && error.response.data.message)
                            ? error.response.data.message
                            : (operation + '请求失败');
                } else if (error && error.request) {
                    this.errorMessage = '无法连接到服务器，请检查后端服务是否启动';
                } else {
                    var msg = (error && error.message) ? error.message : '未知错误';
                    this.errorMessage = operation + '错误: ' + msg;
                }
            }

        },
        mounted() {
            // 初始计算高度（样例演示面板）
            this.$nextTick(() => { this.updateInputSectionHeight(); });
            window.addEventListener('resize', this.updateInputSectionHeight);
        },
        beforeDestroy() {
            window.removeEventListener('resize', this.updateInputSectionHeight);
            // 清理批处理轮询
            this.stopPolling()
        }
    }
</script>

<style scoped>
    /* ===== 你原有的样式（保持不变） ===== */
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
        margin-bottom: 16px;
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

    .modal-one, .modal-two {
        min-height: 400px;
        height: auto;
    }

    .fusion-result, .detection-result {
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

    /* ===== 页内切换样式 ===== */
    .page-switcher {
        display: flex;
        gap: 10px;
        margin: 8px 0 16px;
        justify-content: center;
    }

    .switch-btn {
        padding: 8px 14px;
        border: 1px solid #dcdfe6;
        background: #fff;
        border-radius: 6px;
        cursor: pointer;
        font-size: 14px;
    }

        .switch-btn.active {
            background: #409EFF;
            color: #fff;
            border-color: #409EFF;
        }

    /* ===== 批处理样式（取自你提供的文件，适当缩减不冲突） ===== */
    .batch-process-root {
        background: #ffffff;
        padding: 20px;
    }

    .batch-process-container {
        max-width: 1400px;
        margin: 0 auto;
        background: white;
        border-radius: 16px;
        box-shadow: 0 20px 40px rgba(0,0,0,0.1);
        overflow: hidden;
    }

    .page-header {
        background: linear-gradient(135deg, #2c3e50 0%, #3498db 100%);
        color: white;
        padding: 30px 40px;
        text-align: center;
    }

        .page-header .page-title {
            font-size: 2rem;
            -webkit-text-fill-color: #fff;
            background: none;
        }

    .page-subtitle {
        font-size: 1.1rem;
        opacity: 0.9;
        font-weight: 300;
    }

    .control-panel {
        padding: 30px 40px;
        background: #f8f9fa;
        border-bottom: 1px solid #e9ecef;
    }

    .button-group {
        display: flex;
        gap: 15px;
        margin-bottom: 20px;
    }

    .start-button, .reset-button {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 12px 24px;
        border: none;
        border-radius: 8px;
        font-size: 16px;
        font-weight: 600;
        cursor: pointer;
        transition: all 0.3s ease;
    }

    .start-button {
        background: linear-gradient(135deg, #00b09b 0%, #96c93d 100%);
        color: white;
    }

        .start-button:hover:not(:disabled) {
            transform: translateY(-2px);
            box-shadow: 0 8px 20px rgba(0, 176, 155, 0.3);
        }

        .start-button:disabled {
            opacity: 0.6;
            cursor: not-allowed;
        }

    .reset-button {
        background: #6c757d;
        color: white;
    }

        .reset-button:hover:not(:disabled) {
            background: #5a6268;
            transform: translateY(-1px);
        }

    .status-info {
        background: white;
        padding: 20px;
        border-radius: 12px;
        box-shadow: 0 4px 12px rgba(0,0,0,0.1);
    }

    .progress-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 15px;
    }

    .progress-text {
        display: flex;
        flex-direction: column;
        gap: 4px;
    }

    .progress-label {
        font-size: 14px;
        color: #666;
        font-weight: 500;
    }

    .progress-numbers {
        font-size: 18px;
        font-weight: 700;
        color: #2c3e50;
    }

    .current-file-info {
        display: flex;
        align-items: center;
        gap: 8px;
        background: #f8f9fa;
        padding: 8px 16px;
        border-radius: 6px;
    }

    .file-label {
        font-size: 14px;
        color: #666;
    }

    .file-name {
        font-weight: 600;
        color: #2c3e50;
    }

    .progress-bar-container {
        height: 8px;
        background: #e9ecef;
        border-radius: 4px;
        overflow: hidden;
    }

    .progress-fill {
        height: 100%;
        background: linear-gradient(90deg, #00b09b 0%, #96c93d 100%);
        border-radius: 4px;
        transition: width 0.3s ease;
    }

    .overall-metrics {
        padding: 40px;
    }

    .metrics-header {
        text-align: center;
        margin-bottom: 40px;
    }

    .metrics-title {
        font-size: 2rem;
        color: #2c3e50;
        margin: 0 0 10px 0;
        font-weight: 700;
    }

    .metrics-summary {
        font-size: 1.1rem;
        color: #666;
    }

    .basic-info-cards {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
        gap: 20px;
        margin-bottom: 40px;
    }

    .info-card {
        display: flex;
        align-items: center;
        background: white;
        padding: 25px;
        border-radius: 12px;
        box-shadow: 0 4px 12px rgba(0,0,0,0.1);
        border-left: 6px solid;
        transition: transform 0.3s ease, box-shadow 0.3s ease;
    }

        .info-card:hover {
            transform: translateY(-4px);
            box-shadow: 0 8px 24px rgba(0,0,0,0.15);
        }

        .info-card.file-count {
            border-left-color: #3498db;
        }

        .info-card.avg-time {
            border-left-color: #e74c3c;
        }

        .info-card.total-targets {
            border-left-color: #9b59b6;
        }

        .info-card.correct-detections {
            border-left-color: #27ae60;
        }

    .info-icon {
        font-size: 32px;
        margin-right: 20px;
    }

    .info-content {
        flex: 1;
    }

    .info-value {
        font-size: 2rem;
        font-weight: 800;
        color: #2c3e50;
        margin-bottom: 4px;
    }

    .info-label {
        font-size: 14px;
        color: #666;
        font-weight: 500;
    }

    .total-stats-section {
        margin-bottom: 40px;
    }

    .section-title {
        font-size: 1.5rem;
        color: #2c3e50;
        margin: 0 0 20px 0;
        font-weight: 600;
    }

    .total-stats-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
        gap: 20px;
    }

    .total-stat-card {
        background: white;
        padding: 25px;
        border-radius: 12px;
        text-align: center;
        box-shadow: 0 4px 12px rgba(0,0,0,0.1);
        border-top: 4px solid #3498db;
        transition: transform 0.3s ease;
    }

        .total-stat-card:hover {
            transform: translateY(-2px);
        }

        .total-stat-card.warning {
            border-top-color: #f39c12;
        }

        .total-stat-card.danger {
            border-top-color: #e74c3c;
        }

    .total-stat-icon {
        font-size: 2.5rem;
        margin-bottom: 10px;
    }

    .total-stat-value {
        font-size: 2.2rem;
        font-weight: 800;
        margin-bottom: 8px;
        color: #2c3e50;
    }

    .total-stat-label {
        font-size: 14px;
        color: #666;
        font-weight: 500;
    }

    .category-section {
        background: white;
        border-radius: 16px;
        padding: 30px;
        margin-bottom: 30px;
        box-shadow: 0 4px 12px rgba(0,0,0,0.1);
        border: 2px solid;
    }

    .camouflage-section {
        border-color: #27ae60;
    }

    .military-section {
        border-color: #3498db;
    }

    .section-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 25px;
        padding-bottom: 20px;
        border-bottom: 2px solid #f8f9fa;
    }

    .section-title-group {
        display: flex;
        align-items: center;
        gap: 15px;
    }

    .targets-count-badge {
        padding: 6px 16px;
        border-radius: 20px;
        font-size: 14px;
        font-weight: 600;
    }

    .camouflage-section .targets-count-badge {
        background: #d5f4e6;
        color: #27ae60;
    }

    .military-section .targets-count-badge {
        background: #d6eaf8;
        color: #3498db;
    }

    .section-actions {
        display: flex;
        align-items: center;
        gap: 15px;
    }

    .material-count {
        font-size: 14px;
        color: #666;
        font-weight: 500;
    }

    .targets-list {
        margin-bottom: 25px;
    }

    .targets-title {
        font-size: 1.1rem;
        color: #2c3e50;
        margin: 0 0 15px 0;
        font-weight: 600;
    }

    .targets-grid {
        display: flex;
        flex-wrap: wrap;
        gap: 10px;
    }

    .target-tag {
        padding: 8px 16px;
        border-radius: 20px;
        font-size: 14px;
        font-weight: 600;
        transition: transform 0.2s ease;
    }

        .target-tag:hover {
            transform: scale(1.05);
        }

    .camouflage-tag {
        background: linear-gradient(135deg, #d5f4e6 0%, #a3e4c1 100%);
        color: #27ae60;
        border: 1px solid #82e5b1;
    }

    .military-tag {
        background: linear-gradient(135deg, #d6eaf8 0%, #85c1e9 100%);
        color: #2980b9;
        border: 1px solid #5dade2;
    }

    .category-stats-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
        gap: 15px;
        margin-bottom: 25px;
    }

    .category-stat {
        display: flex;
        align-items: center;
        gap: 15px;
        padding: 20px;
        background: #f8f9fa;
        border-radius: 12px;
        border-left: 4px solid #3498db;
        transition: transform 0.2s ease;
    }

        .category-stat:hover {
            transform: translateX(4px);
        }

        .category-stat.success {
            border-left-color: #27ae60;
        }

        .category-stat.warning {
            border-left-color: #f39c12;
        }

        .category-stat.danger {
            border-left-color: #e74c3c;
        }

    .stat-icon {
        font-size: 1.5rem;
    }

    .stat-content {
        display: flex;
        flex-direction: column;
        gap: 4px;
    }

    .stat-label {
        color: #666;
        font-size: 13px;
        font-weight: 500;
    }

    .stat-value {
        font-weight: 700;
        color: #2c3e50;
        font-size: 1.2rem;
    }

    .key-metrics {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
        gap: 20px;
        margin-bottom: 30px;
    }

    .key-metric {
        background: white;
        padding: 25px;
        border-radius: 16px;
        display: flex;
        align-items: center;
        gap: 20px;
        box-shadow: 0 4px 12px rgba(0,0,0,0.1);
        border: 2px solid;
        transition: transform 0.3s ease;
    }

        .key-metric:hover {
            transform: translateY(-4px);
        }

        .key-metric.primary {
            border-color: #27ae60;
            background: linear-gradient(135deg, #f0f9f4 0%, #e1f7eb 100%);
        }

        .key-metric.secondary {
            border-color: #3498db;
            background: linear-gradient(135deg, #f0f8ff 0%, #e1f0ff 100%);
        }

    .metric-icon {
        font-size: 2.5rem;
    }

    .metric-content {
        flex: 1;
    }

    .metric-value {
        font-size: 2.2rem;
        font-weight: 800;
        margin-bottom: 8px;
    }

    .key-metric.primary .metric-value {
        color: #27ae60;
    }

    .key-metric.secondary .metric-value {
        color: #3498db;
    }

    .metric-label {
        font-size: 14px;
        color: #666;
        font-weight: 600;
    }

    .detailed-stats-subsection {
        background: #f8f9fa;
        border-radius: 12px;
        padding: 25px;
        margin-top: 20px;
        border: 1px solid #e9ecef;
    }

    .subsection-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 20px;
        padding-bottom: 15px;
        border-bottom: 1px solid #dee2e6;
    }

    .subsection-title {
        font-size: 1.2rem;
        color: #2c3e50;
        margin: 0;
        font-weight: 600;
    }

    .table-summary {
        font-size: 14px;
        color: #666;
        font-weight: 500;
    }

    .detailed-stats-table {
        overflow-x: auto;
        border-radius: 8px;
        border: 1px solid #e9ecef;
        background: white;
    }

        .detailed-stats-table table {
            width: 100%;
            border-collapse: collapse;
            font-size: 14px;
            min-width: 1000px;
        }

        .detailed-stats-table th, .detailed-stats-table td {
            padding: 16px;
            text-align: center;
            border-bottom: 1px solid #e9ecef;
        }

        .detailed-stats-table th {
            background: linear-gradient(135deg, #2c3e50 0%, #3498db 100%);
            color: white;
            font-weight: 600;
            position: sticky;
            top: 0;
            border: none;
        }

        .detailed-stats-table tr:hover {
            background: #f8f9fa;
        }

    .target-name-cell {
        display: flex;
        align-items: center;
        gap: 10px;
        text-align: left !important;
        font-weight: 600;
    }

    .target-color {
        width: 16px;
        height: 16px;
        border-radius: 4px;
        display: inline-block;
        border: 2px solid white;
        box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }

    .success-cell {
        color: #27ae60;
        font-weight: 700;
    }

    .warning-cell {
        color: #f39c12;
        font-weight: 700;
    }

    .danger-cell {
        color: #e74c3c;
        font-weight: 700;
    }

    .rate-cell {
        font-weight: 700;
        color: #2c3e50;
    }

    .military-row {
        background: #f8fdff;
    }

    .camouflage-row {
        background: #f8fff8;
    }

    .error-message {
        padding: 0 40px 40px;
    }

    .error-alert {
        display: flex;
        align-items: center;
        gap: 15px;
        background: linear-gradient(135deg, #ffeaa7 0%, #fab1a0 100%);
        padding: 20px;
        border-radius: 12px;
        border-left: 6px solid #e74c3c;
    }

    .error-icon {
        font-size: 2rem;
    }

    .error-content {
        flex: 1;
    }

    .error-title {
        font-weight: 700;
        color: #c0392b;
        margin-bottom: 4px;
    }

    .error-text {
        color: #7f8c8d;
        font-weight: 500;
    }

    /* 响应式缩略，省略若干重复项（保持你原样式一致性） */
    @media (max-width: 768px) {
        .batch-process-root {
            padding: 10px;
        }

        .batch-process-container {
            border-radius: 12px;
        }

        .page-header {
            padding: 20px;
        }

        .control-panel {
            padding: 20px;
        }

        .button-group {
            flex-direction: column;
        }

        .overall-metrics {
            padding: 20px;
        }

        .basic-info-cards {
            grid-template-columns: 1fr;
        }

        .total-stats-grid {
            grid-template-columns: 1fr;
        }

        .category-stats-grid {
            grid-template-columns: 1fr;
        }

        .key-metrics {
            grid-template-columns: 1fr;
        }

        .section-header {
            flex-direction: column;
            gap: 15px;
            align-items: flex-start;
        }

        .subsection-header {
            flex-direction: column;
            gap: 10px;
            align-items: flex-start;
        }

        .detailed-stats-table {
            font-size: 12px;
        }

        .category-section {
            padding: 20px;
        }

        .detailed-stats-subsection {
            padding: 15px;
        }

        .detailed-stats-table table {
            min-width: 800px;
        }
    }
</style>
