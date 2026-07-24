-- 初始用户 (密码: admin123, 使用 BCrypt 加密)
INSERT INTO sys_user (username, email, password, nickname, avatar, role, total_study_hours, read_docs_count, streak_days, favorite_count, level, exp, energy) VALUES
('admin', 'admin@knowflow.com', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '管理员', '', 'ADMIN', 10.5, 25, 7, 15, 5, 1200, 100),
('user1', 'user1@knowflow.com', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '测试用户1', '', 'USER', 5.0, 10, 3, 5, 2, 300, 80),
('user2', 'user2@knowflow.com', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '测试用户2', '', 'USER', 2.5, 5, 1, 2, 1, 100, 100);

-- 初始分类
INSERT INTO doc_category (name, code, parent_id, icon, description, sort_order, doc_count, status) VALUES
('编程开发', 'programming', 0, '💻', '编程开发相关文档', 1, 8, 1),
('前端开发', 'frontend', 1, '🎨', '前端开发技术', 1, 4, 1),
('后端开发', 'backend', 1, '⚙️', '后端开发技术', 2, 4, 1),
('人工智能', 'ai', 0, '🤖', '人工智能相关文档', 2, 6, 1),
('机器学习', 'ml', 4, '📊', '机器学习技术', 1, 3, 1),
('深度学习', 'dl', 4, '🧠', '深度学习技术', 2, 3, 1),
('产品设计', 'product', 0, '📱', '产品设计相关文档', 3, 4, 1),
('UI设计', 'ui', 7, '🎯', 'UI设计指南', 1, 2, 1),
('产品经理', 'pm', 7, '📋', '产品经理必备', 2, 2, 1);

