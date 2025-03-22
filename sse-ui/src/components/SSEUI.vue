<template>


  <div class="chat-container">
    <div class="chat-window">
      <div v-for="(msg, index) in messageList" :key="index" :class="['message', msg.role === 'User' ? 'user' : 'gpt']">
        {{ msg.text }}
      </div>
    </div>
    <input v-model="newMessage" @keyup.enter="sendMessage" placeholder="输入消息..."/>
    <button @click="sendMessage">发送</button>
  </div>

</template>

<script>


import {ElMessage} from 'element-plus'
import axios from 'axios'

/*
客户端：
  1、使用 EventSource 监听 SSE 服务器推送的消息。
  2、在 Vue 组件的 created() 生命周期中初始化 SSE 连接。
  3、在 beforeDestroy() 中关闭连接，避免内存泄漏。
 */

const BOS = "<BOS>"
const EOS = "<EOS>"

export default {
  name: 'SSEUI',

  data() {
    return {
      // 当前 SSE 客户端的UUID, 这里要优化一下,UUID由后端生成,保证全局唯一
      uuid: crypto.randomUUID(),
      // SSE 连接对象
      eventSource: null,
      // 输入框的数据
      newMessage: 'What are you model???',
      // 聊天记录
      messageList: [
        {role: "User", text: "你是谁"},
        {role: "GPT", text: "我是基于ChatGPT的聊天模型"}
      ],
      currentResultMsgUuid: null,
      bufferedText: "", // 用于存储拼接的 text
    };
  },
  mounted() {
  },
  created() {
    this.initSSE();
  },
  beforeDestroy() {
    this.closeSSE();
  },
  methods: {
    initSSE() {
      // 确保该 客户端 可以使用sse功能
      if (window.EventSource) {

        this.eventSource = new EventSource("http://localhost:9090/sse/createSse?uuid=" + this.uuid); // 连接服务器 SSE 接口

        if (this.eventSource) {
          console.log(this.uuid, '连接SSE成功！！！');
        }

        // 监听onmessage消息
        this.eventSource.onmessage = (event) => {

          //去除出字符串"T"两端的引号“
          let msg = event.data //.replace(/^"|"$/g, '');
          // console.log(' msg', msg);

          // 判断是开始还是结束或是正常的消息
          if (BOS === msg) {
            // console.log('flag BOS',msg)

            // 第一次接收到这个 entId 的消息，
            this.bufferedText = ""; // 清空缓冲区，准备接收新消息
            this.messageList.push({role: "GPT", text: ""}); // 先添加一个空消息对象
          } else if (EOS === msg) {
            // console.log('flag EOS', msg);

            // 清空缓冲区，准备下一条消息
            // 消息完成，Vue 会自动响应式更新
            this.bufferedText = "";
          } else {
            // console.log('flag msg---' + msg);

            // 解析json
            const jsonMsg = JSON.parse(msg);
            // 找到当前最新的消息对象，并更新其 text 字段
            if (this.messageList.length > 0) {
              // this.messageList[this.messageList.length - 1].text += jsonMsg["content"];
              if (!this.messageList[this.messageList.length - 1].buffer) {
                this.messageList[this.messageList.length - 1].buffer = [];
              }
              this.messageList[this.messageList.length - 1].buffer.push(jsonMsg["content"]);
              this.messageList[this.messageList.length - 1].text = this.messageList[this.messageList.length - 1].buffer.join('');
            }

            // 由于 Vue 不能检测数组对象属性变化，需要手动触发更新
            this.$forceUpdate();

          }

        };

        // 监听错误
        this.eventSource.onerror = (error) => {
          console.error("SSE 连接错误:", error);
          ElMessage({
            message: 'SSE 连接错误' + error,
            type: 'error',
          })
          this.closeSSE();
        };
      } else {
        console.error("当前浏览器不支持 SSE");
        ElMessage({
          message: '当前浏览器不支持 SSE',
          type: 'error',
        })
      }
    },
    closeSSE() {
      if (this.eventSource) {
        this.eventSource.close();
        this.eventSource = null;
      }
    },
    sendMessage() {
      if (this.newMessage.trim()) {

        // 添加用户消息
        this.messageList.push({role: 'User', text: this.newMessage});
        let userMessage = this.newMessage; // 备份消息
        this.newMessage = ''; // 清空输入框


        axios.get('http://localhost:9090/sse/sendMsg', {
          params: {
            uuid: this.uuid,
            text: userMessage
          }
        })
            .then(response => {
              console.log('请求成功:', response.data);
            })
            .catch(error => {
              console.error('请求失败:', error);
            });


        // // 模拟 ChatGPT 回复
        // setTimeout(() => {
        //   this.messageList.push({role: 'GPT', text: 'ChatGPT 回复: ' + userMessage});
        // }, 1000);
      }
    }
  }
}
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped>


.chat-container {
  width: 300px;
  margin: 20px auto;
  border: 1px solid #ccc;
  padding: 10px;
  border-radius: 5px;
}

.chat-window {
  height: 300px;
  overflow-y: auto;
  border-bottom: 1px solid #ddd;
  padding-bottom: 10px;
}

.message {
  padding: 5px;
  margin: 5px 0;
  border-radius: 4px;
}

.user {
  background: #daf1ff;
  text-align: right;
}

.bot {
  background: #f1f1f1;
}

input {
  width: calc(100% - 60px);
  padding: 5px;
}

button {
  width: 50px;
  padding: 5px;
}

.user {
  background: #daf1ff;
  text-align: right;
  align-self: flex-end;
}

.gpt {
  background: #f1f1f1;
  text-align: left;
  align-self: flex-start;
}
</style>
