// 占位页骨架：5 个底部 Tab 页 + 登录页

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:knowflow_mobile/core/theme/app_theme.dart';
import 'package:knowflow_mobile/shared/widgets/kf_icon.dart';
import 'package:knowflow_mobile/shared/widgets/placeholder_card.dart';

// ============ 首页 ============
class HomePage extends StatelessWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('知识库'),
        actions: [
          IconButton(onPressed: () {}, icon: const KfIcon(KfIconData.search)),
          IconButton(onPressed: () {}, icon: const Badge(label: Text('3'), child: KfIcon(KfIconData.bell))),
          const SizedBox(width: 8),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(AppSpacing.lg),
        children: const [
          _QuickEntryRow(),
          SizedBox(height: AppSpacing.xl),
          PlaceholderCard(title: '今天的学习计划', subtitle: '3 个番茄钟 · 1 张闪卡'),
          SizedBox(height: AppSpacing.lg),
          PlaceholderCard(title: '推荐学习路径', subtitle: 'Python 入门 → SQL → Java 后端'),
          SizedBox(height: AppSpacing.lg),
          PlaceholderCard(title: '每日一句', subtitle: '"学而时习之，不亦说乎。"'),
        ],
      ),
    );
  }
}

class _QuickEntryRow extends StatelessWidget {
  const _QuickEntryRow();

  @override
  Widget build(BuildContext context) {
    final entries = [
      const _Entry('学习路径', KfIconData.bookOpen, '/learning', AppColors.primary),
      const _Entry('闪卡复习', KfIconData.database, '/learning', AppColors.success),
      const _Entry('AI 问答', KfIconData.bot, '/chat', AppColors.highlight),
      const _Entry('社区', KfIconData.messageCircle, '/community', AppColors.longBreakBlue),
    ];
    return Row(
      children: entries
          .map((e) => Expanded(
                child: _QuickEntry(
                  label: e.label,
                  icon: e.icon,
                  onTap: () => GoRouter.of(context).go(e.route),
                  color: e.color,
                ),
              ))
          .toList(),
    );
  }
}

class _Entry {
  const _Entry(this.label, this.icon, this.route, this.color);
  final String label;
  final IconData icon;
  final String route;
  final Color color;
}

class _QuickEntry extends StatelessWidget {
  const _QuickEntry({
    required this.label,
    required this.icon,
    required this.onTap,
    required this.color,
  });
  final String label;
  final IconData icon;
  final VoidCallback onTap;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(AppRadius.md),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: AppSpacing.md),
        child: Column(
          children: [
            Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: color.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(AppRadius.md),
              ),
              child: KfIcon(icon, size: 24, color: color),
            ),
            const SizedBox(height: AppSpacing.sm),
            Text(label, style: Theme.of(context).textTheme.bodySmall),
          ],
        ),
      ),
    );
  }
}
