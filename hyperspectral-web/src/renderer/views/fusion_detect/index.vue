<template>
    <div class="fusion-page">
        <div class="fusion-container">
            <h2 class="page-title">自动融合检测</h2>

            <div class="fusion-content">
                <!-- 检测结果展示 -->
                <div class="result-card auto-detection-result">
                    <div class="card-header">
                        <h3 class="card-title">检测结果展示</h3>
                    </div>
                    <div class="card-content">
                        <div class="result-preview" v-if="detectionImageUrl">
                            <!-- 双图对比布局 -->
                            <div class="images-comparison">
                                <!-- 左：原始伪彩图 -->
                                <div class="image-column">
                                    <div class="image-wrapper">
                                        <img :src="originalImageUrl" class="result-image" alt="原始伪彩图" />
                                        <div class="image-label">原始伪彩图</div>
                                    </div>
                                </div>

                                <!-- 右：检测结果图 -->
                                <div class="image-column">
                                    <div class="image-wrapper">
                                        <img :src="detectionImageUrl" class="result-image" alt="检测结果" />
                                        <div class="image-label">检测结果图</div>
                                    </div>
                                </div>
                            </div>

                            <!-- 检测到的目标展示（名称 + 数量） -->
                            <div v-if="detectedTargets && detectedTargets.length"
                                 class="detected-targets-section">
                                <h4 class="detected-targets-title">检测结果</h4>
                                <div class="detected-targets-grid">
                                    <div v-for="target in detectedTargetsWithCount"
                                         :key="target.type"
                                         class="detected-target-item">
                                        <div class="target-color-indicator"
                                             :style="{ backgroundColor: target.color_hex }"></div>
                                        <span class="target-name">{{ target.name }}</span>
                                        <span class="target-count">{{ target.object_count }}个</span>
                                        <!-- 如需显示RR：取消下一行注释 -->
                                        <!-- <span class="target-rr">RR: {{ target.sbl }}%</span> -->
                                    </div>
                                </div>
                            </div>

                            <!-- 统计面板 -->
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
                                        <span class="item-value highlight">{{ wzml }}个</span>
                                    </div>
                                    <div class="result-item">
                                        <span class="item-label">军事目标:</span>
                                        <span class="item-value highlight">{{ jsml }}个</span>
                                    </div>
                                    <!-- 若需显示破译率/精度，取消以下注释并保证后端返回对应字段 -->
                                    <!--
                                    <div class="result-item">
                                      <span class="item-label">伪装目标破译率:</span>
                                      <span class="item-value highlight">{{ pyl }}</span>
                                    </div>
                                    <div class="result-item">
                                      <span class="item-label">军事目标检测精度:</span>
                                      <span class="item-value highlight">{{ jsjd }}</span>
                                    </div>
                                    -->
                                    <div class="result-item full-width">
                                        <span class="item-label">检测状态:</span>
                                        <span class="item-value status-value">完成</span>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div class="empty-state" v-else>
                            <div class="empty-icon" v-if="isDetecting">🛰️</div>
                            <div class="empty-icon" v-else>🔍</div>
                            <h4>{{ isDetecting ? '正在执行自动拍摄与检测...' : '等待检测结果' }}</h4>
                            <p>{{ isDetecting ? '请稍候，系统正在自动采集数据并进行融合检测' : '点击下方按钮开始自动检测' }}</p>
                        </div>

                        <!-- 调试信息 -->
                        <div v-if="debugInfo" class="debug-section">
                            <pre>{{ debugInfo }}</pre>
                        </div>

                        <!-- 错误信息显示 -->
                        <div v-if="errorMessage" class="error-message">
                            <div class="error-alert">{{ errorMessage }}</div>
                        </div>
                    </div>

                    <!-- 操作按钮 -->
                    <div class="action-section">
                        <el-button type="primary"
                                   @click="runAutoFusionDetect"
                                   class="fusion-button"
                                   :disabled="isDetecting"
                                   :loading="isDetecting">
                            <i class="el-icon-video-camera"></i>
                            {{ isDetecting ? '检测中...' : '执行自动融合检测' }}
                        </el-button>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script>
import axios from 'axios'

