<template>
  <div class="workflow-page">
    <!-- 顶部 toolbar -->
    <header class="wf-toolbar">
      <div class="tb-left">
        <h2>🧩 工作流编排</h2>
        <el-input v-model="specName" placeholder="工作流名称" size="default" style="width: 240px" />
        <el-button type="primary" :loading="saving" @click="save">
          <el-icon><Document /></el-icon> 保存
        </el-button>
        <el-tooltip
          :content="nodes.length && !validation.valid ? '流程不合法: ' + validation.reason : ''"
          placement="bottom"
        >
          <el-button
            type="success"
            :loading="running"
            :disabled="!nodes.length || !validation.valid"
            @click="run"
          >
            <el-icon><VideoPlay /></el-icon> 运行 ({{ nodes.length }} 节点)
          </el-button>
        </el-tooltip>
        <el-tooltip
          :content="nodes.length && !validation.valid ? '流程不合法: ' + validation.reason : ''"
          placement="bottom"
        >
          <el-button
            :disabled="!nodes.length || !validation.valid"
            @click="exportSpec"
          >
            <el-icon><Promotion /></el-icon> 导出
          </el-button>
        </el-tooltip>
        <el-tooltip content="从 JSON 文件加载流程 (跨平台)" placement="bottom">
          <el-button @click="triggerImport">
            <el-icon><Upload /></el-icon> 导入
          </el-button>
        </el-tooltip>
        <input ref="fileInput" type="file" accept=".json" style="display: none" @change="onFileSelected" />
        <el-button :disabled="!history.length" @click="undo">
          <el-icon><Back /></el-icon> 撤销
        </el-button>
        <el-button :disabled="historyIdx < 0" @click="redo">
          <el-icon><RefreshRight /></el-icon> 重做
        </el-button>
        <el-divider direction="vertical" />
        <el-button @click="zoomOpen = true" :disabled="!nodes.length">
          <el-icon><FullScreen /></el-icon> 放大画布
        </el-button>
      </div>
      <div class="tb-right">
        <el-button text @click="showGuide = true">
          <el-icon><QuestionFilled /></el-icon> 使用说明
        </el-button>
        <el-button text @click="loadSpecList">
          <el-icon><FolderOpened /></el-icon> 我的工作流
        </el-button>
        <el-button text type="primary" @click="aiGenOpen = true">
          <el-icon><MagicStick /></el-icon> AI 极速生成
        </el-button>
        <el-button text @click="loadTemplate('rag')">
          <el-icon><Download /></el-icon> 加载 RAG 模板
        </el-button>
      </div>
    </header>

    <!-- 三列布局: 节点库 / 画布 / 节点配置 -->
    <div class="wf-grid">
      <!-- 左: 节点库 -->
      <aside class="palette">
        <h4>节点库 (拖到中间)</h4>
        <div v-for="g in palette" :key="g.group" class="pal-group">
          <div class="pal-group-hd">
            <span class="pal-ico" :style="{ background: g.c1 }">{{ g.icon }}</span>
            <strong>{{ g.group }}</strong>
            <small class="muted">{{ g.nodes.length }}</small>
          </div>
          <el-popover
            v-for="n in g.nodes"
            :key="n.id"
            placement="right"
            :width="320"
            trigger="hover"
            :show-after="200"
          >
            <template #reference>
              <div
                class="pal-node"
                draggable="true"
                @dragstart="onDragStart($event, n)"
                @click="addNode(n)"
              >
                <el-icon><component :is="n.icon" /></el-icon>
                <div class="pal-meta">
                  <div class="pal-name">{{ n.name }}</div>
                  <div class="pal-desc">{{ n.desc }}</div>
                </div>
              </div>
            </template>
            <div class="tip-box">
              <div class="tip-title">
                <el-icon><component :is="n.icon" /></el-icon>
                {{ n.name }}
                <el-tag size="small" effect="plain" type="info">{{ n.id }}</el-tag>
              </div>
              <p class="tip-desc">{{ n.desc }}</p>
              <table v-if="n.tips" class="tip-table">
                <tr v-for="t in n.tips" :key="t.k">
                  <td class="tip-k">{{ t.k }}</td>
                  <td class="tip-v">{{ t.v }}</td>
                </tr>
              </table>
            </div>
          </el-popover>
        </div>
      </aside>

      <!-- 中: 画布 -->
      <main
        class="canvas"
        :class="{ 'canvas-invalid': !validation.valid && nodes.length > 0 }"
        @drop="onDrop"
        @dragover.prevent
        @mousedown="onCanvasMouseDown"
      >
        <!-- 顶部流程状态条 -->
        <div v-if="nodes.length" class="canvas-status" :class="validation.valid ? 'ok' : 'err'">
          <el-icon><CircleCheckFilled v-if="validation.valid" /><CircleCloseFilled v-else /></el-icon>
          <span v-if="validation.valid">
            ✓ 流程合法 · 起点: {{ validation.starts.map(id => nodes.find(n => n.id === id)?.name).filter(Boolean).join(', ') || '无' }} · 节点数: {{ nodes.length }}
          </span>
          <span v-else>✗ {{ validation.reason }}</span>
        </div>
        <div v-if="!nodes.length" class="canvas-empty">
          <el-icon :size="48" color="#cbd5e1"><Plus /></el-icon>
          <h3>拖入节点开始编排</h3>
          <p class="muted">从左侧拖入节点 / 直接点击添加<br>连接节点后点"运行"即可执行</p>
          <el-button type="primary" plain @click="loadTemplate('rag')">
            <el-icon><Download /></el-icon> 加载 RAG 模板
          </el-button>
        </div>

        <!-- 节点卡片: 只显示名称, 右上角 ? 提示 + X 删除 -->
        <div
          v-for="n in nodes"
          :key="n.id"
          :ref="(el) => setNodeRef(el, n.id)"
          :data-node-id="n.id"
          class="wf-node"
          :class="{
            running: currentNode === n.id,
            done: doneSet.has(n.id),
            failed: failedIds.has(n.id),
            selected: isSelected(n.id),
            'is-start': validation.starts.includes(n.id),
            'is-end': !edges.some(e => e.from === n.id) && edges.some(e => e.to === n.id),
            'is-cycle': validation.cycle.includes(n.id)
          }"
          :style="{ left: n.x + 'px', top: n.y + 'px' }"
          @mousedown.stop="onNodeMouseDown($event, n)"
          @dblclick.stop="openConfig(n)"
        >
          <el-icon class="wn-ico"><component :is="iconOf(n)" /></el-icon>
          <span class="wn-name">{{ n.name }}</span>
          <el-popover
            placement="right"
            :width="320"
            trigger="hover"
            :show-after="200"
            popper-class="node-tip-popper"
          >
            <template #reference>
              <el-icon class="wn-help" @click.stop><QuestionFilled /></el-icon>
            </template>
            <div class="tip-box">
              <div class="tip-title">
                <el-icon><component :is="iconOf(n)" /></el-icon>
                {{ n.name }}
                <el-tag size="small" effect="plain" type="info">{{ n.id }}</el-tag>
              </div>
              <p class="tip-desc">{{ n.desc || tplDesc(n) }}</p>
              <div v-if="Object.keys(n.params).length" class="tip-params">
                <strong>当前参数:</strong>
                <code v-for="(v, k) in n.params" :key="k">{{ k }}=<span>{{ v }}</span></code>
              </div>
              <div style="margin-top: 8px; padding-top: 8px; border-top: 1px dashed #e5e7eb; font-size: 11px; color: #94a3b8;">
                💡 双击节点可改参数
              </div>
            </div>
          </el-popover>
          <el-icon class="wn-del" @click.stop="removeNode(n.id)"><Close /></el-icon>
          <!-- 输入端口 (左边) -->
          <span
            :class="['wn-port', 'wn-port-in', _connectFrom && _connectFrom.id !== n.id ? 'wn-connecting' : '']"
            style="left: -5px; top: 50%; transform: translateY(-50%);"
            @mousedown.stop="onPortMouseDown($event, n, 'in', 'in')"
            title="输入端口 - 点击连线"
          />
          <!-- 输出端口 (右边) -->
          <span
            v-for="p in n.outPorts"
            :key="p"
            class="wn-port wn-port-out"
            :style="{ right: '-5px', top: ((n.outPorts.indexOf(p) + 1) * 14) + 'px' }"
            @mousedown.stop="onPortMouseDown($event, n, p, 'out')"
            title="输出端口 - 点击连线"
          />
        </div>

        <!-- SVG 连线 -->
        <svg v-if="edges.length" class="wires" :viewBox="`0 0 ${canvasW} ${canvasH}`" preserveAspectRatio="none">
          <defs>
            <!-- 默认箭头 (紫蓝) — 流程合法 -->
            <marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto">
              <path d="M 0 0 L 10 5 L 0 10 z" fill="#6366f1" />
            </marker>
            <!-- 错误箭头 (红) — 环 -->
            <marker id="arrow-err" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto">
              <path d="M 0 0 L 10 5 L 0 10 z" fill="#ef4444" />
            </marker>
            <!-- 选中箭头 (橙) — 高亮选中边 -->
            <marker id="arrow-sel" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto">
              <path d="M 0 0 L 10 5 L 0 10 z" fill="#f59e0b" />
            </marker>
          </defs>
          <path
            v-for="(e, i) in edges"
            :key="i"
            :d="edgePath(e)"
            :stroke="edgeColor(i)"
            :stroke-width="selectedEdge === i ? 3.5 : (validation.valid ? 2 : 3)"
            fill="none"
            :marker-end="selectedEdge === i ? 'url(#arrow-sel)' : (validation.valid ? 'url(#arrow)' : 'url(#arrow-err)')"
            :class="['edge-path', { 'edge-cycle': !validation.valid, 'edge-selected': selectedEdge === i }]"
            @click.stop="selectEdge(i)"
            style="cursor: pointer; pointer-events: stroke;"
          />
        </svg>
      </main>

      <!-- 右: 节点配置 / 运行日志 -->
      <aside class="inspector">
        <el-tabs v-model="tab">
          <el-tab-pane label="节点配置" name="cfg">
            <div v-if="!selectedNode" class="empty-tip">点击画布上的节点查看配置</div>
            <div v-else>
              <h4>{{ selectedNode.name }} <small class="muted">{{ selectedNode.id }}</small></h4>
              <el-form label-position="top" size="small">
                <el-form-item v-for="p in paramSchema(selectedNode)" :key="p.key" :label="p.label">
                  <el-input v-if="p.type === 'string' || !p.type" v-model="selectedNode.params[p.key]" :placeholder="String(p.default || '')" />
                  <el-input-number v-else-if="p.type === 'number'" v-model="selectedNode.params[p.key]" :min="p.min" :max="p.max" :step="p.step || 1" style="width: 100%" />
                  <el-switch v-else-if="p.type === 'boolean'" v-model="selectedNode.params[p.key]" />
                </el-form-item>
              </el-form>
              <el-button type="danger" plain @click="removeNode(selectedNode.id)">
                <el-icon><Delete /></el-icon> 删除节点
              </el-button>
            </div>
          </el-tab-pane>
          <el-tab-pane label="运行日志" name="log">
            <div class="run-info" v-if="lastRun">
              <el-tag :type="lastRun.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
                {{ lastRun.status }}
              </el-tag>
              <span class="muted small">耗时 {{ lastRun.durationMs }}ms · {{ new Date(lastRun.ts).toLocaleTimeString() }}</span>
              <el-button v-if="lastRun.failedNodeId" size="small" type="warning" plain @click="retry">
                <el-icon><RefreshRight /></el-icon> 重试 {{ lastRun.failedNodeId }}
              </el-button>
            </div>
            <el-scrollbar ref="logEl" height="calc(100vh - 280px)">
              <div v-for="(l, i) in logs" :key="i" class="log-line" :class="l.level">
                <span class="log-ts">{{ l.ts }}</span>
                <span class="log-tag">{{ l.tag }}</span>
                <span class="log-msg">{{ l.msg }}</span>
              </div>
            </el-scrollbar>
          </el-tab-pane>
          <el-tab-pane label="我的工作流" name="list">
            <el-button size="small" @click="loadSpecList" :loading="loadingSpec">
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
            <div v-for="s in specList" :key="s.id" class="spec-row">
              <div>
                <div class="spec-name">{{ s.name }}</div>
                <small class="muted">{{ s.nodes?.length || 0 }} 节点 · {{ new Date(s.updatedAt).toLocaleString() }}</small>
              </div>
              <el-button-group>
                <el-button size="small" @click="loadSpec(s)">打开</el-button>
                <el-button size="small" type="danger" @click="removeSpec(s.id)">删</el-button>
              </el-button-group>
            </div>
            <el-empty v-if="!specList.length && !loadingSpec" description="暂无工作流" />
          </el-tab-pane>
        </el-tabs>
      </aside>
    </div>

    <!-- 使用说明 drawer -->
    <el-drawer v-model="showGuide" title="📖 工作流编排使用说明" size="540px" direction="rtl">
      <div class="guide">
        <h3>1. 这是什么</h3>
        <p>工作流是一个<b>有向无环图 (DAG)</b> — 由节点 + 连线组成. 每个节点调用一个具体能力 (训练 / 推理 / 知识库 / Agent / 工具), 数据沿连线顺序流转.</p>

        <h3>2. 怎么用</h3>
        <ol>
          <li><b>添加节点</b>: 从左侧"节点库"拖入或点击. 支持 32 类节点, 12 个分组.</li>
          <li><b>连接</b>: 拖动节点右侧圆点(输出)到下一节点(输入).</li>
          <li><b>配置</b>: 点击节点, 右侧"节点配置"里改参数.</li>
          <li><b>运行</b>: 点顶部"运行"按钮, 后端按连线顺序依次执行, 实时显示日志和每个节点的返回.</li>
          <li><b>保存</b>: 命名后点"保存", 存入后端, 下次从"我的工作流"打开.</li>
        </ol>

        <h3>3. 节点分组说明</h3>
        <ul>
          <li><b>📊 数据准备</b>: dataset_list / data_loader / data_clean / data_split — 数据接入与预处理</li>
          <li><b>⚙️ 训练</b>: train_lora / train_dpo / train_full — 训练一个模型</li>
          <li><b>🧪 评估</b>: eval_bleu / eval_hallucination — 评估模型质量</li>
          <li><b>🚀 部署</b>: model_register / model_deploy — 上线模型到推理服务</li>
          <li><b>🤖 Agent</b>: agent_think / agent_tool — ReAct 智能体</li>
          <li><b>📚 知识库</b>: kb_ingest / kb_search / kb_chunk / kb_embed — RAG 全流程</li>
          <li><b>🛠️ 工具</b>: tool_clean / tool_chunk / tool_web_search — 通用工具</li>
          <li><b>🔮 推理</b>: infer_generate / infer_embed / infer_chat — 模型推理</li>
        </ul>

        <h3>4. 实际功能</h3>
        <p>每个节点执行时, 都<b>真正调用后端微服务</b>:</p>
        <ul>
          <li>训练节点 → <code>ai-platform-trainer</code> 服务 (DJL + PyTorch)</li>
          <li>知识库 → <code>ai-platform-knowledge</code> 服务 (Elasticsearch 8)</li>
          <li>Agent → <code>ai-platform-agent</code> 服务 (ReAct + 工具调用)</li>
          <li>推理 → <code>ai-platform-inference</code> 服务 (ONNX)</li>
          <li>工具 → <code>ai-platform-system</code> / <code>ai-platform-files</code></li>
        </ul>

        <h3>5. 快捷键</h3>
        <ul>
          <li><kbd>Ctrl</kbd>+<kbd>Z</kbd> 撤销</li>
          <li><kbd>Ctrl</kbd>+<kbd>Y</kbd> / <kbd>Ctrl</kbd>+<kbd>Shift</kbd>+<kbd>Z</kbd> 重做</li>
          <li><kbd>Delete</kbd> 删除选中节点/边</li>
          <li><kbd>Esc</kbd> 取消选择</li>
          <li>画布空白处拖动 = 框选多个节点</li>
        </ul>

        <h3>6. 加载 RAG 模板</h3>
        <p>点右上"加载 RAG 模板", 自动创建一个 3 节点流水线:</p>
        <ol>
          <li>kb_ingest — 文档切块入库</li>
          <li>kb_search — 检索 Top-K 文档</li>
          <li>agent_think — LLM 基于检索结果回答</li>
        </ol>
      </div>
    </el-drawer>

    <!-- 双击节点: 改参数 dialog (后端拉 schema + AI 建议) -->
    <el-dialog v-model="configVisible" :title="configTitle" width="720px" align-center
      :close-on-click-modal="false" :close-on-press-escape="true"
      @close="onConfigDialogClose">
      <div v-loading="configLoading" v-if="configNode">
        <!-- ★ 异常 Banner (如果该节点刚运行失败, 顶部醒示) -->
        <el-alert
          v-if="configNode._failedReason"
          type="error"
          :closable="true"
          show-icon
          :title="`🚨 本节点上次运行异常: ${configNode._failedReason}`"
          style="margin-bottom: 12px;"
        >
          <template #default>
            <div style="margin-top: 4px;">
              <b>错误原因:</b> {{ configNode._failedReason }}<br>
              <b>修改建议:</b> 检查下面标红的必填项 / 调大超时 / 点右侧 [🤖 AI 补全参数] / 点 [问 AI 怎么修]<br>
              <span style="color: #6b7280;">改完后点 [确定保存] 再点顶栏 [▶ 运行] 重试</span>
            </div>
          </template>
        </el-alert>

        <el-row :gutter="16">
          <!-- 左: 参数表单 -->
          <el-col :span="14">
            <h4 style="margin: 0 0 8px; color: #6366f1;">📝 参数配置</h4>
            <el-form label-position="top" size="default">
              <el-form-item v-for="p in configSchema" :key="p.key"
                  :label="p.label + (p.required ? ' *' : '')"
                  :error="isFieldInvalid(p) ? fieldErrorTip(p) : ''">
                <template v-if="p.type === 'select'">
                  <el-select v-model="configNode.params[p.key]" placeholder="选择" style="width: 100%">
                    <el-option v-for="opt in (p.options || [])" :key="opt" :label="opt" :value="opt" />
                  </el-select>
                </template>
                <template v-else-if="p.type === 'number'">
                  <el-input-number v-model="configNode.params[p.key]" :min="p.min" :max="p.max" :step="p.step || 1" style="width: 100%" />
                </template>
                <template v-else-if="p.type === 'boolean'">
                  <el-switch v-model="configNode.params[p.key]" />
                </template>
                <template v-else-if="p.type === 'textarea'">
                  <el-input v-model="configNode.params[p.key]" type="textarea" :rows="3" :placeholder="String(p.defaultValue || '')" />
                </template>
                <template v-else>
                  <el-input v-model="configNode.params[p.key]" :placeholder="String(p.defaultValue || '')" />
                </template>
                <div v-if="p.description" class="field-tip">{{ p.description }}</div>
              </el-form-item>
              <el-empty v-if="!configSchema.length" description="该节点无参数" />
            </el-form>
          </el-col>
          <!-- 右: AI 建议 -->
          <el-col :span="10">
            <h4 style="margin: 0 0 8px; color: #10b981;">🤖 AI 智能建议</h4>
            <el-button type="success" plain :loading="suggestLoading" @click="askAI" size="small" style="width: 100%">
              <el-icon><MagicStick /></el-icon> 根据当前输入给建议
            </el-button>
            <el-button v-if="configSuggestions.length" type="primary" plain size="small" @click="applyAllSuggestions" style="width: 100%; margin-top: 4px">
              全部应用
            </el-button>
            <el-scrollbar height="380px" style="margin-top: 8px;">
              <div v-if="!configSuggestions.length && !suggestLoading" class="empty-tip">
                💡 点击上面按钮, AI 会根据节点类型 + 你的当前输入给出每个参数的最佳推荐
              </div>
              <el-card v-for="s in configSuggestions" :key="s.key" shadow="never" class="suggestion-card">
                <div class="sug-head">
                  <strong>{{ s.key }}</strong>
                  <el-button type="primary" link size="small" @click="applySuggestion(s)">采用</el-button>
                </div>
                <div class="sug-row"><span class="lbl">当前:</span><code>{{ s.current || '(空)' }}</code></div>
                <div class="sug-row"><span class="lbl">推荐:</span><code class="rec">{{ s.recommended }}</code></div>
                <div v-if="s.reason" class="sug-reason">💡 {{ s.reason }}</div>
              </el-card>
            </el-scrollbar>
          </el-col>
        </el-row>
      </div>
      <template #footer>
        <el-button @click="cancelConfig">取消</el-button>
        <el-button type="primary" @click="saveConfig">确定保存</el-button>
      </template>
    </el-dialog>

    <!-- 放大画布: 全屏 dialog, 不动节点数据, 仅 CSS transform 缩放/拖动 -->
    <el-dialog
      v-model="zoomOpen"
      title="🔍 放大画布"
      fullscreen
      :show-close="false"
      :modal="true"
      class="zoom-dialog"
    >
      <div class="zoom-toolbar">
        <span class="muted">画布缩放 (滚轮 / 按键) · 拖动画布移动</span>
        <el-button-group>
          <el-button @click="zoomOut" size="small"><el-icon><ZoomOut /></el-icon></el-button>
          <el-button @click="zoomReset" size="small">{{ Math.round(zoom * 100) }}%</el-button>
          <el-button @click="zoomIn" size="small"><el-icon><ZoomIn /></el-icon></el-button>
        </el-button-group>
        <el-button-group>
          <el-button @click="panReset" size="small" type="primary" plain>
            <el-icon><Aim /></el-icon> 重置视图
          </el-button>
        </el-button-group>
        <el-button type="danger" plain @click="zoomOpen = false" size="small">
          <el-icon><Close /></el-icon> 关闭放大 (还原)
        </el-button>
      </div>
      <div
        class="zoom-viewport"
        ref="zoomViewport"
        @wheel.prevent="onZoomWheel"
        @mousedown="onZoomPanStart"
      >
        <div
          class="zoom-stage"
          :style="{ transform: `translate(${panX}px, ${panY}px) scale(${zoom})`, transformOrigin: '0 0' }"
        >
          <!-- 复用画布: SVG + 节点 -->
          <svg
            v-if="edges.length"
            class="zoom-wires"
            :viewBox="`0 0 ${canvasW} ${canvasH}`"
            :width="canvasW"
            :height="canvasH"
            :style="{ left: 0, top: 0 }"
          >
            <defs>
              <marker id="z-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto">
                <path d="M 0 0 L 10 5 L 0 10 z" fill="#6366f1" />
              </marker>
              <marker id="z-arrow-err" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto">
                <path d="M 0 0 L 10 5 L 0 10 z" fill="#ef4444" />
              </marker>
              <marker id="z-arrow-sel" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto">
                <path d="M 0 0 L 10 5 L 0 10 z" fill="#f59e0b" />
              </marker>
            </defs>
            <path
              v-for="(e, i) in edges"
              :key="i"
              :d="edgePath(e)"
              :stroke="edgeColor(i)"
              :stroke-width="selectedEdge === i ? 3.5 : (validation.valid ? 2 : 3)"
              fill="none"
              :marker-end="selectedEdge === i ? 'url(#z-arrow-sel)' : (validation.valid ? 'url(#z-arrow)' : 'url(#z-arrow-err)')"
            />
          </svg>
          <div
            v-for="n in nodes"
            :key="n.id"
            :ref="(el) => setNodeRef(el, n.id)"
            class="zoom-node"
            :class="{
              'is-start': validation.starts.includes(n.id),
              'is-end': !edges.some(e => e.from === n.id) && edges.some(e => e.to === n.id),
              'is-cycle': validation.cycle.includes(n.id),
              'is-failed': failedIds.has(n.id),
              'is-selected': selectedIds.includes(n.id)
            }"
            :style="{ left: n.x + 'px', top: n.y + 'px' }"
          >
            <el-icon><component :is="iconOf(n)" /></el-icon>
            <span>{{ n.name }}</span>
          </div>
        </div>
        <div class="zoom-hint muted">
          提示: 滚轮缩放 · 空白处拖动 · Ctrl+0 还原 · 右上“还原”可关闭本窗口
        </div>
      </div>
    </el-dialog>

    <!-- AI 极速生成流程 (一句话 → 画布, 支持多轮修改) -->
    <WorkflowAiGenerate v-model="aiGenOpen" :current="{ name: specName, nodes, edges }" @apply="onAiApply" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch, watchEffect } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  QuestionFilled, Back, RefreshRight, VideoPlay, Document, FolderOpened, Download, Plus, Close, Delete, Refresh, Promotion,
  FullScreen, ZoomIn, ZoomOut, Position, Aim,
  CircleCheckFilled, CircleCloseFilled, MagicStick, Upload
} from '@element-plus/icons-vue'
import { useGlobalBus } from '@/composables/useGlobalBus'
import { useWorkflowRunner } from '@/composables/useWorkflowRunner'
import WorkflowAiGenerate from '@/components/WorkflowAiGenerate.vue'
import { workflowApi } from '@/api'

