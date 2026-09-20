<template>
  <div id="app">
    <div class="wrap">
      <div class="box">
        <div class="block1">
          <h2>高光谱视频</h2>
          <el-upload
            class="upload-demo"
            :show-file-list="false"
            :before-upload="beforeUpload"
            :on-change="handleChange1"
          >
            <el-button size="small" type="primary">选择视频</el-button>
          </el-upload>

          <video v-if="videoUrl1" :src="videoUrl1" :class="['video-input', { active: isActive }]" controls autoplay></video>
          <el-card class="title-card" v-if="videoUrl1 && !isActive">
           <!-- 高光谱视频数据：分辨率475x216，光谱波段数6，帧数619，帧率30.0 FPS，波段范围400.0-700.0 nm，拍摄高度2000m -->
          高光谱视频数据： 分辨率<span class="font-bold">475x216</span>，
           <!-- 光谱波段数<span class="font-bold">6</span>，
           帧率<span class="font-bold">30</span>FPS， -->
           波段范围<span class="font-bold">400.0-700.0</span> nm，
           拍摄高度<span class="font-bold">2000 </span>m

          </el-card>
        </div>
        <div class="block2">
          <h2>高分辨率视频</h2>
          <el-upload
            class="upload-demo"
            :show-file-list="false"
            :before-upload="beforeUpload"
            :on-change="handleChange2"
          >
            <el-button size="small" type="primary">选择视频</el-button>
          </el-upload>
          <video v-if="videoUrl2" :src="videoUrl2" :class="['video-input', { active: isActive }]" controls autoplay></video>
          <el-card class="title-card" v-if="videoUrl2 && !isActive">
            <!-- 分辨率2378x1080，帧数619，帧率30.0 FPS，拍摄高度2000m -->
            分辨率<span class="font-bold">2378x1080</span>，
            帧数<span class="font-bold">619</span>，
            <!-- 帧率<span class="font-bold">30.0 </span>FPS， -->
            拍摄高度<span class="font-bold">2000 </span>m
          </el-card>
        </div>
      </div>
      <div :class="['block3', { active: isActive }]" v-if="isActive">
        <h2>融合结果</h2>
        <!-- <video v-if="videoUrl3" :src="videoUrl3" :class="['video-container','video-input', { active: isActive }]" controls autoplay ></video> -->
         <div class="video-container"> <!-- 新增一个容器包裹图片 -->
              <video v-if="videoUrl3" :src="videoUrl3" :class="['thisvideo',{ active: isActive }]" controls autoplay ></video> 
          </div>
        <el-card class="title-card">
            <!-- 分辨率2378x1080，光谱波段数3，帧数619，帧率30.0 FPS，拍摄高度2000m，融合精度：QNR高达0.9437。 -->
            高光谱视频数据： 分辨率<span class="font-bold">2378x1080</span>，
            <!-- 光谱波段数<span class="font-bold">3</span>， -->
            帧数<span class="font-bold">619</span>，
            <!-- 帧率<span class="font-bold">30.0 </span>FPS， -->
            拍摄高度<span class="font-bold">2000 </span>m

          </el-card>
        
      </div>
    </div>
    <div class="fusion-button">
      <el-button type="primary" @click="startFusion" v-if="videoUrl2 && videoUrl1">开始融合</el-button>
    </div>
  </div>
</template>

