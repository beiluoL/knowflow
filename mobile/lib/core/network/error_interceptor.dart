// Dio 错误拦截器：把 DioException 统一映射成 AppException

import 'package:dio/dio.dart';
import 'package:knowflow_mobile/core/error/app_exception.dart';

class ErrorInterceptor extends Interceptor {
  @override
  void onError(DioException err, ErrorInterceptorHandler handler) {
    late AppException e;
    switch (err.type) {
      case DioExceptionType.connectionTimeout:
      case DioExceptionType.sendTimeout:
      case DioExceptionType.receiveTimeout:
      case DioExceptionType.transformTimeout:
        e = const AppException(AppErrorCode.timeout, '请求超时');
      case DioExceptionType.connectionError:
      case DioExceptionType.badCertificate:
        e = AppException(
          AppErrorCode.network,
          err.message ?? '网络连接失败',
          cause: err,
        );
      case DioExceptionType.badResponse:
        final status = err.response?.statusCode ?? 0;
        final body = err.response?.data;
        final msg = body is Map && body['message'] != null
            ? body['message'].toString()
            : (err.message ?? '请求失败');
        e = AppException.fromHttpStatus(status, msg);
      case DioExceptionType.cancel:
        // 用户取消 → 向上透传（由上层忽略）
        handler.next(err);
        return;
      case DioExceptionType.unknown:
        final msg = err.toString().toLowerCase();
        if (msg.contains('socketexception')) {
          e = const AppException(AppErrorCode.network, '网络连接失败');
        } else {
          e = AppException(
            AppErrorCode.unknown,
            err.message ?? '未知错误',
            cause: err,
          );
        }
    }
    handler.next(
      DioException(
        requestOptions: err.requestOptions,
        error: e,
        response: err.response,
        type: err.type,
      ),
    );
  }
}