defineOptions({ name: 'Workflow' })

const bus = useGlobalBus()
const logEl = ref(null)
// 节点 DOM ref map (用于计算真实宽度, 精确连线)
const nodeMap = ref(new Map())
let resizeObserver = null  // 全局 ResizeObserver, 节点变宽/变名同步量
// ★ 节点宽度版本号: 变一调, 驱动 edgePath computed 重算 (因为 _renderW 不在 reactive 里)
const renderVersion = ref(0)
const setNodeRef = (el, id) => {
  if (el) {
    nodeMap.value.set(id, el)
    // 测实际宽度, 存到 node._renderW
    const n = nodes.value.find(x => x.id === id)
    if (n) {
      n._renderW = el.offsetWidth
      // ★ 绑定 ResizeObserver, 名字变了/参数变了节点变宽 → 箭头立即跟上
      if (resizeObserver) resizeObserver.observe(el)
    }
  } else {
    nodeMap.value.delete(id)
  }
}

// ResizeObserver: 节点 DOM size 变 → 同步 _renderW + 驱动箭头重算
const setupResizeObserver = () => {
  if (typeof ResizeObserver === 'undefined') return
  if (resizeObserver) return
  resizeObserver = new ResizeObserver(entries => {
    let changed = false
    for (const entry of entries) {
      const el = entry.target
      const id = el.getAttribute('data-node-id')
      if (!id) continue
      const n = nodes.value.find(x => x.id === id)
      if (n && Math.abs((n._renderW || 0) - el.offsetWidth) > 0.5) {
        n._renderW = el.offsetWidth
        changed = true
      }
    }
    if (changed) renderVersion.value++  // 驱动 edgePath 重算
  })
}