<script>
export default {
  data() {
    return {
      isVertical: false,
      isActive: false, // 控制是否激活的状态
      videoUrl1: '', // 存储视频 URL
      videoUrl2: '', // 存储视频 URL
      videoUrl3: '', // 存储融合结果视频 URL
      videoName1: "",
      videoName2: "",
    };
  },
  computed: {
    containerStyle() {
      return {
        display: 'flex',
        flexDirection: this.isVertical ? 'column' : 'row',
        maxHeight: this.isVertical ? '1000px' : '200px', // 动态变化最大高度
        transition: 'max-height 0.5s ease', // 动画过渡
      };
    },
  },
  methods: {
    toggleLayout() {
      this.isVertical = !this.isVertical;
      this.isActive = !this.isActive;
    },
    beforeUpload(file) {
      const isVideo = file.type.startsWith('video/');
      if (!isVideo) {
        // this.$message.error('只能上传视频文件');
      }
      return isVideo;
    },

    handleChange1(file) {
      this.videoName1 = file.name;
      if (this.videoName1 && this.videoName1.endsWith('.mat')) {
        // 将文件名的 .mat 后缀替换为 .mp4
        this.videoName1 = this.videoName1.replace('.mat', '.mp4');
      }
      this.fetchVideo(this.videoName1, 1);
    },

    handleChange2(file) {
      this.videoName2 = file.name;
      this.fetchVideo(this.videoName2, 2);
    },

    async startFusion() {
      this.isVertical = 1;
      this.isActive = 1;
      const formData = new FormData();
      formData.append("video1", this.videoName1);  // 添加 video1 文件到 FormData
      formData.append("video2", this.videoName2);  // 添加 video2 文件到 FormData

      try {
        const response = await fetch("http://localhost:8000/upload_fusion", {
          method: "POST",
          body: formData,
        });

        if (!response.ok) {
          throw new Error("上传失败！");
        }

        const data = await response.json();
        this.videoUrl3 = data.video_url;  // 获取返回的视频 URL
      } catch (error) {
        console.error("上传错误:", error);
        this.$message.error("上传失败！");
      }
    },



    async fetchVideo(videoName, n) {
      if (!videoName) {
        this.$message.warning('请输入视频名称');
        return;
      }

      try {
        // 向 FastAPI 后端请求获取视频 URL
        const response = await fetch(`http://localhost:8000/get_video/${videoName}`);

        if (!response.ok) {
          throw new Error('视频获取失败');
        }

        // 解析返回的 JSON，获取视频的 URL
        const data = await response.json();
        if (n === 1) {
          this.videoUrl1 = data.video_url; // 设置视频 URL
        } else if (n === 2) {
          this.videoUrl2 = data.video_url; // 设置视频 URL
        }
      } catch (error) {
        console.error(error);
        this.showErrorDialog = true; // 显示错误提示框
      }
    },
  },
};
</script>

<style scoped>
.wrap {
  display: flex;
  width: 100%;
  height: 900px;
  background-image: url('../../assets/bg.jpg');  
  background-size: cover;       /* 图片覆盖整个容器 */  
  background-position: center;  /* 图片居中显示 */  
  background-repeat: no-repeat; /* 不重复平铺 */  
  background-attachment: fixed; /* 背景固定，不随滚动移动 */  
  font-family: 'Helvetica Neue', 'Segoe UI', Arial, sans-serif;  
  min-height: 100vh; /* 确保背景至少覆盖整个视口高度 */
}
.box {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  padding: 20px;
}
.block1,
.block2 {
  flex: 1;
  flex-shrink: 0;
  min-width: 300px;
  margin: 20px;
  border: 1px;
  border-radius: 10px;
  border: 5px solid rgb(233, 233, 233); /* 设置边框 */
  padding: 10px;
}
.block1 {
  /* background-color: red; */
}
.block2 {
  /* background-color: blueviolet; */
}
.block3 {
  width: 0;
  height: 0;
  padding: 30px;
  overflow: hidden;
  transition: all ease-in-out 0.3s;
}

.block3.active {
  width: 80%;
  height: 100%;
}

.upload-image {
  display: flex;
  justify-content: center; /* 水平居中 */
  align-items: center; /* 垂直居中 */
  flex-direction: column; /* 使上传按钮和图片垂直排列 */
  text-align: center; /* 图片和按钮文字居中 */
}

.block2 {
  display: flex;
  flex-direction: column; /* 上传按钮和视频纵向排列 */
  justify-content: center; /* 垂直居中 */
  align-items: center; /* 水平居中 */
}
.block1 {
  display: flex;
  flex-direction: column; /* 上传按钮和视频纵向排列 */
  justify-content: center; /* 垂直居中 */
  align-items: center; /* 水平居中 */
}

.video-input {
  max-width: 100%;
  max-height: 500px;
  margin: 10px;
  border-radius: 5px;
  box-shadow: 0 0 10px rgba(0, 0, 0, 0.1);
}

.video-input.active {
  max-height: 300px;
}

.title-card {
  width: 100%;
  display: flex;
  justify-content: center; /* 水平居中 */
  align-items: center; /* 垂直居中 */
  font-size: 24px;
}
.fusion-button {
  display: flex;
  justify-content: center; /* 水平居中 */
  align-items: center; /* 垂直居中 */
  margin-left: 20px;
}
.video-container {
 
  padding: 80px;
    display: flex;
    justify-content: center; /* 水平居中 */
    align-items: center; /* 垂直居中 */
   
  /* transform: scale(1.5); 
  transform-origin: center; */
}
.thisvideo{
 max-width: 1000px;
}
.font-bold{
  font-weight: bold;
}
</style>
