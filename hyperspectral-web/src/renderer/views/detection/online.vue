<template>
  <div id="app">
     <!-- <el-button id="change" @click="toggleLayout">change</el-button> -->
      <div class="wrap">
        <div class="box">
          <div class="block1">
            <h2>模态一数据：高光谱图像视频流</h2> 
            <el-upload
              class="upload-demo"
              :show-file-list="false"
              :before-upload="beforeUpload"
              :on-change="handleChange1"
            >
              <el-button size="small" type="primary">选择图片</el-button>
            </el-upload>
            
            <!-- <img  src="@/assets/hsi.png" :class="['image-input', { active: isActive }]" alt="选择的图片" /> -->
            <img  :class="['image-input', { active: isActive }]"/>
            <el-card class="title-card"  v-if="imageUrl1 && !isActive">
             <!-- 分辨率128*128，光谱波段数176，波段范围300.0-2500.0 nm -->
             分辨率<span class="font-bold">128*128</span>，
            光谱波段数<span class="font-bold">176</span>，
            波段范围<span class="font-bold">300.0-2500.0</span>nm



            </el-card> 
          </div>
          <div class="block2">
            <h2>模态二数据：全色图像视频流</h2> 
            <el-upload
              class="upload-demo"
              :show-file-list="false"
              :before-upload="beforeUpload"
              :on-change="handleChange2"
            >
              <el-button size="small" type="primary">选择图片</el-button>
            </el-upload>
            <!-- <img src="@/assets/msi.png" :class="['image-input', { active: isActive }]" alt="选择的图片" /> -->
            <img  :class="['image-input', { active: isActive }]"/>
            <el-card class="title-card"  v-if="imageUrl2 && !isActive">
              <!-- 分辨率512*512，光谱波段数1 -->
               分辨率<span class="font-bold">512*512</span>，
            光谱波段数<span class="font-bold">1</span>
            <!-- 波段范围<span class="font-bold">300.0-2500.0</span>nm -->
            </el-card> 
          </div>
        </div>
         <div :class="['block3', { active: isActive }]" v-if="isActive">
            <h2>实时融合检测结果</h2> 
            <div class="image-container"> <!-- 新增一个容器包裹图片 -->
              <!-- <img  src="@/assets/hrhsi.png"  :class="['image-input3', { active: isActive }]" alt="选择的图片"  style="max-width: 800px;"/> -->
              <img   :class="['image-input3', { active: isActive }]" style="max-width: 800px;"/>
          </div>
            <!-- <img v-if="imageUrl3" :src="imageUrl3" :class="['image-input3', { active: isActive }]" alt="选择的图片" /> -->
            <el-card class="title-card"  v-if="imageUrl3">
              分辨率<span class="font-bold">512*512</span>，光谱波段数<span class="font-bold">176</span>
            </el-card>
         </div>
      </div>
      <div class="fusion-button">
                <el-button  type="primary" @click="startFusion">开始融合</el-button>
      </div>
  </div>
</template>