-- 初始文档
INSERT INTO doc_document (title, content, summary, cover, category_id, category_path, tags, view_count, read_count, favorite_count, word_count, difficulty, sort_order, status) VALUES
('JavaScript 入门教程', 'JavaScript 是一种轻量级的编程语言...\n\n## 变量声明\n\n在 JavaScript 中，我们可以使用 var、let、const 来声明变量...\n\n## 数据类型\n\nJavaScript 有多种数据类型，包括：string、number、boolean、null、undefined、object、symbol...', '从零开始学习 JavaScript 编程，掌握基础语法和核心概念。', '', 2, '1,2', 'JavaScript,前端,入门', 1520, 320, 85, 5200, 1, 1, 1),
('React 实战指南', 'React 是一个用于构建用户界面的 JavaScript 库...\n\n## JSX 语法\n\nJSX 是 JavaScript 的语法扩展...\n\n## 组件\n\n组件是 React 应用的基本构建块...', '深入学习 React，掌握组件化开发和状态管理。', '', 2, '1,2', 'React,前端,框架', 2100, 450, 120, 8500, 2, 2, 1),
('Vue3 快速上手', 'Vue 3 是 Vue.js 的最新版本...\n\n## Composition API\n\nVue 3 引入了 Composition API...\n\n## 响应式原理\n\nVue 3 使用 Proxy 实现响应式...', 'Vue3 新特性详解，快速掌握最新 Vue 技术栈。', '', 2, '1,2', 'Vue,前端,框架', 1800, 380, 95, 6800, 2, 3, 1),
('CSS Grid 布局详解', 'CSS Grid 是一种二维布局系统...\n\n## 基本概念\n\n网格容器、网格项、网格线...\n\n## 常用属性\n\ngrid-template-columns、grid-template-rows...', '全面掌握 CSS Grid 布局，轻松实现复杂页面布局。', '', 2, '1,2', 'CSS,布局,前端', 980, 200, 45, 3200, 2, 4, 1),
('Spring Boot 入门', 'Spring Boot 简化了 Spring 应用的开发...\n\n## 自动配置\n\nSpring Boot 的自动配置原理...\n\n## 起步依赖\n\nStarter 依赖的概念和使用...', '快速上手 Spring Boot，构建企业级 Java 应用。', '', 3, '1,3', 'Spring Boot,Java,后端', 2500, 560, 150, 9200, 2, 1, 1),
('MySQL 性能优化', 'MySQL 性能优化是数据库管理的重要部分...\n\n## 索引优化\n\n正确使用索引可以大幅提升查询性能...\n\n## 查询优化\n\n编写高效的 SQL 语句...', '深入理解 MySQL 性能优化，提升系统响应速度。', '', 3, '1,3', 'MySQL,数据库,优化', 1650, 350, 80, 7200, 3, 2, 1),
('Redis 实战', 'Redis 是一个高性能的键值存储系统...\n\n## 数据结构\n\nString、List、Set、Hash、ZSet...\n\n## 持久化\n\nRDB 和 AOF 两种持久化方式...', 'Redis 实战指南，掌握缓存和分布式场景应用。', '', 3, '1,3', 'Redis,缓存,后端', 1320, 280, 70, 5800, 2, 3, 1),
('微服务架构设计', '微服务架构是一种将应用拆分为小服务的架构风格...\n\n## 服务拆分\n\n如何合理拆分微服务...\n\n## 服务治理\n\n服务注册与发现、负载均衡...', '深入理解微服务架构，设计可扩展的分布式系统。', '', 3, '1,3', '微服务,架构,后端', 1900, 400, 110, 8000, 3, 4, 1),
('机器学习基础', '机器学习是人工智能的核心...\n\n## 监督学习\n\n分类和回归问题...\n\n## 无监督学习\n\n聚类和降维...', '机器学习入门教程，理解核心算法和应用场景。', '', 5, '4,5', '机器学习,AI,入门', 2200, 480, 130, 7800, 2, 1, 1),
('深度学习入门', '深度学习是机器学习的子领域...\n\n## 神经网络\n\n人工神经网络的基本原理...\n\n## 反向传播\n\n反向传播算法详解...', '深度学习入门，理解神经网络和反向传播。', '', 6, '4,6', '深度学习,AI,神经网络', 1850, 400, 100, 6500, 3, 1, 1),
('NLP 自然语言处理', '自然语言处理是人工智能的重要分支...\n\n## 词向量\n\nWord2Vec、GloVe...\n\n## Transformer\n\nTransformer 架构详解...', '自然语言处理从入门到实战，掌握主流 NLP 技术。', '', 6, '4,6', 'NLP,AI,自然语言', 1600, 340, 90, 7100, 3, 2, 1),
('计算机视觉基础', '计算机视觉是让机器看懂图像的技术...\n\n## CNN 卷积神经网络\n\n卷积、池化、全连接...\n\n## 目标检测\n\nYOLO、Faster R-CNN...', '计算机视觉入门，理解图像处理和目标检测。', '', 6, '4,6', 'CV,AI,图像识别', 1450, 310, 75, 6200, 3, 3, 1),
('UI 设计原则', '好的 UI 设计遵循一定的原则...\n\n## 一致性\n\n保持界面元素的一致性...\n\n## 视觉层次\n\n通过视觉层次引导用户注意力...', 'UI 设计基础原则，打造优秀的用户界面。', '', 8, '7,8', 'UI,设计,入门', 950, 180, 40, 2800, 1, 1, 1),
('Figma 使用指南', 'Figma 是一款流行的 UI 设计工具...\n\n## 基本操作\n\n画布、图层、组件...\n\n## 原型设计\n\n制作交互式原型...', 'Figma 从入门到精通，快速掌握设计工具。', '', 8, '7,8', 'Figma,设计工具,UI', 1100, 220, 55, 3500, 1, 2, 1),
('产品需求文档撰写', 'PRD 是产品经理的重要产出...\n\n## 文档结构\n\n背景、目标、功能描述...\n\n## 功能说明\n\n详细描述每个功能...', '如何写出清晰的产品需求文档，提升沟通效率。', '', 9, '7,9', 'PRD,产品经理,文档', 880, 160, 35, 2600, 1, 1, 1),
('用户调研方法论', '用户调研是产品设计的基础...\n\n## 定性调研\n\n用户访谈、可用性测试...\n\n## 定量调研\n\n问卷调查、数据分析...', '掌握用户调研方法，深入理解用户需求。', '', 9, '7,9', '用户研究,产品,调研', 760, 140, 30, 2400, 2, 2, 1);

-- 学习路径
INSERT INTO learning_path (title, description, cover, level, chapter_count, total_duration, enrolled_count, sort_order, status) VALUES
('前端工程师成长之路', '从零基础到高级前端工程师的完整学习路径', '', 'beginner', 5, 1200, 1280, 1, 1),
('Java 后端开发实战', '系统学习 Java 后端开发技术栈', '', 'intermediate', 6, 1800, 950, 2, 1),
('人工智能入门到精通', '全面学习 AI 相关技术，从基础到实战', '', 'beginner', 8, 2400, 2100, 3, 1),
('产品经理入门指南', '零基础转行产品经理的必修课程', '', 'beginner', 4, 600, 680, 4, 1);