// 监听全局 AI 助手发出的事件 (在其它页点'生成/诊断/建议'后会跳到本页)
onMounted(() => {
  setupResizeObserver()  // ★ 启动 ResizeObserver, 节点名字变长箭头立即重算
  const route = useRoute()
  // ★ 贯通: 从其他页跳过来, 带 presetModel / presetTask
  if (route.query.presetModel) {
    specName.value = '推理闭环: ' + route.query.presetModel
    specDesc.value = '从推理页跳入, 预设模型: ' + route.query.presetModel
    // 加一个 infer 节点
    setTimeout(() => {
      try {
        const tpl = palette.find(p => p.nodes?.some(n => n.type === 'infer_chat'))?.nodes?.find(n => n.type === 'infer_chat')
        if (tpl) {
          addNode(tpl, 60, 60)
          addLog('贯通', '已从 [推理] 页带模型入参: ' + route.query.presetModel, 'success')
          ElMessage.success('已加载预设模型节点, 可点 [▶ 运行] 直接推理')
        }
      } catch (e) { /* ignore */ }
    }, 300)
  }
  bus.on('workflow:ai-generate', ({ input }) => {
    aiGenOpen.value = true
    // 如果 input 已在弹窗填好就直接触发
    setTimeout(() => {
      const ta = document.querySelector('.ai-gen textarea')
      if (ta) {
        ta.value = input || ''
        ta.dispatchEvent(new Event('input', { bubbles: true }))
      }
      // 触发生成按钮
      const btn = document.querySelector('.gen-btn-row .el-button--primary')
      if (btn) btn.click()
    }, 300)
  })
  bus.on('workflow:diagnose', () => {
    if (validation.value.valid) {
      ElMessage.success('当前流程合法, 可以运行')
    } else {
      ElMessage.warning('流程有问题, 请看 AI 助手诊断')
    }
  })
  bus.on('workflow:suggest-params', ({ nodeId }) => {
    if (nodeId) {
      const n = nodes.value.find(x => x.id === nodeId)
      if (n) { selectedNode.value = n; configVisible.value = true; return }
    }
    // 默认打开选中节点
    if (selectedNode.value) configVisible.value = true
    else ElMessage.info('请先选中一个节点, 再点 AI 建议')
  })
  // AI 生成后直接跳过去跑
  bus.on('workflow:run-direct', () => {
    setTimeout(() => run(), 200)
  })
})

// ============== 画布状态 ==============
const specName = ref('我的工作流')
const specDesc = ref('')  // 工作流描述
const nodes = ref([])
const edges = ref([])
const selectedIds = ref([])
const selectedEdge = ref(null)        // 选中的边索引
const selectedNode = computed(() => nodes.value.find(n => n.id === selectedIds.value[0]) || null)

const selectEdge = (i) => {
  selectedEdge.value = i
  selectedIds.value = []  // 互斥
}
const removeEdge = (i) => {
  if (i == null || i < 0) return
  edges.value.splice(i, 1)
  selectedEdge.value = null
  pushHistory('remove edge', { nodes: nodes.value, edges: edges.value })
  addLog('连线', `✓ 边已删除`, 'success')
}
const isSelected = (id) => selectedIds.value.includes(id)
const currentNode = ref(null)
const doneSet = ref(new Set())
const failedIds = ref(new Set())  // 节点失败记录 (高亮 + 选中 + 弹配置)

