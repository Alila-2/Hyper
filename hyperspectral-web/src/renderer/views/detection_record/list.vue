<template>
  <div class="detection-record-list">
    <el-card class="box-card">
      <div slot="header" class="header-title">
        <span>检测记录列表</span>
      </div>
      <div class="table-container">
        <el-table
          :data="paginatedRecords"
          border
          stripe
          style="width: 100%"
          :header-cell-style="headerCellStyle"
          :cell-style="cellStyle"
          @sort-change="handleSortChange"
        >
          <el-table-column prop="index" label="序号" width="60" />
          <el-table-column prop="hsResolution" label="高光谱分辨率" min-width="120" />
          <el-table-column prop="hsBands" label="光谱波段数" width="110" />
          <el-table-column prop="hsFrames" label="高光谱帧数" width="110" />
          <el-table-column prop="hsFPS" label="帧率" width="80" />
          <el-table-column prop="hsRange" label="波段范围 (nm)" min-width="140" />
          <el-table-column prop="hsAltitude" label="拍摄高度 (m)" width="120" />

          <el-table-column prop="rgbResolution" label="融合视频分辨率" min-width="140" />
          <el-table-column prop="rgbBands" label="光谱波段数" width="110" />
          <el-table-column prop="rgbFrames" label="帧数" width="80" />
          <el-table-column prop="rgbFPS" label="帧率" width="80" />
          <el-table-column prop="rgbAltitude" label="拍摄高度 (m)" width="120" />

          <el-table-column prop="fusionQuality" label="融合精度" width="120" />
          <el-table-column prop="detectionAcc" label="探测精度" width="120" />
          <el-table-column prop="operationTime" label="操作时间" min-width="160" />
        </el-table>
      </div>
      <div class="pagination-wrapper">
        <el-pagination
          @current-change="handleCurrentChange"
          @size-change="handleSizeChange"
          :current-page="currentPage"
          :page-size="pageSize"
          :page-sizes="[5, 7, 10, 15, 20, 50]"
          layout="sizes, prev, pager, next, jumper"
          :total="records.length"
        />
      </div>
    </el-card>
  </div>
</template>

<script>
export default {
  name: 'DetectionRecordList',
  data() {
    // 生成20条示例记录
    const list = [];
    for (let i = 1; i <= 20; i++) {
      list.push({
        index: i,
        hsResolution: '475x216',
        hsBands: 6,
        hsFrames: 619,
        hsFPS: 30.0,
        hsRange: '400-700',
        hsAltitude: 2000,
        rgbResolution: '2378x1080',
        rgbBands: 3,
        rgbFrames: 619,
        rgbFPS: 30.0,
        rgbAltitude: 2000,
        fusionQuality: 'QNR=' + (0.94 + Math.random() * 0.01).toFixed(4),
        detectionAcc: (90 + Math.floor(Math.random() * 5)) + '%',
        operationTime: new Date(Date.now() - Math.random() * 1e10).toLocaleString()
      });
    }
    return {
      records: list,
      currentPage: 1,
      pageSize: 5,
      headerCellStyle: {
        background: '#f5f7fa',
        color: '#606266',
        fontSize: '14px',
        fontFamily: '"Helvetica Neue", Arial, sans-serif'
      },
      cellStyle: {
        padding: '12px 8px',
        fontSize: '13px',
        fontFamily: '"Helvetica Neue", Arial, sans-serif'
      }
    };
  },
  computed: {
    paginatedRecords() {
      const start = (this.currentPage - 1) * this.pageSize;
      return this.records.slice(start, start + this.pageSize);
    }
  },
  methods: {
    handleSortChange({ prop, order }) {
      if (!order) return;
      this.records.sort((a, b) => {
        const valA = parseFloat(a[prop]) || a[prop];
        const valB = parseFloat(b[prop]) || b[prop];
        if (!isNaN(valA) && !isNaN(valB)) {
          return order === 'ascending' ? valA - valB : valB - valA;
        }
        return order === 'ascending'
          ? String(valA).localeCompare(valB)
          : String(valB).localeCompare(valA);
      });
    },
    handleCurrentChange(page) {
      this.currentPage = page;
    },
    handleSizeChange(size) {
      this.pageSize = size;
      this.currentPage = 1;
    }
  }
};
</script>

<style scoped>
.detection-record-list {
  padding: 16px;
  background-image: url('../../assets/bg.jpg');  
  background-size: cover;       /* 图片覆盖整个容器 */  
  background-position: center;  /* 图片居中显示 */  
  background-repeat: no-repeat; /* 不重复平铺 */  
  background-attachment: fixed; /* 背景固定，不随滚动移动 */  
  font-family: 'Helvetica Neue', 'Segoe UI', Arial, sans-serif;  
  min-height: 100vh; /* 确保背景至少覆盖整个视口高度 */
}
.header-title span {
  font-size: 18px;
  font-weight: bold;
  font-family: "Helvetica Neue", Arial, sans-serif;
}
.table-container {
  overflow-x: auto;
}
.el-table th, .el-table td {
  white-space: nowrap;
}
.box-card {
  width: 100%;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  margin-top: 12px;
}
</style>
