// 学习中心、知识库、AI 助手、社区 Tab 页占位

import 'package:flutter/material.dart';
import 'package:knowflow_mobile/core/theme/app_theme.dart';
import 'package:knowflow_mobile/shared/widgets/placeholder_card.dart';

class LearningPage extends StatelessWidget {
  const LearningPage({super.key});
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('学习中心')),
      body: ListView(
        padding: const EdgeInsets.all(AppSpacing.lg),
        children: const [
          PlaceholderCard(title: '我的学习路径', subtitle: '2 条进行中'),
          SizedBox(height: AppSpacing.lg),
          PlaceholderCard(title: '今日闪卡复习', subtitle: '12 张待复习'),
          SizedBox(height: AppSpacing.lg),
          PlaceholderCard(title: '章节学习', subtitle: 'Java 基础 → 第 3 章 面向对象'),
        ],
      ),
    );
  }
}

class KnowledgePage extends StatelessWidget {
  const KnowledgePage({super.key});
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('知识库')),
      body: ListView(
        padding: const EdgeInsets.all(AppSpacing.lg),
        children: const [
          PlaceholderCard(title: '知识图谱', subtitle: '可视化知识关联 · 324 节点'),
          SizedBox(height: AppSpacing.lg),
          PlaceholderCard(title: '知识库列表', subtitle: '5 个知识库 · 128 篇文档'),
          SizedBox(height: AppSpacing.lg),
          PlaceholderCard(title: '最近浏览', subtitle: '数据结构与算法基础'),
        ],
      ),
    );
  }
}

class ChatPage extends StatelessWidget {
  const ChatPage({super.key});
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('AI 助手')),
      body: ListView(
        padding: const EdgeInsets.all(AppSpacing.lg),
        children: const [
          PlaceholderCard(title: '智能问答', subtitle: '问任何学习问题，AI 帮你解答'),
          SizedBox(height: AppSpacing.lg),
          PlaceholderCard(title: '最近对话', subtitle: '暂无对话记录，开始你的第一次提问吧'),
        ],
      ),
    );
  }
}

class CommunityPage extends StatelessWidget {
  const CommunityPage({super.key});
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('社区')),
      body: ListView(
        padding: const EdgeInsets.all(AppSpacing.lg),
        children: const [
          PlaceholderCard(title: '热门讨论', subtitle: '一起分享学习心得'),
          SizedBox(height: AppSpacing.lg),
          PlaceholderCard(title: '学习小组', subtitle: '加入 3 个小组'),
          SizedBox(height: AppSpacing.lg),
          PlaceholderCard(title: '全局排行榜', subtitle: '今日积分前 100'),
        ],
      ),
    );
  }
}
