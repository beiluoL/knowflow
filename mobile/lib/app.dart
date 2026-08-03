// App 根：ProviderScope + 主题 + 路由

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:knowflow_mobile/core/router/app_router.dart';
import 'package:knowflow_mobile/core/storage/token_storage.dart';
import 'package:knowflow_mobile/core/theme/app_theme.dart';
import 'package:knowflow_mobile/data/repositories/app_providers.dart';

class KnowFlowApp extends ConsumerStatefulWidget {
  const KnowFlowApp({super.key, required this.storage});
  final TokenStorage storage;

  @override
  ConsumerState<KnowFlowApp> createState() => _KnowFlowAppState();
}

class _KnowFlowAppState extends ConsumerState<KnowFlowApp> {
  @override
  Widget build(BuildContext context) {
    final router = ref.watch(routerProvider);
    final mode = ref.watch(themeModeProvider.notifier).toTheme();
    return MaterialApp.router(
      title: 'KnowFlow',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light(),
      darkTheme: AppTheme.dark(),
      themeMode: mode,
      routerConfig: router,
      // 页面切换动画：滑动
      builder: (context, child) {
        return MediaQuery(
          data: MediaQuery.of(context).copyWith(textScaler: const TextScaler.linear(1)),
          child: child ?? const SizedBox.shrink(),
        );
      },
    );
  }
}