// ★ 重构: 抽 到 useWorkflowRunner composable, 这里只透传
const {
  running: _wfRunning,
  lastRun: _wfLastRun,
  log: _wfLog,
  run: _wfRun,
  retry: _wfRetry
} = useWorkflowRunner({
  getNodes: () => nodes.value,
  getValidation: () => validation.value,
  getSpecName: () => specName.value,
  onNodeStart: (n) => { currentNode.value = n.id },
  onNodeDone: () => {},
  onNodeFailed: (n, err) => {
    // 高亮 + 选中 + 滚到 + 弹配置 (原代码逻辑保留)
    selectedIds.value = [n.id]
    scrollToNode(n.id)
    openConfig(n)
  }
})
// 别名让原代码少改
const running = _wfRunning
const lastRun = _wfLastRun
const run = _wfRun
const retry = _wfRetry
// ★ logs 面板显示 composable 的日志 (双写同步)
watch(_wfLog, (v) => { logs.value = v }, { deep: true })
const canvasW = 2000
const canvasH = 1200

// ============== 历史 ==============
const history = ref([])
const historyIdx = ref(-1)
const pushHistory = (label, snapshot) => {
  history.value = history.value.slice(0, historyIdx.value + 1)
  history.value.push({ label, snapshot: JSON.parse(JSON.stringify(snapshot)) })
  if (history.value.length > 50) history.value.shift()
  historyIdx.value = history.value.length - 1
}
const undo = () => {
  if (historyIdx.value < 0) return
  const h = history.value[historyIdx.value]
  Object.assign(nodes.value, JSON.parse(JSON.stringify(h.snapshot.nodes || [])))
  Object.assign(edges.value, JSON.parse(JSON.stringify(h.snapshot.edges || [])))
  historyIdx.value--
}
const redo = () => {
  if (historyIdx.value >= history.value.length - 1) return
  historyIdx.value++
  const h = history.value[historyIdx.value]
  Object.assign(nodes.value, JSON.parse(JSON.stringify(h.snapshot.nodes || [])))
  Object.assign(edges.value, JSON.parse(JSON.stringify(h.snapshot.edges || [])))
}