export default {
  name: 'AutoFusionDetection',
  data() {
    return {
      detectionImageUrl: null,
      originalImageUrl: null,
      isDetecting: false,
      debugInfo: '',
      errorMessage: '',
      detectionInfo: null, // { processingTime, materialCount, targetCount }
      detectedTargets: [], // [{ type, name, color_hex, ... }]
      targetStatistics: {}, // { [type]: { object_count, ... } }
      pyl: null, // 伪装目标破译率（可选）
      jsjd: null, // 军事目标检测精度（可选）
      processStartTime: 0,
      materials: [
        { name: '假草皮', color: '#008000', targets: ['d1'] },
        // { name: '木板', color: '#DAA520', targets: ['d2'] },
        // { name: '竹材', color: '#808080', targets: ['d3'] },
        { name: '棕色迷彩服', color: '#8B4513', targets: ['d4'] },
        /* 其余材料若启用，前端分类统计会自动生效
        { name: '绿色迷彩服', color: '#228B22', targets: ['d5'] },
        { name: '绿色伪装服', color: '#00FF00', targets: ['d6'] },
        { name: '棕色伪装服', color: '#A0522D', targets: ['d7'] },
        { name: '伪装网', color: '#800080', targets: ['d8'] },
        { name: '火炮', color: '#FF6B6B', targets: ['d9'] },
        { name: '坦克', color: '#4ECDC4', targets: ['d10'] },
        { name: '装甲车', color: '#FFD166', targets: ['d11'] },
        { name: '哨塔', color: '#FFAEC9', targets: ['d12'] },
        { name: '碉堡', color: '#A0522D', targets: ['d13'] }
        */
      ]
    }
  },
  computed: {
    allMaterialNames() {
      return this.materials.map(m => m.name)
    },
    allTargets() {
      const set = new Set()
      this.materials.forEach(m => {
        if (m.targets && Array.isArray(m.targets)) {
          m.targets.forEach(t => set.add(t))
        }
      })
      return Array.from(set)
    },
    // 总目标数 = 各类别 object_count 之和
    totalObjectsCount() {
      if (!this.targetStatistics) return 0
      const values = Object.values(this.targetStatistics)
      return values.reduce((sum, stats) => {
        return sum + ((stats && typeof stats.object_count !== 'undefined' && stats.object_count !== null) ? stats.object_count : 0)
      }, 0)
    },
    // 将 detectedTargets 与数量统计合并，供 UI 展示
    detectedTargetsWithCount() {
      if (!this.detectedTargets || !this.targetStatistics) return []
      return this.detectedTargets.map(t => {
        const stats = this.targetStatistics[t.type]
        return Object.assign({}, t, {
          object_count: stats ? stats.object_count : 0,
          sbl: stats ? stats.sbl : 0
        })
      })
    },

    /* ===== 分类清单 & 前端汇总计数 ===== */
    camouflageNames() {
      // 伪装目标
      return ['假草皮', '木板', '竹材', '棕色迷彩服', '绿色迷彩服', '绿色伪装服', '棕色伪装服', '伪装网']
    },
    militaryNames() {
      // 军事目标
      return ['火炮', '坦克', '装甲车', '哨塔', '碉堡']
    },
    // 伪装目标数量（前端计算：按名字归类，累加 object_count）
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
    // 军事目标数量（前端计算：按名字归类，累加 object_count）
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
    async runAutoFusionDetect() {
      if (this.isDetecting) return

      this.isDetecting = true
      this.errorMessage = ''
      this.debugInfo = ''
      this.detectionImageUrl = null
      this.originalImageUrl = null
      this.detectedTargets = []
      this.targetStatistics = {}
      this.detectionInfo = null
      this.pyl = null
      this.jsjd = null

      this.processStartTime = Date.now()
      this.debugInfo += '🚀 开始自动融合检测流程...\n'

      try {
        const formData = new FormData()
        formData.append('targets', JSON.stringify(this.allTargets))

        const response = await axios.post(
          'http://localhost:8000/fusion/auto_detect',
          formData,
          {
            headers: { 'Content-Type': 'multipart/form-data' },
            timeout: 180000
          }
        )

        this.debugInfo += '✅ 响应状态: ' + response.status + '\n'

        if (response.data && response.data.success) {
          // 图片
          if (response.data.image) {
            this.detectionImageUrl = 'data:image/png;base64,' + response.data.image
          }
          if (response.data.png_file_path) {
            this.originalImageUrl = 'data:image/png;base64,' + response.data.png_file_path
          } else if (response.data.original_image) {
            this.originalImageUrl = 'data:image/png;base64,' + response.data.original_image
          }

          // === 关键：接收并赋值 target_statistics ===
          this.detectedTargets = response.data.detected_targets || []
          this.targetStatistics = response.data.target_statistics || {}

          // 可选指标
          this.pyl = (typeof response.data.pyl !== 'undefined') ? response.data.pyl : null
          this.jsjd = (typeof response.data.jsjd !== 'undefined') ? response.data.jsjd : null

          // 统计信息
          this.detectionInfo = {
            processingTime:
              (typeof response.data.runtime !== 'undefined' && response.data.runtime !== null)
                ? response.data.runtime
                : ((Date.now() - this.processStartTime) / 1000).toFixed(2),
            materialCount: this.allMaterialNames.length,
            targetCount: this.allTargets.length
          }

          this.debugInfo +=
            '检测完成：发现 ' +
            this.detectedTargets.length +
            ' 种材料，总计 ' +
            this.totalObjectsCount +
            ' 个目标。\n'
          if (this.$message && typeof this.$message.success === 'function') {
            this.$message.success('自动融合检测完成！')
          }
        } else {
          this.errorMessage =
            (response.data && response.data.message) ? response.data.message : '检测失败'
          if (this.$message && typeof this.$message.error === 'function') {
            this.$message.error(this.errorMessage)
          }
        }
      } catch (error) {
        console.error('检测出错:', error)
        this.debugInfo += '❌ 错误: ' + (error && error.message ? error.message : String(error)) + '\n'
        this.handleError(error)
        if (this.$message && typeof this.$message.error === 'function') {
          this.$message.error('自动融合检测失败！')
        }
      } finally {
        this.isDetecting = false
      }
    },

    // 若后端仅返回 type，这里由 type → 中文名
    getMaterialNameByTarget(targetCode) {
      let found = null
      this.materials.some(m => {
        if ((m.targets || []).indexOf(targetCode) !== -1) {
          found = m
          return true
        }
        return false
      })
      return found ? found.name : targetCode
    },

    handleError(error) {
      if (error && error.response) {
        if (error.response.data && error.response.data.message) {
          this.errorMessage = error.response.data.message
        } else {
          this.errorMessage = '请求失败，状态码: ' + error.response.status
        }
      } else if (error && error.request) {
        this.errorMessage = '无法连接到服务器，请检查后端服务是否启动'
      } else {
        this.errorMessage = '请求发生错误：' + (error ? error.message : '未知错误')
      }
    }
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
        max-width: 1200px;
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
        grid-template-columns: 1fr;
        gap: 30px;
        margin-bottom: 30px;
        align-items: start;
    }

    .result-card {
        background: #fafafa;
        border: 2px dashed #e2e8f0;
        border-radius: 12px;
        padding: 25px;
        transition: all 0.3s ease;
        display: flex;
        flex-direction: column;
        box-sizing: border-box;
        min-height: 600px;
    }

    .auto-detection-result {
        height: auto;
        min-height: 600px;
    }

    .result-card:hover {
        border-color: #667eea;
        box-shadow: 0 4px 12px rgba(102, 126, 234, 0.1);
    }

    .card-header {
        margin-bottom: 20px;
        text-align: center;
    }

    .card-title {
        font-size: 20px;
        color: #2d3748;
        font-weight: 600;
        margin: 0;
    }

    .card-content {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 20px;
        flex: 1;
        overflow: hidden;
    }

    .result-preview {
        width: 100%;
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 20px;
        flex: 1;
        overflow-y: auto;
        max-height: 100%;
    }

    /* 双图对比布局 */
    .images-comparison {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 20px;
        width: 100%;
        max-width: 900px;
        margin: 0 auto;
    }

    .image-column {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 10px;
    }

    .image-wrapper {
        position: relative;
        display: flex;
        flex-direction: column;
        align-items: center;
        width: 100%;
        max-height: 350px;
        min-height: 250px;
    }

    .result-image {
        max-width: 100%;
        max-height: 300px;
        border-radius: 8px;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
        border: 2px solid #e9ecef;
        transition: transform 0.2s ease;
        object-fit: contain;
    }

        .result-image:hover {
            transform: scale(1.02);
        }

    .image-label {
        font-size: 14px;
        font-weight: 600;
        color: #4a5568;
        text-align: center;
        margin-top: 8px;
        padding: 4px 12px;
        background: rgba(255, 255, 255, 0.9);
        border-radius: 6px;
        border: 1px solid #e2e8f0;
    }

    .empty-state {
        text-align: center;
        padding: 60px 20px;
        color: #6c757d;
        flex: 1;
        display: flex;
        flex-direction: column;
        justify-content: center;
        align-items: center;
        height: 100%;
    }

    .empty-icon {
        font-size: 4em;
        margin-bottom: 20px;
        opacity: 0.7;
    }

    .empty-state h4 {
        margin: 0 0 15px 0;
        color: #495057;
        font-size: 1.3em;
    }

    .empty-state p {
        margin: 0;
        font-size: 1em;
        color: #6c757d;
    }

    /* 统计面板 */
    .detection-result-panel {
        background: #ffffff;
        border-radius: 12px;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
        padding: 20px;
        width: 100%;
        font-family: 'Arial', sans-serif;
        border: 1px solid #e8e8e8;
        margin-top: 10px;
        max-height: 200px;
        overflow-y: auto;
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

        .item-value.highlight {
            color: #e74c3c;
            font-weight: 700;
        }

    .status-value {
        color: #27ae60;
        font-weight: 700;
    }

    /* 目标列表 */
    .detected-targets-section {
        width: 100%;
        margin: 10px 0;
        padding: 15px;
        background: #f8f9fa;
        border-radius: 8px;
        border: 1px solid #e9ecef;
        max-height: 150px;
        overflow-y: auto;
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
        grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
        gap: 10px;
    }

    .detected-target-item {
        display: flex;
        align-items: center;
        justify-content: space-between;
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
        flex-shrink: 0;
    }

    .target-name {
        font-size: 14px;
        color: #2c3e50;
        font-weight: 600;
        flex-grow: 1;
    }

    .target-count {
        font-size: 13px;
        color: #e74c3c;
        font-weight: 700;
        background: #ffeaea;
        padding: 4px 8px;
        border-radius: 12px;
        min-width: 44px;
        text-align: center;
    }
    /* 可选：RR展示 */
    .target-rr {
        font-size: 12px;
        color: #6c757d;
        margin-left: 6px;
    }

    .debug-section {
        width: 100%;
        margin: 15px 0;
    }

        .debug-section pre {
            text-align: left;
            background: #f5f5f5;
            padding: 12px;
            border-radius: 6px;
            font-size: 12px;
            max-height: 120px;
            overflow-y: auto;
            white-space: pre-wrap;
            word-break: break-all;
            border: 1px solid #e9ecef;
        }

    .error-message {
        width: 100%;
        margin: 15px 0;
    }

    .error-alert {
        background: #fed7d7;
        color: #c53030;
        padding: 12px 16px;
        border-radius: 8px;
        border: 1px solid #feb2b2;
        font-size: 14px;
        text-align: center;
    }

    .action-section {
        display: flex;
        justify-content: center;
        margin-top: 20px;
        width: 100%;
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
        min-width: 200px;
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

    /* 响应式 */
    @media (max-width: 1024px) {
        .fusion-container {
            padding: 20px;
        }

        .result-card {
            min-height: 500px;
        }

        .images-comparison {
            grid-template-columns: 1fr;
            gap: 30px;
        }

        .image-wrapper {
            max-height: 300px;
        }

        .result-image {
            max-height: 280px;
        }

        .detected-targets-grid {
            grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
        }
    }

    @media (max-width: 768px) {
        .fusion-page {
            padding: 10px;
        }

        .page-title {
            font-size: 24px;
        }

        .result-card {
            padding: 20px;
            min-height: 450px;
        }

        .image-wrapper {
            max-height: 250px;
        }

        .result-image {
            max-height: 230px;
        }

        .detection-result-panel {
            padding: 15px;
            max-height: 180px;
        }

        .detected-targets-section {
            max-height: 130px;
        }

        .empty-state {
            padding: 40px 20px;
        }

        .empty-icon {
            font-size: 3em;
        }
    }

    @media (max-width: 480px) {
        .card-title {
            font-size: 18px;
        }

        .detected-targets-grid {
            grid-template-columns: 1fr;
        }

        .item-label,
        .item-value {
            font-size: 12px;
        }

        .fusion-button {
            padding: 12px 30px;
            font-size: 14px;
            min-width: 180px;
        }

        .empty-state h4 {
            font-size: 1.1em;
        }

        .empty-state p {
            font-size: 0.9em;
        }
    }
</style>
