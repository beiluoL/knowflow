// go_router 路由 + 鉴权守卫 + 底部 Tab Shell

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:knowflow_mobile/data/repositories/app_providers.dart';
import 'package:knowflow_mobile/features/auth/pages/login_page.dart';
import 'package:knowflow_mobile/features/home/pages/home_page.dart';
import 'package:knowflow_mobile/features/learning/pages/learning_page.dart';
import 'package:knowflow_mobile/features/profile/pages/profile_page.dart';
import 'package:knowflow_mobile/shared/layouts/main_shell.dart';

final routerProvider = Provider<GoRouter>((ref) {
  final auth = ref.watch(authControllerProvider);
  return GoRouter(
    initialLocation: '/',
    debugLogDiagnostics: true,
    refreshListenable: GoRouterRefreshStream(auth.isLoading),
    redirect: (context, state) {
      // 仅在 auth 已 resolve 后才做守卫（首次进入等待拉 user/me 完成）
      if (auth.isLoading) return null;
      final loggedIn = auth.valueOrNull != null;
      final loggingIn = state.matchedLocation.startsWith('/login');
      if (!loggedIn && !loggingIn) return '/login';
      if (loggedIn && loggingIn) return '/';
      return null;
    },
    routes: [
      // === 全屏路由 ===
      GoRoute(
        path: '/login',
        builder: (_, __) => const LoginPage(),
      ),
      // === 底部 Tab Shell ===
      ShellRoute(
        builder: (_, __, child) => MainShell(child: child),
        routes: [
          GoRoute(path: '/', builder: (_, __) => const HomePage()),
          GoRoute(path: '/learning', builder: (_, __) => const LearningPage()),
          GoRoute(path: '/knowledge', builder: (_, __) => const KnowledgePage()),
          GoRoute(path: '/chat', builder: (_, __) => const ChatPage()),
          GoRoute(path: '/community', builder: (_, __) => const CommunityPage()),
          GoRoute(path: '/profile', builder: (_, __) => const ProfilePage()),
        ],
      ),
    ],
    errorBuilder: (_, s) => Scaffold(
      body: Center(child: Text('未找到页面：${s.matchedLocation}')),
    ),
  );
});

class GoRouterRefreshStream extends ChangeNotifier {
  GoRouterRefreshStream(bool _) {
    notifyListeners();
  }
}