// ============== 运行 / 日志 ==============
// running + lastRun 来自 useWorkflowRunner composable (上面 alias 过了)
// 下面的 logs / addLog 保留供画布日志面板使用
const saving = ref(false)
const logs = ref([])
const tab = ref('cfg')
const addLog = (tag, msg, level = 'info') => {
  const now = new Date()
  const ts = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`
  logs.value.push({ ts, tag, msg, level })
  if (logs.value.length > 200) logs.value.shift()
  nextTick(() => { if (logEl.value) logEl.value.scrollTo({ top: 1e9, behavior: 'smooth' }) })
}

// ============== 节点库 (12 组 32 节点) ==============
const palette = [
  { group: '数据准备', icon: '📊', c1: '#06b6d4', nodes: [
    { id: 'dataset_list', name: '数据集列表', desc: '列出所有数据集', icon: 'Files', outPorts: ['out'], params: { source: '/data/' } },
    { id: 'data_loader', name: '数据加载', desc: '读取 jsonl/json/csv', icon: 'Folder', outPorts: ['out'], params: { format: 'jsonl' } },
    { id: 'data_clean', name: '数据清洗', desc: '正则去噪', icon: 'Brush', outPorts: ['out'], params: { rules: '去除HTML/URL/邮箱' } },
    { id: 'data_split', name: '数据切分', desc: 'train/test 划分', icon: 'ScaleToOriginal', outPorts: ['out'], params: { ratio: '8:2' } }
  ]},
  { group: '训练', icon: '⚙️', c1: '#f59e0b', nodes: [
    { id: 'train_lora', name: 'LoRA 训练', desc: '轻量微调', icon: 'Cpu', outPorts: ['out'], params: { trainerId: 'minigpt', lr: 0.001, maxIters: 200 } },
    { id: 'train_dpo', name: 'DPO 训练', desc: '偏好对齐', icon: 'Cpu', outPorts: ['out'], params: { trainerId: 'minigpt2' } },
    { id: 'train_full', name: '全量微调', desc: '从头训练', icon: 'Cpu', outPorts: ['out'], params: { trainerId: 'llama-mini' } }
  ]},
  { group: '评估', icon: '🧪', c1: '#10b981', nodes: [
    { id: 'eval_bleu', name: 'BLEU 评估', desc: '文本相似度', icon: 'DataAnalysis', outPorts: ['out'], params: {} },
    { id: 'eval_hallucination', name: '幻觉检测', desc: 'RAGAS 评估', icon: 'DataLine', outPorts: ['out'], params: { threshold: 0.7 } }
  ]},
  { group: '部署', icon: '🚀', c1: '#8b5cf6', nodes: [
    { id: 'model_register', name: '注册模型', desc: '注册到版本表', icon: 'Folder', outPorts: ['out'], params: { version: 'v1' } },
    { id: 'model_deploy', name: '部署上线', desc: 'ONNX 推理服务', icon: 'Promotion', outPorts: ['out'], params: { stage: 'prod' } }
  ]},
  { group: 'Agent', icon: '🤖', c1: '#ec4899', nodes: [
    { id: 'agent_think', name: 'Agent 思考', desc: 'ReAct 思考 + 工具调用', icon: 'UserFilled', outPorts: ['out'], params: { maxSteps: 5, systemPrompt: '你是 AI 助手' } },
    { id: 'agent_tool', name: 'Agent 工具', desc: '调用外部工具', icon: 'Tools', outPorts: ['out'], params: { toolName: 'web_search' } }
  ]},
  { group: '知识库', icon: '📚', c1: '#3b82f6', nodes: [
    { id: 'kb_ingest', name: '文档入库', desc: 'chunk + embed + 写 ES', icon: 'Reading', outPorts: ['out'], params: { kbId: 'default' } },
    { id: 'kb_search', name: '知识检索', desc: '向量 + BM25 混合', icon: 'Search', outPorts: ['out'], params: { topK: 3 } },
    { id: 'kb_chunk', name: '文档切片', desc: '256 token 滑窗', icon: 'Document', outPorts: ['out'], params: { chunkSize: 256, overlap: 32 } },
    { id: 'kb_embed', name: '向量化', desc: 'BGE 512 维', icon: 'Coin', outPorts: ['out'], params: { model: 'BAAI/bge-small-zh-v1.5' } }
  ]},
  { group: '工具', icon: '🛠️', c1: '#6366f1', nodes: [
    { id: 'tool_clean', name: '文本清洗', desc: '正则工具', icon: 'Brush', outPorts: ['out'], params: {} },
    { id: 'tool_chunk', name: '文本切片', desc: '滑窗工具', icon: 'Document', outPorts: ['out'], params: {} },
    { id: 'tool_web_search', name: 'Web 搜索', desc: 'DuckDuckGo', icon: 'Search', outPorts: ['out'], params: { maxResults: 5 } }
  ]},
  { group: '推理', icon: '🔮', c1: '#ef4444', nodes: [
    { id: 'infer_generate', name: '文本生成', desc: 'ONNX 生成', icon: 'VideoPlay', outPorts: ['out'], params: { model: 'minigpt', maxTokens: 100 } },
    { id: 'infer_embed', name: 'Embedding', desc: '向量化推理', icon: 'Coin', outPorts: ['out'], params: { model: 'BAAI/bge-small-zh-v1.5' } },
    { id: 'infer_chat', name: '对话', desc: '多轮 chat', icon: 'ChatDotRound', outPorts: ['out'], params: {} }
  ]}
]

// ============== 工具 ==============
const findNode = (id) => nodes.value.find(n => n.id === id)
const iconOf = (n) => {
  for (const g of palette) for (const x of g.nodes) if (x.id === n.id || x.name === n.name) return x.icon
  return 'Cpu'
}
const tplDesc = (n) => {
  for (const g of palette) for (const x of g.nodes) if (x.id === n.id || x.name === n.name) return x.desc || ''
  return ''
}
const paramSchema = (n) => {
  for (const g of palette) for (const x of g.nodes) if (x.id === n.id) {
    return Object.keys(x.params).map(k => ({ key: k, label: k, type: typeof x.params[k] === 'number' ? 'number' : 'string', default: x.params[k] }))
  }
  return []
}

// ============== 节点操作 ==============
let _id = 0
const nid = () => `n${++_id}`

const addNode = (tpl, x, y) => {
  if (x == null) {
    const offset = nodes.value.length
    x = 60 + (offset % 4) * 240
    y = 60 + Math.floor(offset / 4) * 160
  }
  const node = {
    id: nid(),
    type: tpl.id,
    name: tpl.name,
    x, y,
    params: { ...tpl.params },
    outPorts: tpl.outPorts || ['out']
  }
  nodes.value.push(node)
  pushHistory(`add ${tpl.name}`, { nodes: nodes.value, edges: edges.value })
  addLog('节点', `+ ${tpl.name} (${node.id})`, 'info')
}

const removeNode = (id) => {
  const idx = nodes.value.findIndex(n => n.id === id)
  if (idx < 0) return
  nodes.value.splice(idx, 1)
  edges.value = edges.value.filter(e => e.from !== id && e.to !== id)
  selectedIds.value = selectedIds.value.filter(x => x !== id)
  pushHistory(`remove`, { nodes: nodes.value, edges: edges.value })
}

// ============== 拖动 / 框选 ==============
const onDragStart = (e, n) => {
  e.dataTransfer.effectAllowed = 'copy'
  e.dataTransfer.setData('text/plain', JSON.stringify(n))
}
const onDrop = (e) => {
  const raw = e.dataTransfer.getData('text/plain')
  if (!raw) return
  const tpl = JSON.parse(raw)
  const rect = e.currentTarget.getBoundingClientRect()
  addNode(tpl, e.clientX - rect.left, e.clientY - rect.top)
}

const onNodeMouseDown = (e, n) => {
  // 如果正在连线中, 节点本体被点击 = 视为点 in 端口
  if (_connectFrom.value) {
    onPortMouseDown(e, n, 'in', 'in')
    return
  }
  selectedIds.value = [n.id]
  const startX = e.clientX, startY = e.clientY
  const ox = n.x, oy = n.y
  let moved = false
  const move = (m) => {
    moved = true
    n.x = Math.max(0, ox + (m.clientX - startX))
    n.y = Math.max(0, oy + (m.clientY - startY))
  }
  const up = () => {
    window.removeEventListener('mousemove', move)
    window.removeEventListener('mouseup', up)
    if (moved) pushHistory(`move ${n.id}`, { nodes: nodes.value, edges: edges.value })
  }
  window.addEventListener('mousemove', move)
  window.addEventListener('mouseup', up)
}

let _connectFrom = ref(null)
// ============== 节点配置 dialog (双击触发) — 从后端拉 schema ==============
const configVisible = ref(false)
const configNode = ref(null)
const configSchema = ref([])          // 当前节点的 schema.fields (从后端拉)
const configLoading = ref(false)
const configSuggestions = ref([])     // AI 智能建议
const suggestLoading = ref(false)
const configTitle = computed(() => configNode.value ? `配置: ${configNode.value.name} (${configNode.value.id})` : '')

// ★ 企业级: 检查字段是否填了 (必填项 + 异常后高亮)
const isFieldInvalid = (p) => {
  if (!configNode.value) return false
  // 仅当节点异常时, 红色框
  if (!configNode.value._failedReason) return false
  // 必填项 + 值为空 → 错
  if (p.required) {
    const v = configNode.value.params?.[p.key]
    if (v === undefined || v === null || v === '') return true
  }
  return false
}
const fieldErrorTip = (p) => {
  if (!configNode.value) return ''
  if (p.required) return '必填项, 节点上次异常可能是这个原因'
  return ''
}

// 打开双击节点 config: 调后端 schema
const openConfig = async (n) => {
  // 浅拷贝避免 selectedNode <-> configNode reactive 循环引用
  configNode.value = { ...n, params: { ...(n.params || {}) } }
  configVisible.value = true
  configSchema.value = []
  configSuggestions.value = []
  configLoading.value = true
  try {
    const r = await workflowApi.getComponentSchema(n.type || n.id)
    if (r.code === 200 && r.data) {
      configSchema.value = r.data.fields || []
    } else {
      // 兌底: 走本地 paramSchema
      configSchema.value = paramSchema(n)
    }
  } catch (e) {
    addLog('schema', `拉取失败, 用本地兌底: ${e.message}`, 'error')
    configSchema.value = paramSchema(n)
  } finally {
    configLoading.value = false
  }
}

// 滚动到某个节点 (让用户看到高亮 + 配置弹窗)
const scrollToNode = (id) => {
  nextTick(() => {
    const el = document.querySelector(`[data-node-id="${id}"]`)
    if (el && el.scrollIntoView) {
      el.scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'center' })
    }
  })
}

// AI 智能建议: 调后端 /suggest
const askAI = async () => {
  if (!configNode.value) return
  suggestLoading.value = true
  try {
    const r = await workflowApi.suggestComponentParams(configNode.value.type || configNode.value.id, {
      ...configNode.value.params
    })
    if (r.code === 200 && r.data) {
      configSuggestions.value = r.data.suggestions || []
      ElMessage.success(`已获得 ${configSuggestions.value.length} 项 AI 建议`)
    }
  } catch (e) {
    ElMessage.error(`AI 建议拉取失败: ${e.message}`)
  } finally {
    suggestLoading.value = false
  }
}

// 应用单条 AI 建议
const applySuggestion = (s) => {
  if (!configNode.value) return
  if (s.recommended === undefined || s.recommended === null) return
  // 尝试转类型 (string / number)
  const num = Number(s.recommended)
  configNode.value.params[s.key] = !isNaN(num) && s.recommended !== '' ? num : s.recommended
  ElMessage.success(`已应用: ${s.key} = ${s.recommended}`)
}

// 应用全部 AI 建议
const applyAllSuggestions = () => {
  configSuggestions.value.forEach(applySuggestion)
  ElMessage.success(`已应用全部 ${configSuggestions.value.length} 项建议`)
}

const cancelConfig = () => {
  // ★ v3.x 重构: 用闭包参数保留点选快照, 避免 nextTick 异步清理导致中间状态被快速开关 dialog 污染
  // 取消不需回滚原节点 (openConfig 已浅拷贝, 原 nodes 未被动过), 只需关闭 dialog + 清零状态
  configVisible.value = false
  addLog('配置', '取消修改', 'info')
  // 立即同步清, 不走 nextTick: 避免用户连续开关 dialog 时状态残留
  configNode.value = null
  configSuggestions.value = []
  configSchema.value = []
  configLoading.value = false
}

/**
 * ★ v3.x 单独处理 dialog 关闭事件 (无论是 cancel/save/ESC/X 都统一走这里)
 * 避免 v-model 双绑跟 @close 回调循环触发
 */
const onConfigDialogClose = () => {
  // 弹窗关闭后统一清零 (不区分 cancel/save, 在 saveConfig/cancelConfig 里已提前清)
  configNode.value = null
  configSuggestions.value = []
  configSchema.value = []
}

const saveConfig = () => {
  if (!configNode.value) return
  // ★ v3.x 写入前重新拉原节点 (以防 ids 已被删/重命名)
  const orig = nodes.value.find(n => n.id === configNode.value.id)
  if (!orig) {
    ElMessage.warning(`节点 ${configNode.value.id} 已不存在, 取消保存`)
    configVisible.value = false
    configNode.value = null
    return
  }
  // 深拷贝避免后续修改原对象动到原节点
  orig.params = JSON.parse(JSON.stringify(configNode.value.params || {}))
  pushHistory(`config ${configNode.value.id}`, { nodes: nodes.value, edges: edges.value })
  addLog('配置', `已更新 ${orig.name} (${orig.id}) 参数`, 'success')
  ElMessage.success(`${orig.name} 参数已保存`)
  // 先清 state 再关闭 dialog, 避免动画中报错
  configNode.value = null
  configSuggestions.value = []
  configSchema.value = []
  configVisible.value = false
}

// 边自检: 检测重复边 / 自连 / 输入输出同向
const edgeConflict = (fromId, fromPort, toId, toPort) => {
  if (fromId === toId) return '不能连接自己'
  if (!fromId || !toId) return null
  if (edges.value.some(e => e.from === fromId && e.to === toId)) return '重复连线'
  return null
}

const onPortMouseDown = (e, node, port, dir) => {
  if (!_connectFrom.value) {
    _connectFrom.value = { id: node.id, port, dir }
    addLog('连线', `→ 起点: ${node.name} (${dir} 端口)`, 'info')
    return
  }
  const from = _connectFrom.value
  const to = { id: node.id, port, dir }

  // 同节点且同方向 = 取消并重选起点
  if (from.id === to.id && from.dir === to.dir) {
    _connectFrom.value = { id: node.id, port, dir }
    addLog('连线', `→ 重新选起点: ${node.name} (${dir} 端口)`, 'info')
    return
  }

  // 决定方向: out  → in
  let a, b  // a=from, b=to
  if (from.dir === 'out' && to.dir === 'in') {
    a = from; b = to
  } else if (from.dir === 'in' && to.dir === 'out') {
    a = to; b = from
  } else {
    // 同方向 (out-out 或 in-in)  - 重选
    _connectFrom.value = { id: node.id, port, dir }
    addLog('连线', `→ 重选起点: ${node.name} (${dir} 端口)`, 'info')
    return
  }

  if (a.id === b.id) {
    ElMessage.warning('不能连接自己')
    _connectFrom.value = null
    return
  }
  const conflict = edgeConflict(a.id, a.port, b.id, b.port)
  if (conflict) {
    ElMessage.warning(conflict)
    addLog('连线', `✗ 拒绝: ${conflict}`, 'error')
    _connectFrom.value = null
    return
  }
  // 检查逆向已存在: B→A 已存在, 拒绝 A→B
  if (edges.value.some(e => e.from === b.id && e.to === a.id)) {
    ElMessage.warning('反向连线已存在, 不能双向连接')
    _connectFrom.value = null
    return
  }

  edges.value.push({ from: a.id, fromPort: a.port, to: b.id, toPort: b.port })
  pushHistory('connect', { nodes: nodes.value, edges: edges.value })
  addLog('连线', `✓ ${a.id} → ${b.id}`, 'success')
  ElMessage.success('已连线: ' + (nodes.value.find(n => n.id === a.id)?.name || a.id) + ' → ' + (nodes.value.find(n => n.id === b.id)?.name || b.id))
  _connectFrom.value = null
}

let _selectionRect = null
let _canvasEl = null
const onCanvasMouseDown = (e) => {
  if (e.target.classList && (e.target.classList.contains('canvas') || e.target.tagName === 'svg' || e.target.classList.contains('canvas-empty'))) {
    _canvasEl = e.currentTarget
    const rect = _canvasEl.getBoundingClientRect()
    _selectionRect = { x1: e.clientX - rect.left, y1: e.clientY - rect.top, x2: e.clientX - rect.left, y2: e.clientY - rect.top }
    selectedIds.value = []
    const onMove = (m) => {
      const r = _canvasEl.getBoundingClientRect()
      _selectionRect.x2 = m.clientX - r.left
      _selectionRect.y2 = m.clientY - r.top
    }
    const onUp = () => {
      const r = _selectionRect
      if (r) {
        const x1 = Math.min(r.x1, r.x2), y1 = Math.min(r.y1, r.y2)
        const x2 = Math.max(r.x1, r.x2), y2 = Math.max(r.y1, r.y2)
        selectedIds.value = nodes.value
          .filter(n => n.x >= x1 - 200 && n.x <= x2 + 200 && n.y >= y1 - 80 && n.y <= y2 + 80)
          .map(n => n.id)
      }
      _selectionRect = null
      _canvasEl = null
      window.removeEventListener('mousemove', onMove)
      window.removeEventListener('mouseup', onUp)
    }
    window.addEventListener('mousemove', onMove)
    window.addEventListener('mouseup', onUp)
  }
}

// ============== 边 ==============
// 节点常量 (跟 CSS .wf-node 一致, 保持同步)
const NODE_W_DEFAULT = 140
const NODE_H = 32
const PORT_R = 6      // 端口半径 10/2 = 5, 加 1 圈描边
const PORT_OFFSET = 5 // 端口突出节点边的像素

// 算某节点某方向端口的画布坐标
const portCoord = (node, dir, portName) => {
  const w = node._renderW || NODE_W_DEFAULT
  if (dir === 'in') {
    // 输入端口在节点左边中点 (CSS: top: 50%, transform: translateY(-50%))
    return { x: node.x - PORT_OFFSET, y: node.y + NODE_H / 2 }
  }
  if (dir === 'out') {
    // 输出端口 y 根据 outPorts 序号动态算 (CSS: top: ((index+1) * 14) + 'px')
    // 第 1 个 (默认): 14, 第 2 个: 28, 第 3 个: 42 ...
    const outPorts = node.outPorts && node.outPorts.length ? node.outPorts : ['out']
    const idx = portName ? outPorts.indexOf(portName) : 0
    const portIndex = idx < 0 ? 0 : idx
    const portTop = (portIndex + 1) * 14  // CSS: top: ((index+1) * 14)px
    // 端口高度 8px, 要让 y 对准端口中心
    const portCenterY = node.y + portTop + 4  // 端口 center = top + height/2
    return { x: node.x + w + PORT_OFFSET, y: portCenterY }
  }
  return { x: node.x, y: node.y }
}

// 边的颜色: 选中> 环> 默认
const edgeColor = (i) => {
  if (selectedEdge.value === i) return '#f59e0b'  // 选中橙
  if (!validation.value.valid) return '#ef4444'    // 环 红
  return '#6366f1'                                // 默认紫蓝
}

// 计算路径: 同行用 bezier, 跨行用正交折线 (L型 + 圆角)
const edgePath = (e) => {
  // 读 renderVersion 驱动重算 (节点尺寸变化时主动重算)
  void renderVersion.value
  const a = findNode(e.from)
  const b = findNode(e.to)
  if (!a || !b) return ''
  const p1 = portCoord(a, 'out', e.fromPort)
  const p2 = portCoord(b, 'in', e.toPort)

  // 判断同行 (y 差小于 6px)
  const sameRow = Math.abs(p1.y - p2.y) < 6
  if (sameRow) {
    // 同行: Bezier 曲线 (平滑)
    const dx = Math.max(40, (p2.x - p1.x) / 2)
    return `M ${p1.x} ${p1.y} C ${p1.x + dx} ${p1.y}, ${p2.x - dx} ${p2.y}, ${p2.x} ${p2.y}`
  }

  // 跨行: 正交折线 L 型 (出 → 横 → 纵 → 入)
  // 起点向右走 30px, 中点到两边中点水平对齐, 转弯走 圆角 (R=8)
  const R = 8  // 圆角半径
  const dx1 = 30  // 出节点走 30px
  const midX = (p1.x + p2.x) / 2
  // 出端
  const x1 = p1.x
  const y1 = p1.y
  const x2 = x1 + dx1
  const y2 = y1
  // 转角
  const x3 = midX
  const y3 = y2
  const x4 = midX
  const y4 = p2.y
  // 入端
  const x5 = p2.x
  const y5 = p2.y

  // L 型路径 + 圆角
  // 从 (x1,y1) 走到 (x2,y2), 转弯 (R), 走到 (x4,y4), 转弯 (R), 走到 (x5,y5)
  return [
    `M ${x1} ${y1}`,
    `L ${x3 - R} ${y2}`,  // 走到转角前
    `Q ${x3} ${y2} ${x3} ${y2 + Math.sign(y4 - y2) * R}`,  // 转弯 1
    `L ${x4} ${y4 - Math.sign(y4 - y2) * R}`,  // 走到入端转角前
    `Q ${x4} ${y4} ${x4 + Math.sign(x5 - x4) * R} ${y4}`,  // 转弯 2
    `L ${x5} ${y5}`  // 走到入点
  ].join(' ')
}

// ============== 模板 (调后端 /api/workflow/templates) ==============
const showGuide = ref(false)
const aiGenOpen = ref(false)
// AI 生成后填充到画布
const onAiApply = (data) => {
  if (!data || !data.nodes || !data.nodes.length) {
    ElMessage.warning('没有可填充的节点')
    return
  }
  // 清空画布后重新填充
  nodes.value = data.nodes.map(n => ({
    id: n.id,
    type: n.type,
    name: n.name,
    x: n.x,
    y: n.y,
    params: n.params || {}
  }))
  edges.value = (data.edges || []).map((e, i) => ({
    id: 'e' + (i + 1),
    from: e.from,
    to: e.to
  }))
  // 画布名称 + 描述
  if (data.name) {
    specName.value = data.name
  }
  if (data.description) {
    specDesc.value = data.description
  }
  selectedNode.value = null  // computed, 这行无效 (下面重置数组)
  selectedIds.value = []
  selectedEdge.value = null
  configNode.value = null
  configVisible.value = false
  // 强制刷新 (新增/删除节点)
  nextTick(() => { /* trigger re-render */ })
  ElMessage.success(`已填充 ${data.nodes.length} 个节点 / ${data.edges.length} 条边 (场景: ${data.scenario})`)
}

const loadTemplate = async (name) => {
  try {
    const r = await workflowApi.template()
    if (r.code !== 200) throw new Error(r.message)
    const tpl = r.data
    nodes.value = tpl.nodes.map((n, i) => ({ ...n, x: 60 + (i % 4) * 240, y: 60 + Math.floor(i / 4) * 160 }))
    edges.value = tpl.edges || []
    specName.value = tpl.name || '训练-评估-部署 模板'
    addLog('模板', `加载: ${specName.value}`, 'success')
    bus.emit('wf:event', { text: `已加载模板: ${specName.value}` })
  } catch (e) {
    addLog('模板', `加载失败, 使用本地兜底: ${e.message}`, 'error')
    // 兜底: 本地 RAG 三节点
    nodes.value = []
    edges.value = []
    addNode(palette[4].nodes[0], 60, 60)  // kb_ingest
    addNode(palette[4].nodes[1], 320, 60) // kb_search
    addNode(palette[4].nodes[0], 60, 220) // agent_think
    edges.value = [
      { from: nodes.value[0].id, fromPort: 'out', to: nodes.value[1].id, toPort: 'in' },
      { from: nodes.value[1].id, fromPort: 'out', to: nodes.value[2].id, toPort: 'in' }
    ]
    specName.value = 'RAG 检索增强 (本地兜底)'
  }
}

// ============== 保存 / 加载 ==============
const specList = ref([])
const loadingSpec = ref(false)
const loadSpecList = async () => {
  loadingSpec.value = true
  try {
    const r = await workflowApi.listSpecs()
    if (r.code === 200) specList.value = r.data || []
    else addLog('列表', `拉取失败: ${r.message}`, 'error')
  } catch (e) {
    addLog('列表', `拉取失败: ${e.message}`, 'error')
  } finally {
    loadingSpec.value = false
  }
}

const save = async () => {
  saving.value = true
  try {
    // ★ 企业级: 保存全部节点 (含 x,y) + edges + viewport + _renderW
    // 后端吃任意 JSON, 不强制 schema, 全部原样存 spec_json
    const body = {
      name: specName.value || '(未命名)',
      description: specDesc.value || '',
      nodes: nodes.value.map(n => ({
        id: n.id,
        type: n.type,
        name: n.name,
        x: n.x || 0,
        y: n.y || 0,
        params: n.params || {}
      })),
      edges: edges.value,
      viewport: { zoom: zoom.value || 1, pan: pan.value || { x: 0, y: 0 } }
    }
    const r = await workflowApi.saveSpec(body)
    if (r.code === 200) {
      ElMessage.success(`已保存: ${specName.value}`)
      addLog('保存', `✓ ${specName.value} (id=${r.data?.id})`, 'success')
      bus.emit('wf:event', { text: `工作流已保存: ${specName.value}` })
      await loadSpecList()
    } else {
      ElMessage.error(`保存失败: ${r.message}`)
    }
  } catch (e) {
    console.error('[workflow] 保存失败:', e)
    ElMessage.error(`保存失败: ${e?.response?.data?.message || e?.message || '网络错误'}`)
  } finally {
    saving.value = false
  }
}

// 导出为本地 JSON 文件 (流程不合法时按钮 disabled, 不走到这里)
const exportSpec = () => {
  const body = {
    name: specName.value,
    version: 1,
    exportedAt: new Date().toISOString(),
    platform: 'ai-platform-workflow',
    minRuntimeVersion: '2.0',
    nodes: nodes.value.map(({ x, y, ...rest }) => rest),
    edges: edges.value
  }
  const blob = new Blob([JSON.stringify(body, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${specName.value || 'workflow'}.json`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
  addLog('导出', `✓ 已下载: ${a.download}`, 'success')
  ElMessage.success(`已导出: ${a.download}`)
}

