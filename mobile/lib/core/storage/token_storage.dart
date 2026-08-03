// Token 加密存储：iOS Keychain / Android Keystore
// 接入 flutter_secure_storage，存 accessToken / refreshToken

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:meta/meta.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:knowflow_mobile/core/constants/storage_keys.dart';

class TokenStorage {
  TokenStorage._(this._secure, this._prefs);

  final FlutterSecureStorage _secure;
  final SharedPreferences _prefs;

  static TokenStorage? _instance;
  static Future<TokenStorage> get instance async {
    if (_instance != null) return _instance!;
    const secure = FlutterSecureStorage();
    final prefs = await SharedPreferences.getInstance();
    _instance = TokenStorage._(secure, prefs);
    return _instance!;
  }

  // ---------- 加密 Token ----------
  Future<String?> get accessToken => _secure.read(key: StorageKeys.accessToken);
  Future<void> setAccessToken(String? v) => v == null
      ? _secure.delete(key: StorageKeys.accessToken)
      : _secure.write(key: StorageKeys.accessToken, value: v);

  Future<String?> get refreshToken => _secure.read(key: StorageKeys.refreshToken);
  Future<void> setRefreshToken(String? v) => v == null
      ? _secure.delete(key: StorageKeys.refreshToken)
      : _secure.write(key: StorageKeys.refreshToken, value: v);

  // ---------- 轻量用户配置 ----------
  int? get userId => _prefs.getInt(StorageKeys.userId);
  Future<void> setUserId(int? v) => v == null
      ? _prefs.remove(StorageKeys.userId)
      : _prefs.setInt(StorageKeys.userId, v);

  String? get themeMode => _prefs.getString(StorageKeys.themeMode);
  Future<void> setThemeMode(String mode) =>
      _prefs.setString(StorageKeys.themeMode, mode);

  String? get locale => _prefs.getString(StorageKeys.locale);
  Future<void> setLocale(String locale) =>
      _prefs.setString(StorageKeys.locale, locale);

  bool get onboardingDone => _prefs.getBool(StorageKeys.onboardingDone) ?? false;
  Future<void> setOnboardingDone(bool v) =>
      _prefs.setBool(StorageKeys.onboardingDone, v);

  // ---------- 清空（退出登录） ----------
  Future<void> clear() async {
    await _secure.delete(key: StorageKeys.accessToken);
    await _secure.delete(key: StorageKeys.refreshToken);
    // 保留主题/语言等非敏感配置
  }

  // ---------- 刷新 Token（骨架：真实实现调 AuthRepository） ----------
  /// 供拦截器调用，真实业务注入 AuthRepository.renew()
  @visibleForOverriding
  Future<bool> refresh() async => false;
}
