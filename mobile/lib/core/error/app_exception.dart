// 统一业务异常 + 错误码映射

import 'package:flutter/foundation.dart';

enum AppErrorCode {
  unknown,
  network,
  timeout,
  unauthorized,      // 401
  forbidden,         // 403
  notFound,          // 404
  conflict,          // 409
  tooManyRequests,   // 429
  server,            // 5xx
  dataParse,
  localDatabase,
  tokenExpired,
}

class AppException implements Exception {
  final AppErrorCode code;
  final String message;
  final int? httpStatus;
  final Object? cause;
  final StackTrace? trace;

  const AppException(
    this.code,
    this.message, {
    this.httpStatus,
    this.cause,
    this.trace,
  });

  factory AppException.fromHttpStatus(int status, String msg) {
    return AppException(
      switch (status) {
        401 => AppErrorCode.unauthorized,
        403 => AppErrorCode.forbidden,
        404 => AppErrorCode.notFound,
        409 => AppErrorCode.conflict,
        429 => AppErrorCode.tooManyRequests,
        < 599 && >= 500 => AppErrorCode.server,
        _ => AppErrorCode.unknown,
      },
      msg,
      httpStatus: status,
    );
  }

  /// 用户可读的中文提示
  String get displayMessage {
    switch (code) {
      case AppErrorCode.network:
        return '网络连接失败，请检查网络后重试';
      case AppErrorCode.timeout:
        return '请求超时，请稍后重试';
      case AppErrorCode.unauthorized:
      case AppErrorCode.tokenExpired:
        return '登录已过期，请重新登录';
      case AppErrorCode.forbidden:
        return '没有权限访问该资源';
      case AppErrorCode.notFound:
        return '请求的资源不存在';
      case AppErrorCode.tooManyRequests:
        return '操作过于频繁，请稍后再试';
      case AppErrorCode.server:
        return '服务器开小差了，请稍后再试';
      case AppErrorCode.dataParse:
        return '数据解析失败';
      case AppErrorCode.localDatabase:
        return '本地数据异常';
      case AppErrorCode.conflict:
        return message.isEmpty ? '操作冲突' : message;
      case AppErrorCode.unknown:
        return message.isEmpty ? '操作失败，请重试' : message;
    }
  }

  @override
  String toString() {
    final sb = StringBuffer('AppException[$code]: $message');
    if (httpStatus != null) sb.write(' (HTTP $httpStatus)');
    if (cause != null) sb.write('\nCaused by: $cause');
    return sb.toString();
  }
}

extension DioErrorMapper on Exception {
  AppException toAppException() {
    final self = this;
    if (self is AppException) return self;
    // DioException 已在拦截器中转为 AppException，这里兜底
    if (self.toString().contains('SocketException')) {
      return const AppException(AppErrorCode.network, '网络异常');
    }
    if (self.toString().contains('TimeoutException')) {
      return const AppException(AppErrorCode.timeout, '请求超时');
    }
    if (kDebugMode) {
      return AppException(AppErrorCode.unknown, toString());
    }
    return const AppException(AppErrorCode.unknown, '未知错误');
  }
}
