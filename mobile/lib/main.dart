// KnowFlow 移动端入口
// 初始化：Storage → Isar → Riverpod ProviderScope → MaterialApp.router

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:knowflow_mobile/app.dart';
import 'package:knowflow_mobile/core/storage/isar_db.dart';
import 'package:knowflow_mobile/core/storage/token_storage.dart';
import 'package:knowflow_mobile/data/repositories/app_providers.dart';
import 'package:logger/logger.dart';

final Logger _log = Logger(printer: PrettyPrinter(methodCount: 1));

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // 1. 基础基础设施（同步等待，保证 provider 可用）
  final storage = await TokenStorage.instance;
  await AppDatabase.init();

  _log.i('KnowFlow bootstrap: storage & db ready');

  // 2. 覆写 tokenStorageProvider，省去二次 FutureProvider await
  final overrides = [
    tokenStorageProvider.overrideWith((ref) => storage),
  ];

  runApp(
    ProviderScope(
      overrides: overrides,
      observers: [_RiverpodLogger()],
      child: KnowFlowApp(storage: storage),
    ),
  );
}

class _RiverpodLogger extends ProviderObserver {
  @override
  void didUpdateProvider(
    ProviderBase<Object?> provider,
    Object? previousValue,
    Object? newValue,
    ProviderContainer container,
  ) {
    if (newValue is AsyncError) {
      _log.e('Provider error: ${provider.name}',
          error: newValue.error, stackTrace: newValue.stackTrace);
    }
  }
}
