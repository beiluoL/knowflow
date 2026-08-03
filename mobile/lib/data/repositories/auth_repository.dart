// 认证相关 Repository：登录、注册、获取我

import 'package:knowflow_mobile/core/network/api_client.dart';
import 'package:knowflow_mobile/core/storage/token_storage.dart';
import 'package:knowflow_mobile/data/models/user_models.dart';

class AuthRepository {
  AuthRepository(this._api);
  final ApiClient _api;
  TokenStorage get storage => _api.storage;

  // ---------- 登录 ----------
  Future<LoginResponse> login({
    required String username,
    required String password,
  }) async {
    final body = LoginRequest(username: username, password: password).toJson();
    final resp = await _api.post(
      '/api/auth/login',
      body: body,
      fromJson: LoginResponse.fromJson,
    );
    await storage.setAccessToken(resp.accessToken);
    await storage.setRefreshToken(resp.refreshToken);
    if (resp.user != null) await storage.setUserId(resp.user!.id);
    return resp;
  }

  // ---------- 注册 ----------
  Future<UserDTO> register({
    required String username,
    required String password,
    required String email,
  }) async {
    return _api.post(
      '/api/auth/register',
      body: {
        'username': username,
        'password': password,
        'email': email,
      },
      fromJson: UserDTO.fromJson,
    );
  }

  // ---------- 获取当前用户 ----------
  Future<UserDTO> me() async {
    return _api.get('/api/user/me', fromJson: UserDTO.fromJson);
  }

  // ---------- 登出 ----------
  Future<void> logout() async {
    try {
      await _api.post('/api/auth/logout');
    } finally {
      await storage.clear();
    }
  }
}
