<template>
  <div class="agree-page">
    <div class="agree-card">
      <header>
        <h1>📜 用户协议 & 隐私政策</h1>
        <div class="meta">最后更新: 2026-06-18 · v2.0</div>
      </header>
      <el-tabs v-model="tab">
        <el-tab-pane label="用户协议" name="ua">
          <section v-for="(s, i) in sections" :key="i">
            <h2>{{ i+1 }}. {{ s.title }}</h2>
            <p v-for="(p, j) in s.body" :key="j">{{ p }}</p>
          </section>
        </el-tab-pane>
        <el-tab-pane label="隐私政策" name="pp">
          <h2>1. 我们收集什么</h2>
          <p>登录账号、工作流配置、对话记录(用于提升服务质量)。</p>
          <h2>2. 我们如何用</h2>
          <p>仅用于为您提供服务; 不会与第三方共享; 您可随时导出/删除。</p>
          <h2>3. 数据存储</h2>
          <p>中国大陆境内服务器, 符合《个人信息保护法》(PIPL) 要求。</p>
          <h2>4. 您的权利</h2>
          <p>查阅 / 更正 / 删除 / 导出 / 撤回授权 — 提交工单 24h 内响应。</p>
          <h2>5. 联系我们</h2>
          <p>liugl951127@gmail.com · 7×24 响应</p>
        </el-tab-pane>
        <el-tab-pane label="服务等级 (SLA)" name="sla">
          <h2>可用性</h2>
          <p>月可用性 ≥ 99.5% (计划维护除外)</p>
          <h2>响应时间</h2>
          <p>P0 故障 ≤ 30min, P1 ≤ 2h, P2 ≤ 8h, P3 ≤ 24h</p>
          <h2>赔偿</h2>
          <p>月可用性低于 99% 退还当月 10%, 低于 95% 退还 30%。</p>
        </el-tab-pane>
      </el-tabs>
      <footer>
        <el-button @click="$router.back()">返回</el-button>
        <el-button type="primary" @click="$router.push('/dashboard')">我已阅读</el-button>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
defineOptions({ name: 'Agreement' })
const tab = ref('ua')
const sections = [
  { title: '服务范围', body: ['AI Agent Platform 是企业级多租户 AI 智能体平台, 提供模型训练/推理/工作流编排/知识库 RAG 等能力。', '本协议是您与平台运营方之间关于使用本服务所订立的协议。'] },
  { title: '账号与权限', body: ['您应妥善保管账号密码, 因泄露造成的损失由您自行承担。', '严禁出借账号给第三方, 一经发现将立即封禁。'] },
  { title: '禁止行为', body: ['不得利用本服务从事违反中国法律法规的活动。', '不得上传含病毒/木马/违法内容。', '不得对平台进行反向工程/攻击。'] },
  { title: '知识产权', body: ['平台代码/文档版权归运营方所有, 您上传的私有数据归您所有。', '您授权平台为提供服务所必要的复制/分发/展示权。'] },
  { title: '免责', body: ['因不可抗力 (自然灾害/网络中断/政府行为) 造成服务中断, 平台不承担责任。', '因您自身操作失误造成的数据丢失, 平台不承担责任。'] }
]
</script>

<style scoped>
.agree-page { min-height: 100vh; background: linear-gradient(135deg, #f8fafc, #eef2ff); padding: 40px 20px; }
.agree-card { max-width: 880px; margin: 0 auto; background: #fff; border-radius: 16px; box-shadow: 0 8px 32px rgba(99,102,241,0.1); padding: 32px 48px; }
header { border-bottom: 1px solid #e2e8f0; padding-bottom: 16px; margin-bottom: 24px; }
header h1 { margin: 0; font-size: 22px; color: #1e293b; }
header .meta { color: #64748b; font-size: 12px; margin-top: 4px; }
section { margin-bottom: 24px; }
h2 { color: #1e293b; font-size: 16px; margin: 16px 0 8px; }
p { color: #475569; line-height: 1.7; font-size: 14px; margin: 4px 0; }
footer { display: flex; gap: 12px; justify-content: flex-end; padding-top: 24px; border-top: 1px solid #e2e8f0; margin-top: 24px; }
</style>