// 导入流程 (跨平台 JSON)
const fileInput = ref(null)
const triggerImport = () => {
  if (fileInput.value) {
    fileInput.value.value = ''
    fileInput.value.click()
  }
}
const onFileSelected = async (e) => {
  const file = e.target.files?.[0]
  if (!file) return
  try {
    const text = await file.text()
    const data = JSON.parse(text)
    if (!data || !Array.isArray(data.nodes) || !Array.isArray(data.edges)) {
      ElMessage.error('文件格式不对, 需包含 nodes + edges')
      return
    }
    // 平台兼容性检查
    if (data.platform && data.platform !== 'ai-platform-workflow') {
      ElMessage.warning(`文件来自其它平台 (${data.platform}), 尽力加载`)
    }
    pushHistory('import', { nodes: nodes.value, edges: edges.value })
    // 重生 id 避免冲突
    let idCounter = nodes.value.length
    nodes.value = data.nodes.map(n => ({
      id: n.id || `n${++idCounter}`,
      type: n.type,
      name: n.name,
      x: n.x ?? (60 + (idCounter % 4) * 240),
      y: n.y ?? (60 + Math.floor(idCounter / 4) * 160),
      params: n.params || {},
      outPorts: n.outPorts || ['out']
    }))
    edges.value = data.edges
    specName.value = data.name || file.name.replace(/\.json$/, '')
    selectedIds.value = []
    addLog('导入', `✓ 从 ${file.name} 加载 ${nodes.value.length} 节点 / ${edges.value.length} 边`, 'success')
    ElMessage.success(`已导入: ${nodes.value.length} 节点 / ${edges.value.length} 边`)
  } catch (err) {
    ElMessage.error('导入失败: ' + err.message)
  }
}

