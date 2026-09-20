<template>
<div id="app" :style="backgroundStyle">
<router-view></router-view>
</div>
</template>

<script>
import bg1 from '@/assets/bg1.png'

export default {
name: 'App',
data() {
return {
backgroundType: 'none', // 可选值: 'image', 'color', 'none'
backgroundColor: 'rgba(255, 255, 255, 0.9)',
backgroundImage: bg1,
opacity: 0.9
};
},
computed: {
backgroundStyle() {
const style = {
height: '100vh'
};

switch(this.backgroundType) {
case 'image':
style.backgroundImage = `url(${this.backgroundImage})`;
style.backgroundSize = 'cover';
style.backgroundPosition = 'center';
style.backgroundRepeat = 'no-repeat';
style.backgroundColor = this.backgroundColor;
break;
case 'color':
style.backgroundColor = this.backgroundColor;
break;
case 'none':
// 不使用任何背景
break;
}

return style;
}
}
}
</script>

<style lang="scss">
 @import './styles/index.scss'; // 全局自定义的css样式
</style>