<script>
export default {
  data() {
    return {
      isVertical: true,
      isActive: true,  // 控制是否激活的状态
      imageUrl1: '',  // 存储本地图片 URL
      imageUrl2: '',  // 存储本地图片 URL
      imageName1: "",
      imageName2: "",
      imageUrl3: "",
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
      const isImage = file.type.startsWith('image/');
      if (!isImage) {
        // this.$message.error('只能上传图片文件');
      }
      return isImage;
    },

    handleChange1(file) {
      // console.log(file)
      this.imageName1 = file.name
      if (this.imageName1 && this.imageName1.endsWith('.mat')) {
        // 将文件名的 .mat 后缀替换为 .mp4
        this.imageName1 = this.imageName1.replace('.mat', '.png');
      }
      this.fetchImage(this.imageName1, 1)
      // this.imageUrl1 = URL.createObjectURL(file.raw); // 将本地图片文件转换为 URL
    },

    handleChange2(file) {
      this.imageName2 = file.name
      this.imageUrl2 = URL.createObjectURL(file.raw); // 将本地图片文件转换为 URL
    },

    async startFusion(){
      this.isVertical = 1;
       this.isActive = 1;
      this.width = 30
      this.span = 24
      
      const formData = new FormData();
      formData.append("image1", this.imageName1);  // 添加 video1 文件到 FormData
      formData.append("image2", this.imageName2);  // 添加 video2 文件到 FormData

      try {
        const response = await fetch("http://localhost:8000/upload_fusion_image", {
          method: "POST",
          body: formData,
        });

        if (!response.ok) {
          throw new Error("上传失败！");
        }
        const data = await response.json();
        this.imageUrl3 = data.image_url;  // 获取返回的视频 URL
      } catch (error) {
        console.error("上传错误:", error);
        this.$message.error("上传失败！");
      }



      // this.isVertical = "column"
    },

    async fetchImage(imageName, n) {
      if (!imageName) {
        this.$message.warning('请输入图片名称');
        return;
      }

      try {
        // 向 FastAPI 后端请求获取图片 URL
        const response = await fetch(`http://localhost:8000/get_image/${imageName}`);

        if (!response.ok) {
          throw new Error('图片获取失败');
        }

        // 解析返回的 JSON，获取图片的 URL
        const data = await response.json();
        if(n == 1){
          this.imageUrl1 = data.image_url;  // 设置图片 URL
        }else if(n == 2){
          this.imageUrl2 = data.image_url;  // 设置图片 URL
        }
        
      } catch (error) {
        console.error(error);
        this.showErrorDialog = true; // 显示错误提示框
      }
    }
    
    
  },
};
</script>

<style scoped>
.wrap {
      display: flex;
      width: 100%;
      height: 900px;
    }
    .box {
      flex: 1;
      display: flex;
      flex-wrap: wrap;
      padding: 20px;
    }
    .block1, .block2 {
      flex: 1;
      flex-shrink: 0;
      min-width: 280px;
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
      /* background-color: gold; */
      transition: all ease-in-out .3s;
      
    }
    .block3.active {
      width: 80%;
      height: 100%;
    }

.upload-image {
  display: flex;
  justify-content: center;  /* 水平居中 */
  align-items: center;      /* 垂直居中 */
  flex-direction: column;   /* 使上传按钮和图片垂直排列 */
  text-align: center;       /* 图片和按钮文字居中 */
}

.block2 {
  display: flex;
  flex-direction: column;   /* 上传按钮和图片纵向排列 */
  justify-content: center;  /* 垂直居中 */
  align-items: center;      /* 水平居中 */
}
.block1 {
  display: flex;
  flex-direction: column;   /* 上传按钮和图片纵向排列 */
  justify-content: center;  /* 垂直居中 */
  align-items: center;      /* 水平居中 */
}

.image-input {
  /* width: 100%; */
  /* max-width: 200px; */
  max-height: 500px;
  margin: 10px;
  border-radius: 5px;
  box-shadow: 0 0 10px rgba(0, 0, 0, 0.1);
}

.image-input.active  {
  max-height: 300px;
}
.title-card{
  width: 100%;
  display: flex;
  justify-content: center;  /* 水平居中 */
  align-items: center;      /* 垂直居中 */
  font-size: 24px;
}
.fusion-button {
  display: flex;
  justify-content: center;  /* 水平居中 */
  align-items: center;      /* 垂直居中 */
  margin-left: 20px;
  /* background-color: black; */
  /* padding: 50px; */

}

.image-container {
  padding: 80px;
    display: flex;
    justify-content: center; /* 水平居中 */
    align-items: center; /* 垂直居中 */
    width: 100%; /* 确保容器宽度足够 */
}
/* 图片和文字叠加的容器 */
/* .image-wrapper {
  position: relative;
  width: 100%;
  height: 100%;
} */

 .font-bold{
  font-weight: bold;
}
</style>