const loadSpec = async (s) => {
  try {
    const r = await workflowApi.getSpec(s.id)
    if (r.code === 200) {
      const data = r.data
      specName.value = data.name || '(未命名)'
      specDesc.value = data.description || ''
      // ★ 企业级: 后端返回完整 spec (含 nodes+edges+x+y), 原样恢复
      nodes.value = (data.nodes || []).map((n, i) => ({
        id: n.id,
        type: n.type,
        name: n.name,
        x: typeof n.x === 'number' ? n.x : (60 + (i % 4) * 240),
        y: typeof n.y === 'number' ? n.y : (60 + Math.floor(i / 4) * 160),
        params: n.params || {},
        _failedReason: null  // ★ 从 DB 加载后清空, 上次运行的失败原因不适用
      }))
      edges.value = (data.edges || []).map(e => ({
        from: e.from,
        to: e.to,
        fromPort: e.fromPort || 'out',
        toPort: e.toPort || 'in'
      }))
      ElMessage.success(`已加载: ${specName.value} (${nodes.value.length}节点/${edges.value.length}边)`)
      addLog('加载', `✓ ${specName.value} (${nodes.value.length}节点)`, 'success')
      // 取消选中 + 清失败
      selectedIds.value = []
      failedIds.value = new Set()
      doneSet.value = new Set()
      // 下一帧重测所有节点宽度 (因为 _renderW 不存 DB, 重画后会自动重设)
      nextTick(() => {
        nodes.value.forEach(n => {
          const el = nodeMap.value.get(n.id)
          if (el) n._renderW = el.offsetWidth
        })
        renderVersion.value++  // 驱动 edgePath 重算
      })
    } else {
      ElMessage.error(`加载失败: ${r.message}`)
    }
  } catch (e) {
    console.error('[workflow] 加载失败:', e)
    ElMessage.error(`加载失败: ${e?.response?.data?.message || e?.message || '网络错误'}`)
  }
}

const removeSpec = async (id) => {
  try {
    await ElMessageBox.confirm('确认删除此工作流?', '提示', { type: 'warning' })
    const r = await workflowApi.removeSpec(id)
    if (r.code === 200) {
      ElMessage.success('已删除')
      await loadSpecList()
    }
  } catch (e) { /* cancel */ }
}

// ============== 运行 ==============

const topoOrder = () => {
  // Kahn 算法: 返回 { order, cycle, orphans }
  //   order: 拓扑顺序里的节点 id
  //   cycle:  不能加入拓扑序的节点 id (说明有环 / 受环影响)
  //   orphans: 不连通 / 无依赖的节点 id
  const inDeg = {}
  const adj = {}
  for (const n of nodes.value) { inDeg[n.id] = 0; adj[n.id] = [] }
  for (const e of edges.value) {
    if (adj[e.from]) adj[e.from].push(e.to)
    if (inDeg[e.to] != null) inDeg[e.to]++
  }
  // 起点: 入度为 0 的节点
  const starts = nodes.value.filter(n => inDeg[n.id] === 0).map(n => n.id)
  const q = [...starts]
  const order = []
  while (q.length) {
    const id = q.shift()
    order.push(id)
    for (const next of (adj[id] || [])) {
      inDeg[next]--
      if (inDeg[next] === 0) q.push(next)
    }
  }
  const allIds = nodes.value.map(n => n.id)
  const inOrder = new Set(order)
  const cycle = allIds.filter(id => !inOrder.has(id))
  return { order, cycle, starts, orphans: [] }
}

// 当前流水线是否合法 (没环) — 先定义, 供 canvasContext 引用
const validation = computed(() => {
  const v = topoOrder()
  const valid = v.cycle.length === 0 && v.order.length > 0
  const reason = v.cycle.length > 0
    ? `检测到环或闭环依赖: ${v.cycle.join(', ')}`
    : (v.order.length === 0 ? '画布为空' : '流程合法')
  return { valid, reason, ...v }
})

// 画布上下文 -> 推送全局 AI 助手 (跨页面统一使用全局实例)
const canvasContext = computed(() => ({
  nodeCount: nodes.value.length,
  edgeCount: edges.value.length,
  cycleCount: validation.value.cycle.length,
  valid: validation.value.valid
}))

// 变化时推送到全局 (全局 Assistant 订阅 'assistant:context')
watchEffect(() => {
  bus.emit('assistant:context', { type: 'canvas', data: canvasContext.value })
})

// ============== 放大画布 (仅 CSS transform, 不动节点数据) ==============
const zoomOpen = ref(false)
const zoom = ref(1)
const panX = ref(0)
const panY = ref(0)
const pan = computed(() => ({ x: panX.value, y: panY.value }))
const zoomViewport = ref(null)
const ZOOM_MIN = 0.2
const ZOOM_MAX = 4
const ZOOM_STEP = 1.2
const zoomIn = () => { zoom.value = Math.min(ZOOM_MAX, +(zoom.value * ZOOM_STEP).toFixed(2)) }
const zoomOut = () => { zoom.value = Math.max(ZOOM_MIN, +(zoom.value / ZOOM_STEP).toFixed(2)) }
const zoomReset = () => { zoom.value = 1; panX.value = 0; panY.value = 0 }
const panReset = zoomReset
const onZoomWheel = (e) => {
  if (e.ctrlKey || e.metaKey) {
    e.preventDefault()
    if (e.deltaY < 0) zoomIn(); else zoomOut()
  } else {
    // 普通滚轮: 上下平移
    panY.value -= e.deltaY
  }
}
let _panStart = null
const onZoomPanStart = (e) => {
  if (e.button !== 0) return
  // 只在点击空白处启动拖动 (非节点 / 非按钮)
  if (e.target.classList && (e.target.classList.contains('zoom-stage') || e.target.classList.contains('zoom-viewport') || e.target.tagName === 'svg')) {
    _panStart = { x: e.clientX - panX.value, y: e.clientY - panY.value }
    const onMove = (m) => {
      panX.value = m.clientX - _panStart.x
      panY.value = m.clientY - _panStart.y
    }
    const onUp = () => {
      window.removeEventListener('mousemove', onMove)
      window.removeEventListener('mouseup', onUp)
      _panStart = null
    }
    window.addEventListener('mousemove', onMove)
    window.addEventListener('mouseup', onUp)
  }
}

// 打开弹窗时重置视图
watch(zoomOpen, (v) => { if (v) zoomReset() })

// Ctrl+0 还原
const onZoomKey = (e) => {
  if (!zoomOpen.value) return
  if (e.ctrlKey && e.key === '0') { e.preventDefault(); zoomReset() }
  else if (e.key === '+' || e.key === '=') { e.preventDefault(); zoomIn() }
  else if (e.key === '-') { e.preventDefault(); zoomOut() }
  else if (e.key === 'Escape') { zoomOpen.value = false }
}


const onKey = (e) => {
  if (e.key === 'z' && (e.metaKey || e.ctrlKey) && !e.shiftKey) { e.preventDefault(); undo() }
  else if ((e.key === 'y' && (e.metaKey || e.ctrlKey)) || (e.key === 'Z' && e.shiftKey && (e.metaKey || e.ctrlKey))) { e.preventDefault(); redo() }
  else if (e.key === 'Delete' || e.key === 'Backspace') {
    if (selectedEdge.value != null) {
      e.preventDefault()
      removeEdge(selectedEdge.value)
    } else if (selectedIds.value.length) {
      e.preventDefault()
      selectedIds.value.forEach(removeNode)
    }
  } else if (e.key === 'Escape') {
    selectedIds.value = []
    selectedEdge.value = null
    if (_connectFrom.value) {
      _connectFrom.value = null
      addLog('连线', '✗ 取消连线 (ESC)', 'info')
    }
  }
}

// ============== 生命周期 ==============
onMounted(() => {
  window.addEventListener('keydown', onKey)
  window.addEventListener('keydown', onZoomKey)
  loadSpecList()
  bus.emit('wf:event', { text: '工作流编辑器就绪' })
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKey)
  window.removeEventListener('keydown', onZoomKey)
  if (resizeObserver) { resizeObserver.disconnect(); resizeObserver = null }
})
</script>

