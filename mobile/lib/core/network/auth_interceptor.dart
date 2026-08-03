// Dio 拦截器：自动注入 Bearer Token，401 时尝试刷新 Token，失败则登出

import 'package:dio/dio.dart';
import 'package:knowflow_mobile/core/storage/token_storage.dart';

class AuthInterceptor extends Interceptor {
  AuthInterceptor(this._storage, {this.onForceLogout});

  final TokenStorage _storage;
  final void Function()? onForceLogout;
  bool _refreshing = false;
  final List<ErrorInterceptorHandler> _pending = [];

  @override
  Future<void> onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    final token = await _storage.accessToken;
    if (token != null && token.isNotEmpty) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    options.headers['Accept'] = 'application/json';
    options.headers['User-Agent'] = 'KnowFlow-Mobile/1.0';
    handler.next(options);
  }

  @override
  Future<void> onError(DioException err, ErrorInterceptorHandler handler) async {
    if (err.response?.statusCode != 401) {
      handler.next(err);
      return;
    }

    // 已认证请求返回 401：尝试刷新 token
    final currentToken = await _storage.accessToken;
    if (currentToken == null || currentToken.isEmpty) {
      _forceLogout();
      handler.next(err);
      return;
    }

    _pending.add(handler);
    if (!_refreshing) {
      _refreshing = true;
      final ok = await _storage.refresh();
      _refreshing = false;

      if (ok) {
        for (final h in _pending) {
          await _retry(err.requestOptions, h);
        }
      } else {
        _forceLogout();
        for (final h in _pending) {
          h.next(err);
        }
      }
      _pending.clear();
    }
  }

  Future<void> _retry(
    RequestOptions request,
    ErrorInterceptorHandler handler,
  ) async {
    final token = await _storage.accessToken;
    if (token != null) request.headers['Authorization'] = 'Bearer $token';
    try {
      final dio = Dio()..options.baseUrl = request.baseUrl;
      final r = await dio.request(
        request.uri.toString(),
        data: request.data,
        options: Options(
          method: request.method,
          headers: request.headers,
          responseType: request.responseType,
        ),
      );
      handler.resolve(r);
    } on DioException catch (e) {
      handler.next(e);
    }
  }

  void _forceLogout() {
    onForceLogout?.call();
  }
}
