<template>


  <div class="chat-container">
    <div class="chat-window" ref="chatWindow">

      <div
          v-for="(msg, index) in messageList"
          :key="index"
          :class="['message', msg.role === 'User' ? 'user' : 'gpt']"
      >
        <div class="avatar">{{ msg.role === 'User' ? '🧑' : '🤖' }}</div>
        <div class="text" v-html="renderMarkdown(msg.text)"></div>
      </div>
    </div>

    <div class="input-container">

      <textarea
          v-model="newMessage"
          @keydown.enter.prevent="handleEnter"
          placeholder="输入消息..."
          class="message-input"
          ref="messageInput"
      ></textarea>
      <button @click="sendMessage" class="send-button">发送</button>

    </div>
  </div>

</template>

<script>


import {ElMessage} from 'element-plus'
import axios from 'axios'
// 支持 Markdown 语法样式显示
import { marked } from "marked";

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
            // 消息接收完毕，自动滚动到底部
            this.scrollToBottom();
          } else {
            // console.log('flag msg---' + msg);

            // 解析json
            const jsonMsg = JSON.parse(msg);
            // 找到当前最新的消息对象，并更新其 text 字段
            if (this.messageList.length > 0) {
              this.messageList[this.messageList.length - 1].text += jsonMsg["content"];
              // if (!this.messageList[this.messageList.length - 1].buffer) {
              //   this.messageList[this.messageList.length - 1].buffer = [];
              // }
              // this.messageList[this.messageList.length - 1].buffer.push(jsonMsg["content"]);
              // this.messageList[this.messageList.length - 1].text = this.messageList[this.messageList.length - 1].buffer.join('');
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
      if (!this.newMessage.trim())
        return;

      if (this.newMessage.trim()) {

        axios.get('http://localhost:9090/sse/sendMsg', {
          params: {
            uuid: this.uuid,
            text: this.newMessage
          }
        })
            .then(response => {
              console.log('请求成功:', response);
              console.log('请求成功:', response.data);
              if (response.data) {
                this.eventSource = response.data
              }
            })
            .catch(error => {
              console.error('请求失败:', error);
            });

        // 添加用户消息
        this.messageList.push({role: 'User', text: this.newMessage});
        this.newMessage = ''; // 清空输入框
        this.$nextTick(() => this.$refs.messageInput.focus()); // 保持输入框焦点

        // // 模拟 ChatGPT 回复
        // setTimeout(() => {
        //   this.messageList.push({role: 'GPT', text: 'ChatGPT 回复: ' + userMessage});
        // }, 1000);
      }
    },
    handleEnter(event) {
      if (event.shiftKey) {
        // Shift + Enter 换行
        this.newMessage += '\n';
      } else {
        // Enter 发送消息
        event.preventDefault();  // 阻止默认行为，以便不换行
        this.sendMessage();
      }
    },
    // 新消息出现时，自动滚动到底部
    scrollToBottom() {
      this.$nextTick(() => {
        const chatWindow = this.$refs.chatWindow;
        chatWindow.scrollTop = chatWindow.scrollHeight;
      });
    },
    renderMarkdown(text) {
      return marked(text);
    }
  }
}
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 85vh;
  width: 80%;
  margin: auto;
  border: 1px solid #ddd;
  border-radius: 8px;
  overflow: hidden;
}

.chat-window {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
  background: #f4f4f4;
  display: flex;
  flex-direction: column;
}

.message {
  /*align-items: flex-start;*/
  display: flex; /* 让消息框独占一行 */
  /*display: block; !* 让消息框独占一行 *!*/
  width: fit-content; /* 让消息框根据内容调整宽度 */
  max-width: 80%; /* 限制最大宽度，防止过长 */
  word-break: break-word; /* 允许长单词自动换行 */
  padding: 5px;
  margin: 5px 0;
  border-radius: 4px;
  white-space: pre-wrap; /* 实现特殊字符效果 允许自动换行 */
}

.user {
  align-self: flex-end;
  flex-direction: row-reverse;
  text-align: right; /* 确保文本右对齐 */
}

.gpt {
  align-self: flex-start;
  text-align: left; /* 确保文本左对齐 */
}

.avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 18px;
  margin: 5px;
  background: #ddd;
}

.user .avatar {
  background: #4caf50;
  color: white;
}

.gpt .avatar {
  background: #2196f3;
}

.text {
  padding: 10px;
  border-radius: 10px;
  line-height: 1.5;
  word-break: break-word;
  background: white;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.user .text {
  background: #dcf8c6;
}

.input-container {
  display: flex;
  align-items: center;
  padding: 10px;
  background: #fff;
  border-top: 1px solid #ddd;
}

.message-input {
  flex: 1;
  height: 40px;
  padding: 8px;
  border: 1px solid #ddd;
  border-radius: 5px;
  outline: none;
  resize: vertical; /* 只能上下拉伸 */
  overflow-y: auto; /* 内容溢出时显示滚动条 */
  max-height: 100px;
  min-height: 20px;
}

.send-button {
  margin-left: 10px;
  padding: 10px 15px;
  border: none;
  background: #2196f3;
  color: white;
  border-radius: 5px;
  cursor: pointer;
}

.send-button:hover {
  background: #1976d2;
}
</style>
