// 全局 Riverpod Provider 集合
// 暴露 ApiClient、AuthRepository、AuthState（等）

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:knowflow_mobile/core/network/api_client.dart';
import 'package:knowflow_mobile/core/storage/token_storage.dart';
import 'package:knowflow_mobile/data/models/user_models.dart';
import 'package:knowflow_mobile/data/repositories/auth_repository.dart';

// ===== 基础依赖 =====
final tokenStorageProvider = FutureProvider<TokenStorage>(
  (ref) => TokenStorage.instance,
);

final apiClientProvider = Provider<ApiClient>((ref) {
  final storage = ref.watch(tokenStorageProvider).requireValue;
  return ApiClient.fromStorage(storage);
});

// ===== 认证 =====
final authRepositoryProvider = Provider<AuthRepository>(
  (ref) => AuthRepository(ref.watch(apiClientProvider)),
);

final authControllerProvider =
    AsyncNotifierProvider<AuthController, UserDTO?>(AuthController.new);

/// 认证状态：
///   AsyncData(null)  → 未登录
///   AsyncLoading      → 正在拉取 /api/user/me
///   AsyncData(User)   → 已登录
///   AsyncError        → 拉取失败（提示网络或强退登录）
class AuthController extends AsyncNotifier<UserDTO?> {
  @override
  Future<UserDTO?> build() async {
    await ref.watch(tokenStorageProvider.future);
    final storage = ref.read(tokenStorageProvider).requireValue;
    final token = await storage.accessToken;
    if (token == null || token.isEmpty) return null;
    try {
      final repo = ref.read(authRepositoryProvider);
      final user = await repo.me();
      await storage.setUserId(user.id);
      return user;
    } on DioException catch (_) {
      // /api/user/me 失败：本地 token 失效，清掉
      await storage.clear();
      return null;
    }
  }

  // ========= 登录 =========
  Future<UserDTO> login({
    required String username,
    required String password,
  }) async {
    state = const AsyncValue.loading();
    state = await AsyncValue.guard(() async {
      final repo = ref.read(authRepositoryProvider);
      final resp = await repo.login(username: username, password: password);
      if (resp.user != null) return resp.user!;
      return ref.read(authRepositoryProvider).me();
    });
    if (state case AsyncData(value: final u?)) return u;
    throw state.error ?? Exception('登录失败');
  }

  // ========= 登出 =========
  Future<void> logout() async {
    state = const AsyncValue.loading();
    final repo = ref.read(authRepositoryProvider);
    await repo.logout();
    state = const AsyncValue.data(null);
  }
}

// ===== 主题模式 =====
enum AppThemeMode { system, light, dark }

final themeModeProvider =
    StateNotifierProvider<ThemeModeController, AppThemeMode>(
  (ref) {
    final storage = ref.read(tokenStorageProvider).requireValue;
    final v = storage.themeMode ?? 'system';
    return ThemeModeController(
      storage,
      switch (v) {
        'light' => AppThemeMode.light,
        'dark' => AppThemeMode.dark,
        _ => AppThemeMode.system,
      },
    );
  },
);

class ThemeModeController extends StateNotifier<AppThemeMode> {
  ThemeModeController(this._storage, AppThemeMode mode) : super(mode);
  final TokenStorage _storage;

  Future<void> set(AppThemeMode m) async {
    state = m;
    await _storage.setThemeMode(
      switch (m) {
        AppThemeMode.light => 'light',
        AppThemeMode.dark => 'dark',
        AppThemeMode.system => 'system',
      },
    );
  }

  ThemeMode toTheme() => switch (state) {
        AppThemeMode.light => ThemeMode.light,
        AppThemeMode.dark => ThemeMode.dark,
        AppThemeMode.system => ThemeMode.system,
      };
}
