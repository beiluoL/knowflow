// 我的页面：用户卡片 + 主题切换 + 菜单

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:knowflow_mobile/core/theme/app_theme.dart';
import 'package:knowflow_mobile/data/repositories/app_providers.dart';
import 'package:knowflow_mobile/shared/widgets/kf_icon.dart';
import 'package:knowflow_mobile/shared/widgets/placeholder_card.dart';

class ProfilePage extends ConsumerWidget {
  const ProfilePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(authControllerProvider).valueOrNull;
    final theme = ref.watch(themeModeProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Text('我的'),
        actions: [
          IconButton(
            tooltip: '设置',
            onPressed: () {
              final next = switch (theme) {
                AppThemeMode.system => AppThemeMode.light,
                AppThemeMode.light => AppThemeMode.dark,
                AppThemeMode.dark => AppThemeMode.system,
              };
              ref.read(themeModeProvider.notifier).set(next);
            },
            icon: Icon(switch (theme) {
              AppThemeMode.system => Icons.auto_mode_rounded,
              AppThemeMode.light => Icons.light_mode_rounded,
              AppThemeMode.dark => Icons.dark_mode_rounded,
            }),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(AppSpacing.lg),
        children: [
          Row(
            children: [
              CircleAvatar(
                radius: 28,
                backgroundColor: AppColors.primary,
                child: Text(
                  user?.initials ?? '?',
                  style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600),
                ),
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      user?.displayName ?? '未登录',
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    Text(
                      'Lv.${user?.level ?? 1} · ${user?.points ?? 0} 积分 · ${user?.studyMinutes ?? 0} 分钟',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.xl),
          Card(
            margin: EdgeInsets.zero,
            child: Column(
              children: [
                const _Tile('成就系统', KfIconData.check, '12 / 30'),
                const Divider(height: 1, indent: 56),
                const _Tile('错题本', KfIconData.bot, '24 道错题'),
                const Divider(height: 1, indent: 56),
                const _Tile('我的笔记', KfIconData.search, '8 篇'),
                const Divider(height: 1, indent: 56),
                _Tile('退出登录', KfIconData.logout, '', onTap: () async {
                  await ref.read(authControllerProvider.notifier).logout();
                  if (context.mounted) context.go('/login');
                }, isDanger: true),
              ],
            ),
          ),
          const SizedBox(height: AppSpacing.xl),
          const PlaceholderCard(title: '学习报告', subtitle: '查看周报/月报数据'),
        ],
      ),
    );
  }
}

class _Tile extends StatelessWidget {
  const _Tile(this.title, this.icon, this.subtitle, {this.onTap, this.isDanger = false});
  final String title;
  final IconData icon;
  final String subtitle;
  final VoidCallback? onTap;
  final bool isDanger;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      onTap: onTap,
      leading: KfIcon(icon, color: isDanger ? AppColors.danger : null),
      title: Text(
        title,
        style: TextStyle(color: isDanger ? AppColors.danger : null),
      ),
      trailing: subtitle.isNotEmpty
          ? Text(subtitle, style: Theme.of(context).textTheme.bodySmall)
          : null,
    );
  }
}
