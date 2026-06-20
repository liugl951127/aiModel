/**
 * 页面级知识库
 * 每个路由对应一组常见问题 + 答案 + 可执行 action
 *
 * 答案按 "类型|类型" 形式分多关键词匹配, 命中即用
 * Action 可绑定按钮, 一键跳页面 / 弹窗 / 触发事件
 */
export const pageKnowledge = {
  // ============ 工作台 ============
  '/dashboard': {
    name: '工作台',
    icon: '🏠',
    description: '系统总览, 业务指标 + AI 能力 + 快捷入口',
    quickQuestions: [
      '怎么看今天业务?',
      '4 大业务入口分别是?',
      '分布式开关在哪?',
      '知识库怎么进入?',
    ],
    qa: [
      {
        keywords: '业务|指标|统计|客户|订单|回款|对账',
        answer: '工作台展示 4 大核心指标: 客户数 / 商机金额 / 订单数 / 合同回款. 数据来自业务表 (biz_customer/biz_opportunity/biz_order/biz_payment), 实时同步.\n\n点击指标卡可下钻到对应业务列表页.',
        actions: [
          { label: '看客户', event: 'navigate', payload: '/customer' },
          { label: '看订单', event: 'navigate', payload: '/order' },
        ]
      },
      {
        keywords: '分布式|限流|幂等|锁|cache',
        answer: '分布式 7 大能力 (锁/ID/限流/幂等/缓存/事件/调度) 都基于 Redisson + Redis.\n\n具体演示页: /distributed (实时展示所有能力效果).',
        actions: [{ label: '打开分布式', event: 'navigate', payload: '/distributed' }]
      },
      {
        keywords: '知识库|kb|文档|rag',
        answer: '知识库 4 个动作: ① 文档上传 ② 自动切片 + 向量化 ③ 检索 TopK ④ 在 Agent 里引用.\n\n主页面 /knowledge (列表), 编辑器 /knowledge/manage (RAG 调试台).',
        actions: [{ label: '打开知识库', event: 'navigate', payload: '/knowledge' }]
      },
    ]
  },

  // ============ AI 能力 ============
  '/models': {
    name: '模型管理',
    icon: '🤖',
    description: '大语言模型注册与版本管理',
    quickQuestions: [
      '怎么注册一个新模型?',
      '支持哪些模型格式?',
      '本地模型和 API 模型区别?',
      '模型版本怎么升级?',
    ],
    qa: [
      {
        keywords: '注册|新增|添加|新建|create',
        answer: '注册模型 2 步: ① 点 [+ 新增模型] ② 填 4 个字段: 名称/类型 (LLM/Embedding/Rerank)/来源 (本地/API/ONNX)/端点 URL.\n\n本地模型放 /models/ 目录, 系统自动扫描; API 模型填 URL + Key.',
        actions: [{ label: '打开新增', event: 'pageAction', payload: { page: '/models', action: 'openCreate' } }]
      },
      {
        keywords: '格式|支持|类型|engine',
        answer: '支持 3 类: ① DJL (PyTorch/ONNX Runtime) ② HuggingFace Transformers ③ OpenAI 兼容 API (含 Azure/通义/智谱/文心).\n\n配置项里 type 字段决定后端调度.',
      },
      {
        keywords: '版本|升级|回滚|compare|ab',
        answer: '版本管理在 /model-versions 页: ① 点 [+ 新版本] 关联主模型 ② 填变更说明 ③ 点 [上线] 即生效. 旧版本仍可回滚, 支持 A/B 对比.\n\n提示: 上线前先在 staging 测试.',
        actions: [{ label: '打开版本', event: 'navigate', payload: '/model-versions' }]
      },
    ]
  },

  '/model-versions': {
    name: '模型版本',
    icon: '🧬',
    description: '版本时间线 + A/B 对比 + 上线/回滚',
    quickQuestions: [
      '怎么上线新版本?',
      'A/B 测试怎么做?',
      '回滚到上一版?',
      '版本号规则?',
    ],
    qa: [
      {
        keywords: '上线|发布|publish|灰度|deploy',
        answer: '上线流程: ① 在时间线点对应版本的 [→ 上线] ② 选择灰度比例 (10% / 50% / 100%) ③ 确认.\n\n系统自动切流量, 旧版本保留可回滚. 灰度期间看监控指标.',
        actions: [{ label: '新建版本', event: 'pageAction', payload: { page: '/model-versions', action: 'openCreate' } }]
      },
      {
        keywords: 'ab|对比|分流|灰度|compare',
        answer: 'A/B 配置: ① 进入 [A/B 测试] Tab ② 选 2 个版本 ③ 设分流比例 ④ [开始实验].\n\n系统用 50/50 哈希分流, 后台统计转化率/准确率/耗时差异.',
      },
      {
        keywords: '回滚|rollback|降级|revert',
        answer: '回滚 2 步: ① 在时间线找上一稳定版本 ② 点 [← 回滚到此版本].\n\n回滚后流量立即切回, 监控会自动对齐历史基线.',
      },
    ]
  },

  '/inference': {
    name: '推理服务',
    icon: '⚡',
    description: '在线推理 / 批量推理 / 健康检查',
    quickQuestions: [
      '健康检查怎么用?',
      '批量推理和在线区别?',
      'QPS 上限多少?',
      '推理慢怎么排查?',
    ],
    qa: [
      {
        keywords: '健康|health|心跳|probe|ready',
        answer: '健康检查 3 维度: ① /actuator/health (Spring Boot 标配) ② 模型加载状态 (是否已就绪) ③ GPU 显存 (低于 80% 才算健康).\n\n页面顶部有 [🔄 刷新] 按钮, 实时显示 3 项状态.',
        actions: [{ label: '立即检测', event: 'pageAction', payload: { page: '/inference', action: 'healthCheck' } }]
      },
      {
        keywords: '批量|batch|离线|offline',
        answer: '批量推理: 输入文件 (JSONL/CSV) → 后台 worker 池处理 → 输出到 OSS/本地.\n\n适合: 文档分类、批量嵌入、离线评估. 单次可处理 1 万条.',
        actions: [{ label: '打开批量', event: 'pageAction', payload: { page: '/inference', action: 'switchTab', payload: 'batch' } }]
      },
      {
        keywords: '慢|timeout|qps|性能|性能|优化|tps',
        answer: '推理慢 4 大原因: ① 显存不够 → 减 batch ② 模型没量化 → 启用 INT8 ③ 网络延迟 → 换内网 ④ 单 QPS 触顶 → 扩 worker.\n\n页面 [⚙ 配置] 里调 worker 数 (默认 4, 建议等于 CPU 核数).',
      },
    ]
  },

  '/chat': {
    name: 'AI 对话',
    icon: '💬',
    description: '多轮对话 / 会话历史 / 引用溯源',
    quickQuestions: [
      '怎么开启 RAG?',
      '会话怎么保存?',
      '引用来源怎么显示?',
      '上下文窗口多大?',
    ],
    qa: [
      {
        keywords: 'rag|检索|知识库|引用|source',
        answer: 'RAG 开启 2 步: ① 选模型 (必须支持 function call) ② 关联知识库 ID.\n\n回答时 AI 引用 [1][2] 角标, 点击可看原文 + 切片.',
        actions: [{ label: '管理知识库', event: 'navigate', payload: '/knowledge' }]
      },
      {
        keywords: '会话|历史|保存|多轮|context',
        answer: '会话默认保存 30 天 (后台 DB), 左侧栏可看历史.\n\n多轮上下文窗口: 默认 8K token, 长对话自动摘要压缩 (见 ConversationCompressor).',
      },
      {
        keywords: '来源|cite|溯源|reference',
        answer: '引用来源: 回答中 [1][2] 标号, 鼠标悬停看文档名 + 切片摘要, 点击跳转原文.\n\n溯源机制: 每次检索记录 topK 文档, 回答时强制带 [n] 引用.',
      },
    ]
  },

  // ============ 流程编排 ============
  '/workflow': {
    name: '流程编排',
    icon: '🔀',
    description: '32 节点拖拽编排 + AI 助手 + 沙盒运行',
    quickQuestions: [
      '怎么搭第一个流程?',
      'RAG 流程模板在哪?',
      '死循环怎么修?',
      '32 个节点怎么分类?',
    ],
    qa: [
      {
        keywords: '新手|开始|第一个|入门|上手',
        answer: '新手 3 步: ① 点 [🪄 AI 极速生成] 按钮 ② 一句话告诉 AI 你要什么 ③ AI 自动填画布 + 跑.\n\n背后真调本地 MiniGpt 模型, agent_think 节点会真生成 token.',
        actions: [
          { label: '🪄 一键生成 RAG 流程', event: 'generate', payload: '做一个 RAG 知识库问答流程' },
          { label: '加载 RAG 模板', event: 'pageAction', payload: { page: '/workflow', action: 'loadRag' } }
        ]
      },
      {
        keywords: 'rag|模板|template',
        answer: 'RAG 模板 3 节点流水线: kb_ingest (文档入库) → kb_search (检索) → agent_think (回答).\n\n关键参数: topK 3-5, chunkSize 256, overlap 32.\n\n所有这些节点背后都走真模型, 跑起来后 agent_think 会调本地 MiniGpt 真生成 token.',
        actions: [
          { label: '🪄 AI 生成 RAG', event: 'generate', payload: '做一个 RAG 知识库问答流程' },
          { label: '加载 RAG 模板', event: 'pageAction', payload: { page: '/workflow', action: 'loadRag' } }
        ]
      },
      {
        keywords: '死循环|循环|环|cycle|自连',
        answer: '死循环: A→B→A 闭环. 修复: 找反向箭头删掉, 确认是 DAG.\n\n系统会红色标出循环节点, 脉冲动画提示.',
      },
      {
        keywords: '32|分类|节点|node|组|group',
        answer: '32 节点分 8 组: 数据准备 (5) / 训练 (4) / 评估 (3) / 部署 (3) / Agent (4) / 知识库 (4) / 工具 (5) / 推理 (4).\n\n完整列表: 鼠标悬停节点卡的 ? 图标, 看节点说明.\n\n点击 [诊断] 按钮可让 AI 检查你的流程.',
        actions: [{ label: '🔍 AI 诊断当前流程', event: 'diagnose' }]
      },
      {
        keywords: '训练|llama|lora|微调|finetune',
        answer: '点击下方按钮, AI 会自动生成 LoRA 训练流程 (5 节点: 数据加载 → 切片 → 训练 → 评估 → 注册).\n\n背后会调 trainer 服务真跑训练任务.',
        actions: [{ label: '🪄 生成 LoRA 训练流程', event: 'generate', payload: '训练个 LoRA 模型, epochs=3' }]
      },
      {
        keywords: 'ai|参数|补全|建议|智能化',
        answer: 'AI 参数补全 3 种方式:\n1. 双击节点 → 配置弹窗 → [🤖 AI 补全参数] 按钮\n2. 选中节点 → 右键菜单 → [AI 建议]\n3. 在助手问 "推荐参数" → AI 给建议 + 一键应用\n\n所有 AI 调用都走真后端 /api/workflow/component-schemas/{id}/suggest.',
        actions: [{ label: '打开画布', event: 'navigate', payload: '/workflow' }]
      },
    ]
  },

  // ============ 知识库 ============
  '/knowledge': {
    name: '知识库',
    icon: '📚',
    description: '文档管理 / 切片 / 向量化 / 检索',
    quickQuestions: [
      '怎么上传文档?',
      '切片大小怎么设?',
      '向量模型选哪个?',
      '怎么调试 RAG?',
    ],
    qa: [
      {
        keywords: '上传|文档|文件|upload|pdf|docx',
        answer: '上传 2 步: ① 点 [+ 上传文档] 选文件 (PDF/Word/Markdown/TXT) ② 系统自动切片 + 向量化.\n\n单文件限 50MB, 批量限 100 个. 大文件用 ai-platform-files 服务的分片上传.',
        actions: [{ label: '打开编辑器', event: 'navigate', payload: '/knowledge/manage' }]
      },
      {
        keywords: '切片|chunk|分片|overlap',
        answer: '切片推荐: chunkSize 256 token, overlap 32 (12.5%). 太小 (128) 召回碎, 太大 (1024) 不准.\n\n中英文混排文档: 用 BGE 中文 512 维向量.',
      },
      {
        keywords: '向量|embedding|模型|bge|m3e',
        answer: '推荐 3 选 1:\n• BGE-zh (中文最优, 512 维)\n• M3E (轻量, 384 维)\n• OpenAI text-embedding-3-small (需 API Key)\n\n配置在 [/models] 页注册 embedding 模型.',
        actions: [{ label: '注册向量模型', event: 'navigate', payload: '/models' }]
      },
      {
        keywords: '调试|debug|rag|检索|测试',
        answer: '调试台: /knowledge/manage. 输入问题 → 看召回 topK 文档 → 看相似度分数 → 调 chunkSize / topK.\n\n提示: 召回率 < 70% 说明切片或模型有问题.',
        actions: [{ label: '打开调试台', event: 'navigate', payload: '/knowledge/manage' }]
      },
    ]
  },

  '/knowledge/manage': {
    name: 'RAG 调试台',
    icon: '🧪',
    description: '实时检索测试 / 召回率分析',
    quickQuestions: [
      '召回率怎么算?',
      '怎么调 topK?',
      '检索慢怎么排查?',
      '怎么导出测试结果?',
    ],
    qa: [
      {
        keywords: '召回|recall|命中率|测试集',
        answer: '召回率 = 命中标准答案数 / 总问题数. > 80% 算合格.\n\n调试台: 输入问题集 (JSONL), 系统自动算每条召回率 + 平均.',
      },
      {
        keywords: 'topk|top-k|个数|返回',
        answer: 'topK 经验值: 简单问答 3, 多步推理 5-8, 法律/医疗 10+.\n\n太大会引入噪音, 太小召回不够. 通常配合 reranker 一起用.',
      },
    ]
  },

  // ============ 业务模块 ============
  '/customer': {
    name: '客户管理',
    icon: '👥',
    description: '客户档案 + 联系记录 + 归属',
    quickQuestions: [
      '怎么录入客户?',
      '客户和商机关系?',
      '批量导入怎么搞?',
      '客户归属怎么改?',
    ],
    qa: [
      {
        keywords: '录入|新增|添加|create',
        answer: '新增客户: ① 点 [+ 新增客户] ② 填名称/行业/规模/联系人 ③ 保存.\n\n必填: 客户名称. 选填: 行业 (ICT/制造/金融/医疗/教育/其他).',
        actions: [{ label: '打开新增', event: 'pageAction', payload: { page: '/customer', action: 'openCreate' } }]
      },
      {
        keywords: '商机|opportunity|关联|关系',
        answer: '客户 → 商机: 一对多. 1 个客户可有多次商机 (不同产品/阶段).\n\n客户页 [商机 Tab] 看所有关联商机.',
      },
      {
        keywords: '批量|导入|excel|csv|import',
        answer: '批量导入: ① 点 [📥 导入] ② 下载模板 ③ 填好上传 ④ 预览确认 ⑤ 入库.\n\n模板字段: 名称|行业|规模|联系人|电话|邮箱|备注. 单次限 1000 行.',
      },
    ]
  },

  '/chat-biz': {
    name: '业务会话',
    icon: '🗨',
    description: '客服会话 / 工单 / 转接',
    quickQuestions: [
      '会话怎么转接?',
      '工单怎么生成?',
      '满意度怎么统计?',
      '会话怎么归档?',
    ],
    qa: [
      {
        keywords: '转接|transfer|分配|assign',
        answer: '转接 2 步: ① 会话页点 [↗ 转接] ② 选目标客服 / 部门.\n\n系统自动通知接手方, 客户无感.',
      },
      {
        keywords: '工单|ticket|创建|升级',
        answer: '工单 2 种来源: ① AI 自动识别意图 (退款/投诉) 自动建 ② 客服手动点 [📋 建工单].\n\n工单页: /ticket 看全部.',
      },
    ]
  },

  '/opportunity': {
    name: '商机管理',
    icon: '💡',
    description: '销售漏斗 / 阶段推进 / 赢率',
    quickQuestions: [
      '阶段怎么推进?',
      '赢率怎么算?',
      '怎么绑定产品?',
      '过期商机怎么提醒?',
    ],
    qa: [
      {
        keywords: '阶段|推进|stage|漏斗',
        answer: '5 阶段: 初步沟通 (10%) → 方案确认 (30%) → 商务谈判 (60%) → 签约中 (85%) → 赢单 (100%) / 输单 (0%).\n\n点阶段下拉切换, 系统自动算金额 × 阶段赢率 = 预期收入.',
      },
      {
        keywords: '赢率|win rate|概率|预测',
        answer: '赢率 = 历史该阶段实际赢单率. 系统每月自动回归.\n\n显示: 商机卡右下 [胜率 X%], 颜色: 红 < 30 / 黄 30-60 / 绿 > 60.',
      },
    ]
  },

  '/quote': {
    name: '报价单',
    icon: '📄',
    description: '报价模板 / 价格策略 / 审批流',
    quickQuestions: [
      '怎么创建报价单?',
      '价格策略怎么用?',
      '审批流怎么配?',
      '报价单能转订单吗?',
    ],
    qa: [
      {
        keywords: '创建|新增|create|模板',
        answer: '创建 2 步: ① 选商机 (带入客户+产品) ② 加产品行, 自动套用价格策略 ③ 保存.\n\n可保存为模板, 复用.',
        actions: [{ label: '打开新增', event: 'pageAction', payload: { page: '/quote', action: 'openCreate' } }]
      },
      {
        keywords: '价格|策略|折扣|price',
        answer: '价格策略 3 维度: ① 客户等级 (VIP 9 折) ② 数量阶梯 (100+ 8 折) ③ 促销 (限时 7 折).\n\n冲突时按优先级: 促销 > 阶梯 > 客户等级.',
      },
    ]
  },

  '/contract': {
    name: '合同管理',
    icon: '📜',
    description: '电子合同 / 审批 / 履约',
    quickQuestions: [
      '合同审批几级?',
      '电子签怎么集成?',
      '履约进度怎么看?',
      '合同快到期提醒?',
    ],
    qa: [
      {
        keywords: '审批|流程|approve|level',
        answer: '合同审批 3 级: 销售经理 → 法务 → 财务. 金额 > 50 万加 CEO 终审.\n\n流程配置: /admin/approval.',
      },
      {
        keywords: '电子签|esign|签章|签',
        answer: '电子签集成 e签宝 / 法大大. 配 API Key 后, 合同页 [✍ 在线签署] 即可发起.\n\n支持: 单方签 / 双方签 / 多方会签.',
      },
    ]
  },

  '/order': {
    name: '订单管理',
    icon: '📦',
    description: '订单流转 / 发货 / 退货',
    quickQuestions: [
      '订单状态怎么流转?',
      '怎么关联合同?',
      '退货流程?',
      '批量发货怎么搞?',
    ],
    qa: [
      {
        keywords: '状态|流转|state|生命周期',
        answer: '订单 6 状态: 待付款 → 已付款 → 已发货 → 已签收 → 已完成 / 已退款.\n\n从合同自动创建, 付款后触发发货通知.',
      },
      {
        keywords: '退货|refund|退款|return',
        answer: '退货 3 步: ① 订单页 [↩ 申请退款] ② 选原因 + 上传凭证 ③ 客服审核 (1-3 工作日).\n\n原路退回, 状态自动更新.',
      },
    ]
  },

  '/payment': {
    name: '回款管理',
    icon: '💰',
    description: '收款 / 对账 / 发票',
    quickQuestions: [
      '怎么录入回款?',
      '对账怎么做?',
      '发票怎么开?',
      '逾期怎么催收?',
    ],
    qa: [
      {
        keywords: '录入|新增|回款|收款',
        answer: '录入 2 步: ① 选关联合同 / 订单 ② 填金额 + 方式 + 凭证.\n\n自动勾对: 合同金额 - 已回款 = 未回款.',
      },
      {
        keywords: '对账|reconcile|账单',
        answer: '对账: ① [对账 Tab] ② 选月份 ③ 系统拉银行流水 / 内部记录 ④ 标记差异.\n\n未匹配项标黄, 需手动确认.',
      },
    ]
  },

  // ============ 系统管理 ============
  '/admin/role': {
    name: '角色管理',
    icon: '🛡',
    description: '角色 + 权限分配',
    quickQuestions: [
      '怎么创建角色?',
      '权限粒度多细?',
      '数据权限怎么配?',
      'admin 角色区别?',
    ],
    qa: [
      {
        keywords: '创建|新增|角色|create',
        answer: '创建角色 3 步: ① 填名称/编码/描述 ② 选功能权限 (菜单/按钮) ③ 选数据权限 (全部/本部门/本人/自定义).\n\nadmin 是系统预置超级管理员, 不可删除.',
        actions: [{ label: '打开新增', event: 'pageAction', payload: { page: '/admin/role', action: 'openCreate' } }]
      },
      {
        keywords: '权限|粒度|功能|按钮',
        answer: '功能权限: 菜单级 (看哪些页) + 按钮级 (可点哪些按钮).\n\n数据权限: 全部 / 本部门 / 本部门及下级 / 仅本人 / 自定义 (按规则).',
      },
    ]
  },

  '/admin/menu': {
    name: '菜单管理',
    icon: '📋',
    description: '菜单树 / 路由 / 图标',
    quickQuestions: [
      '怎么加菜单?',
      '图标在哪选?',
      '菜单和权限关系?',
      '隐藏菜单怎么搞?',
    ],
    qa: [
      {
        keywords: '新增|添加|菜单|create',
        answer: '新增菜单: ① 选父菜单 ② 填名称/路径/组件 ③ 选图标 (Element Plus icons-vue) ④ 设排序.\n\n保存后, 关联角色的用户立即可见.',
      },
    ]
  },

  '/admin/audit': {
    name: '审计日志',
    icon: '📊',
    description: '操作日志 / 登录日志 / 错误日志',
    quickQuestions: [
      '日志保留多久?',
      '怎么导出日志?',
      '登录失败怎么看?',
      '错误日志能告警吗?',
    ],
    qa: [
      {
        keywords: '保留|retention|过期|清理',
        answer: '日志保留: 默认 90 天. 配置项: log.retention-days.\n\n定时任务每天凌晨清理, 重要日志可标记 [★ 永久保留].',
      },
      {
        keywords: '导出|export|excel|csv',
        answer: '导出: ① 筛选条件 ② 点 [📥 导出] ③ 选格式 (Excel/CSV/JSON).\n\n最大 10 万行, 超过会分批.',
      },
    ]
  },

  '/admin/model-version': {
    name: '模型版本管理',
    icon: '🧬',
    description: '版本注册 / 灰度 / 回滚',
    quickQuestions: [
      '怎么注册新版本?',
      '灰度怎么配?',
      '回滚机制?',
    ],
    qa: [
      {
        keywords: '注册|新增|create',
        answer: '注册版本: ① 选主模型 ② 填版本号 (SemVer: 1.0.0) ③ 写变更说明 ④ 关联训练任务 ID.\n\n提示: 灰度前先在 staging 跑通.',
      },
    ]
  },

  '/admin/workflow-list': {
    name: '流程列表',
    icon: '📑',
    description: '所有流程 / 版本 / 状态',
    quickQuestions: [
      '怎么找我的流程?',
      '历史版本在哪?',
      '删除能恢复吗?',
    ],
    qa: [
      {
        keywords: '找|搜索|筛选|search',
        answer: '搜索: ① 顶部 [🔍 搜索] 输入名称 ② 用筛选器按状态 / 创建人 / 时间范围.\n\n支持的筛选: 进行中 / 已发布 / 已下线 / 我的.',
      },
    ]
  },

  // ============ 分布式 ============
  '/distributed': {
    name: '分布式演示',
    icon: '🔧',
    description: '7 大分布式能力实时演示',
    quickQuestions: [
      '7 大能力分别是什么?',
      '限流阈值怎么调?',
      '幂等键怎么用?',
      '调度任务怎么加?',
    ],
    qa: [
      {
        keywords: '7|能力|能力|七大|分布式',
        answer: '7 大能力 (基于 Redisson + Redis):\n① 分布式锁 (RedissonLock)\n② 分布式 ID (Snowflake)\n③ 分布式限流 (RateLimiter)\n④ 分布式幂等 (Idempotent)\n⑤ 分布式缓存 (Cache)\n⑥ 分布式事件总线 (Pub/Sub)\n⑦ 分布式调度 (Scheduler)\n\n每项都有演示按钮.',
      },
      {
        keywords: '限流|限流器|阈值',
        answer: '分布式限流器 (RedissonRateLimiter) 默认 100 QPS, 演示页可实时调整. 适用: API 防刷/秒杀限流.',
        actions: [{ label: '查看配置', event: 'pageAction', payload: { page: '/distributed', action: 'showConfig' } }]
      },
    ]
  },

  // ============ 监控 ============
  '/monitor': {
    name: '实时监控',
    icon: '📊',
    description: '9 核心服务健康 + QPS/延迟/错误率 时序 + AI/workflow 业务指标',
    quickQuestions: [
      '服务怎么算健康?',
      '指标多久更新一次?',
      '告警怎么触发的?',
      '数据来源哪里?',
    ],
    qa: [
      {
        keywords: '服务|健康|down|offline|离线',
        answer: '服务健康: 后端 HealthProbe 主动探活 9 个服务 (网关/认证/用户/系统/模型/推理/知识库/文件/训练), HTTP GET /health 2s 超时.\n\n30 秒一次, 响应码 2xx-3xx=up, 4xx 401 也算 up, 5xx/超时=down.\n\n服务 down 时会在顶部出红色告警条.',
        actions: [{ label: '打开监控', event: 'navigate', payload: '/monitor' }]
      },
      {
        keywords: '指标|更新|qps|延迟|多久|间隔',
        answer: '指标时序: 60 个点, 3 秒间隔, 共 3 分钟窗口. 旧点自动滑出.\n\n当前数据是模拟生成 (生产可接 Micrometer+Prometheus 真实埋点). 30 秒前端拉一次 /api/monitor/metrics.',
      },
      {
        keywords: '告警|alert|warning|告警怎么',
        answer: '告警 2 类:\n1. critical: 服务 down (top-bar 红色 alert)\n2. warning: 错误总数 > 100\n\n未来可接 Slack / 飞书 / 邮件 webhook.',
      },
      {
        keywords: '数据|来源|metrics|后端',
        answer: '后端: /api/monitor/snapshot (一次性), /api/monitor/metrics (时序), /api/monitor/stream (SSE 3s 一次推送).\n\nsse 后端用 ConcurrentHashMap + Deque 维护, 进程内 60 点环形缓冲.',
        actions: [{ label: '看后端代码', event: 'navigate', payload: '/monitor' }]
      },
    ]
  },

  // ============ 默认 ============
  '__default__': {
    name: '通用',
    icon: '🪄',
    description: '通用 AI 助手',
    quickQuestions: [
      '怎么用这个系统?',
      'AI 助手能做什么?',
      '哪里有教程?',
      '反馈问题?',
    ],
    qa: [
      {
        keywords: '教程|文档|帮助|help|doc',
        answer: '项目文档: /docs/PROD-DEPLOY.md (部署) + ACCEPTANCE-REPORT.md (验收) + FUNCTIONAL-MANUAL.md (操作手册).\n\n代码仓库: github.com/liugl951127/aiModel.',
      },
      {
        keywords: '反馈|问题|bug|issue',
        answer: '反馈: ① 页面右上 [⚙ 设置] → [反馈] ② 或 GitHub Issues.\n\n紧急: 工作台 [📞 联系我们] 看值班人.',
      },
    ]
  }
}

/**
 * 根据当前路由路径获取页面知识
 * 支持精确匹配 + 前缀匹配 (例: /knowledge/manage 匹配 /knowledge)
 */
export const getPageKnowledge = (path) => {
  // 精确匹配
  if (pageKnowledge[path]) return pageKnowledge[path]
  // 前缀匹配 (最长优先)
  const candidates = Object.keys(pageKnowledge)
    .filter(k => k !== '__default__' && path.startsWith(k))
    .sort((a, b) => b.length - a.length)
  if (candidates.length) return pageKnowledge[candidates[0]]
  return pageKnowledge.__default__
}
