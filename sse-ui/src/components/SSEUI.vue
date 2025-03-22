<template>


  <div class="common-layout">
    <el-container>
      <el-header class="header">sse-ui</el-header>
      <el-container>

      </el-container>
    </el-container>
  </div>

</template>

<script>


import {ElMessage} from 'element-plus'

// //
// ElMessage({
//   message: '消息发送成功',
//   type: 'success',
// })

export default {
  name: 'SSEUI',

  data() {
    return {
    }
  },
  mounted() {
    console.log("SSEUI 组件创建")
  },
  methods: {

  }
}
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped>

</style>