<style scoped>
.workflow-page { display: flex; flex-direction: column; height: calc(100vh - 110px); padding: 8px; }
.wf-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 6px 12px; background: var(--bg-top, #fff); border: 1px solid var(--border, #e5e7eb); border-radius: 8px; margin-bottom: 8px; }
.wf-toolbar h2 { margin: 0; font-size: 16px; }
.tb-left, .tb-right { display: flex; align-items: center; gap: 8px; }

.wf-grid { display: grid; grid-template-columns: 180px 1fr 280px; gap: 8px; flex: 1; min-height: 600px; }

/* 左: 节点库 */
.palette { background: var(--bg-top, #fff); border: 1px solid var(--border, #e5e7eb); border-radius: 8px; padding: 8px; overflow-y: auto; }
.palette h4 { margin: 0 0 6px; font-size: 11px; color: #94a3b8; text-transform: uppercase; letter-spacing: 1px; }
.pal-group { margin-bottom: 8px; }
.pal-group-hd { display: flex; align-items: center; gap: 4px; margin-bottom: 4px; }
.pal-ico { width: 18px; height: 18px; border-radius: 4px; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 10px; }
.pal-node { display: flex; align-items: center; gap: 6px; padding: 5px 6px; margin-bottom: 3px; border-radius: 5px; cursor: grab; transition: all 0.15s; font-size: 11px; }
.pal-node:hover { background: rgba(99, 102, 241, 0.08); transform: translateX(2px); }
.pal-meta { flex: 1; min-width: 0; }
.pal-name { font-weight: 600; }
.pal-desc { color: #94a3b8; font-size: 10px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

/* 中: 画布 */
.canvas { position: relative; background: var(--bg-top, #fff); border: 1px solid var(--border, #e5e7eb); border-radius: 8px; overflow: hidden; min-height: 600px; }
.canvas-invalid { border: 2px solid #ef4444; box-shadow: 0 0 0 4px rgba(239, 68, 68, 0.15); }

.canvas-status { position: absolute; top: 0; left: 0; right: 0; z-index: 10; display: flex; align-items: center; gap: 6px; padding: 6px 12px; font-size: 12px; font-weight: 500; }
.canvas-status.ok { background: linear-gradient(90deg, #ecfdf5, #f0fdf4); color: #047857; border-bottom: 1px solid #a7f3d0; }
.canvas-status.err { background: linear-gradient(90deg, #fef2f2, #fee2e2); color: #b91c1c; border-bottom: 1px solid #fca5a5; }
.canvas-empty { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 6px; pointer-events: none; }
.canvas-empty > * { pointer-events: auto; }

/* 放大画布弹窗 */
::v-deep(.zoom-dialog .el-dialog__body) { padding: 0; height: calc(100vh - 110px); }
.zoom-toolbar { display: flex; align-items: center; gap: 12px; padding: 8px 12px; background: var(--bg-top, #fff); border-bottom: 1px solid var(--border, #e5e7eb); position: absolute; top: 0; left: 0; right: 0; z-index: 10; }
.zoom-viewport { position: absolute; top: 42px; left: 0; right: 0; bottom: 0; overflow: hidden; background: #f8fafc; cursor: grab; }
.zoom-viewport:active { cursor: grabbing; }
.zoom-stage { position: absolute; width: 2000px; height: 1200px; }
.zoom-wires { position: absolute; pointer-events: none; }
.zoom-node { position: absolute; min-width: 120px; max-width: 180px; height: 32px; padding: 0 8px; display: flex; align-items: center; gap: 4px; background: #fff; border: 1.5px solid #e5e7eb; border-radius: 6px; box-shadow: 0 1px 4px -1px rgba(0,0,0,0.08); font-size: 11px; font-weight: 600; }
.zoom-node.is-start { border-color: #10b981; border-left-width: 4px; }
.zoom-node.is-end { border-color: #f43f5e; border-right-width: 4px; }
.zoom-node.is-cycle { border-color: #ef4444; border-width: 2px; background: #fef2f2; }
.zoom-node.is-failed { border-color: #dc2626; border-width: 2px; background: #fee2e2; box-shadow: 0 0 0 3px rgba(220, 38, 38, 0.25); animation: pulse-failed 1.5s infinite; }
.zoom-node.is-selected { outline: 3px solid #f59e0b; outline-offset: 2px; }
@keyframes pulse-failed { 0%, 100% { box-shadow: 0 0 0 3px rgba(220, 38, 38, 0.25); } 50% { box-shadow: 0 0 0 8px rgba(220, 38, 38, 0.08); } }
.zoom-node .el-icon { color: #6366f1; }
.zoom-hint { position: absolute; bottom: 12px; left: 50%; transform: translateX(-50%); background: rgba(255,255,255,0.9); padding: 4px 12px; border-radius: 16px; font-size: 11px; box-shadow: 0 1px 4px rgba(0,0,0,0.08); z-index: 5; }
.wires { position: absolute; inset: 0; width: 100%; height: 100%; pointer-events: none; }
.wires path.edge-cycle { stroke-dasharray: 6 4; animation: dash-flow 0.8s linear infinite; }
.wires path.edge-path:hover { stroke-width: 4; }
.wires path.edge-selected { stroke-width: 5 !important; stroke: #f59e0b !important; }
@keyframes dash-flow { to { stroke-dashoffset: -20; } }

.wf-node { position: absolute; min-width: 120px; max-width: 180px; height: 32px; padding: 0 4px; display: flex; align-items: center; gap: 4px; background: #fff; border: 1.5px solid #e5e7eb; border-radius: 6px; box-shadow: 0 1px 4px -1px rgba(0,0,0,0.08); transition: all 0.15s; cursor: grab; font-size: 11px; }
.wf-node:hover { box-shadow: 0 3px 8px -1px rgba(0,0,0,0.12); border-color: #c7d2fe; }
.wf-node.running { border-color: #f59e0b; box-shadow: 0 0 0 2px rgba(245, 158, 11, 0.2); }
.wf-node.done { border-color: #10b981; }
.wf-node.failed { border-color: #dc2626 !important; background: #fee2e2 !important; box-shadow: 0 0 0 3px rgba(220, 38, 38, 0.3) !important; animation: pulse-failed 1.5s infinite; }
.wf-node.selected { border-color: #6366f1; box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.3); }
.wf-node.is-start { border-color: #10b981; border-left-width: 4px; }
.wf-node.is-end { border-color: #f43f5e; border-right-width: 4px; }
.wf-node.is-cycle { border-color: #ef4444; border-width: 2px; background: #fef2f2; animation: pulse-err 1.5s ease-in-out infinite; }
@keyframes pulse-err { 0%, 100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.4); } 50% { box-shadow: 0 0 0 4px rgba(239, 68, 68, 0.2); } }
.wn-ico { color: #6366f1; flex-shrink: 0; }
.wn-name { font-weight: 600; flex: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.wn-help { color: #94a3b8; cursor: help; flex-shrink: 0; font-size: 12px; padding: 2px; border-radius: 3px; transition: all 0.15s; }
.wn-help:hover { color: #6366f1; background: rgba(99, 102, 241, 0.1); }
.wn-del { color: #cbd5e1; cursor: pointer; flex-shrink: 0; font-size: 12px; padding: 2px; border-radius: 3px; transition: all 0.15s; }
.wn-del:hover { color: #ef4444; background: rgba(239, 68, 68, 0.1); }
.tip-params strong { display: block; font-size: 10px; color: #94a3b8; margin: 4px 0 2px; }
.tip-params code { display: inline-block; background: #f1f5f9; padding: 1px 4px; border-radius: 3px; font-size: 10px; margin: 1px 2px 1px 0; }
.wn-cfg-row { display: flex; align-items: center; gap: 4px; font-size: 10px; }
.wn-cfg-row label { color: #94a3b8; flex-shrink: 0; min-width: 40px; }
.wn-cfg-row code { background: #f1f5f9; padding: 0 4px; border-radius: 3px; }
.wn-port { position: absolute; width: 10px; height: 10px; border-radius: 50%; background: #6366f1; cursor: crosshair; border: 2px solid #fff; box-shadow: 0 1px 3px rgba(0,0,0,0.2); transition: transform 0.15s, background 0.15s; z-index: 5; }
.wn-port:hover { transform: scale(1.4); background: #4f46e5; }
.wn-port-out { right: -5px; }
.wn-port-in { left: -5px; background: #10b981; }
.wn-port-in:hover { background: #059669; }
/* 连线中点亮的样式 */
.wn-connecting { animation: port-pulse 1s infinite; }
@keyframes port-pulse { 0%, 100% { box-shadow: 0 0 0 0 rgba(99, 102, 241, 0.5); } 50% { box-shadow: 0 0 0 6px rgba(99, 102, 241, 0); } }

/* 右: 配置 */
.inspector { background: var(--bg-top, #fff); border: 1px solid var(--border, #e5e7eb); border-radius: 8px; padding: 8px; overflow-y: auto; }
.inspector h4 { margin: 0 0 8px; font-size: 13px; }
.empty-tip { color: #94a3b8; text-align: center; padding: 24px 0; font-size: 12px; }
.run-info { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; padding-bottom: 6px; border-bottom: 1px solid #f0f0f0; }
.log-line { display: flex; gap: 6px; padding: 3px 4px; font-family: 'Fira Code', monospace; font-size: 11px; border-bottom: 1px solid #f8fafc; }
.log-line.error { background: #fef2f2; }
.log-line.success { background: #f0fdf4; }
.log-ts { color: #94a3b8; flex-shrink: 0; }
.log-tag { color: #6366f1; flex-shrink: 0; }
.log-msg { color: #1e293b; flex: 1; word-break: break-all; }
.spec-row { display: flex; align-items: center; justify-content: space-between; padding: 6px 4px; border-bottom: 1px solid #f0f0f0; }
.spec-name { font-weight: 500; font-size: 12px; }

/* 节点配置 dialog 字段提示 */
.field-tip { font-size: 10px; color: #94a3b8; margin-top: 2px; line-height: 1.4; }
.suggestion-card { margin-bottom: 6px; padding: 6px 8px !important; }
.suggestion-card .sug-head { display: flex; align-items: center; justify-content: space-between; }
.suggestion-card .sug-row { display: flex; align-items: center; gap: 4px; font-size: 11px; margin-top: 2px; }
.suggestion-card .lbl { color: #94a3b8; min-width: 36px; }
.suggestion-card code { background: #f1f5f9; padding: 1px 4px; border-radius: 3px; font-size: 11px; }
.suggestion-card code.rec { background: #ecfdf5; color: #047857; }
.suggestion-card .sug-reason { font-size: 10px; color: #6366f1; margin-top: 3px; }

/* 抽屉说明 */
.guide h3 { margin: 16px 0 6px; font-size: 14px; color: #6366f1; }
.guide h3:first-child { margin-top: 0; }
.guide p, .guide li { font-size: 12px; line-height: 1.6; color: #475569; }
.guide code { background: #f1f5f9; padding: 1px 5px; border-radius: 3px; font-size: 11px; }
.guide kbd { background: #f1f5f9; border: 1px solid #cbd5e1; border-radius: 3px; padding: 1px 5px; font-size: 10px; font-family: monospace; }
.guide ul, .guide ol { padding-left: 20px; }
.muted { color: #94a3b8; }
.small { font-size: 11px; }
</style>