-- 章节
INSERT INTO learning_chapter (path_id, title, content, sort_order, duration, doc_ids, flashcard_ids) VALUES
(1, 'HTML 与 CSS 基础', '学习 HTML 标签和 CSS 样式...', 1, 180, '1', ''),
(1, 'JavaScript 核心语法', '深入学习 JavaScript...', 2, 240, '1', ''),
(1, 'DOM 操作与事件', 'DOM 操作和事件处理...', 3, 180, '', ''),
(1, 'React 框架入门', 'React 基础和组件开发...', 4, 300, '2', ''),
(1, '前端工程化实战', 'Webpack、Vite 等工程化工具...', 5, 300, '', ''),
(2, 'Java 基础回顾', 'Java 核心语法复习...', 1, 200, '', ''),
(2, 'Spring Boot 快速上手', 'Spring Boot 项目开发...', 2, 300, '5', ''),
(2, '数据库操作', 'MySQL 和 MyBatis...', 3, 300, '6', ''),
(2, 'Redis 缓存应用', 'Redis 在项目中的应用...', 4, 240, '7', ''),
(2, '微服务架构', '微服务架构设计...', 5, 400, '8', ''),
(2, '项目部署与运维', 'CI/CD 和容器化部署...', 6, 360, '', ''),
(3, 'AI 概述与发展', '人工智能发展历史...', 1, 120, '', ''),
(3, '数学基础', '线性代数和概率论...', 2, 300, '', ''),
(3, '机器学习基础', '经典机器学习算法...', 3, 360, '9', ''),
(3, '深度学习入门', '神经网络基础...', 4, 360, '10', ''),
(3, '计算机视觉', '图像识别和目标检测...', 5, 360, '12', ''),
(3, '自然语言处理', 'NLP 技术和应用...', 6, 360, '11', ''),
(3, '大语言模型', 'LLM 原理和应用...', 7, 300, '', ''),
(3, 'AI 项目实战', '综合项目实战...', 8, 240, '', ''),
(4, '产品思维培养', '什么是产品思维...', 1, 120, '', ''),
(4, '用户研究方法', '用户调研和分析...', 2, 180, '16', ''),
(4, '需求分析与管理', '需求收集和分析...', 3, 150, '15', ''),
(4, '产品设计实战', '从 0 到 1 设计产品...', 4, 150, '', '');

-- 闪卡
INSERT INTO learning_flashcard (path_id, chapter_id, front, back, category, difficulty, review_count) VALUES
(1, 2, 'JavaScript 中有哪些数据类型？', '基本类型：string、number、boolean、null、undefined、symbol、bigint\n引用类型：object', 'JavaScript', 1, 120),
(1, 2, 'let、const、var 的区别？', '1. var 是函数作用域，存在变量提升\n2. let 和 const 是块级作用域\n3. const 声明的变量不能重新赋值', 'JavaScript', 1, 95),
(1, 4, 'React 中 useState 的作用是什么？', 'useState 是 React Hook，用于在函数组件中添加状态管理。返回一个状态值和更新该状态的函数。', 'React', 2, 80),
(3, 4, '什么是梯度下降？', '梯度下降是一种优化算法，通过沿损失函数梯度的反方向迭代更新参数，以最小化损失函数。', '深度学习', 2, 65),
(3, 3, '监督学习和无监督学习的区别？', '监督学习：有标签数据，学习输入到输出的映射\n无监督学习：无标签数据，发现数据内在结构', '机器学习', 1, 110),
(3, 6, 'Transformer 的核心机制是什么？', '自注意力机制（Self-Attention），允许模型在处理序列时关注所有位置，而不仅限于局部窗口。', 'NLP', 3, 45);

-- 学习任务
INSERT INTO learning_task (user_id, title, description, type, target_id, exp_reward, energy_cost, deadline, status) VALUES
(2, '完成 React 入门学习', '学习 React 基础概念和组件开发', 'chapter', 4, 50, 20, NULL, 0),
(2, '每日学习打卡', '今天学习至少 30 分钟', 'daily', NULL, 10, 0, NULL, 1),
(2, '阅读 JavaScript 教程', '完整阅读 JavaScript 入门教程', 'doc', 1, 30, 10, NULL, 0),
(3, '学习机器学习基础', '完成机器学习基础章节学习', 'path', 3, 100, 30, NULL, 0);
