// 底部 Tab 主框架（MainShell）：NavigationBar + 对应页

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:knowflow_mobile/core/theme/app_theme.dart';
import 'package:knowflow_mobile/shared/widgets/kf_icon.dart';

class MainShell extends StatelessWidget {
  const MainShell({super.key, required this.child});
  final Widget child;

  static const List<_Tab> _tabs = [
    _Tab(path: '/', label: '首页', icon: KfIconData.home),
    _Tab(path: '/learning', label: '学习', icon: KfIconData.bookOpen),
    _Tab(path: '/knowledge', label: '知识库', icon: KfIconData.database),
    _Tab(path: '/chat', label: 'AI 助手', icon: KfIconData.bot),
    _Tab(path: '/community', label: '社区', icon: KfIconData.messageCircle),
    _Tab(path: '/profile', label: '我的', icon: KfIconData.user),
  ];

  @override
  Widget build(BuildContext context) {
    final location = GoRouterState.of(context).matchedLocation;
    final currentIdx = _tabs.indexWhere((t) => t.path == location);
    return Scaffold(
      body: SafeArea(child: child),
      bottomNavigationBar: NavigationBar(
        selectedIndex: currentIdx.clamp(0, _tabs.length - 1),
        onDestinationSelected: (i) => context.go(_tabs[i].path),
        destinations: _tabs
            .map((t) => NavigationDestination(
                  icon: KfIcon(t.icon, size: 22),
                  selectedIcon: KfIcon(t.icon, size: 22, color: AppColors.primary),
                  label: t.label,
                ))
            .toList(),
      ),
    );
  }
}

class _Tab {
  const _Tab({required this.path, required this.label, required this.icon});
  final String path;
  final String label;
  final IconData icon;
}
