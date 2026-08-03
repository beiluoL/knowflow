// 统一 HTTP 客户端：基于 Dio + 拦截器链
// 对外暴露 get/post/put/delete/upload 方法，返回已解包的数据 T

import 'package:dio/dio.dart';
import 'package:knowflow_mobile/core/constants/app_constants.dart';
import 'package:knowflow_mobile/core/error/app_exception.dart';
import 'package:knowflow_mobile/core/network/auth_interceptor.dart';
import 'package:knowflow_mobile/core/network/error_interceptor.dart';
import 'package:knowflow_mobile/core/storage/token_storage.dart';
import 'package:logger/logger.dart';

final _log = Logger(printer: PrettyPrinter(methodCount: 2));

class ApiClient {
  ApiClient._(this._dio, this._storage);
  final Dio _dio;
  final TokenStorage _storage;

  static ApiClient? _instance;

  factory ApiClient.fromStorage(TokenStorage storage) {
    if (_instance != null) return _instance!;
    final dio = Dio(BaseOptions(
      baseUrl: AppConstants.apiBaseUrl,
      connectTimeout: const Duration(milliseconds: AppConstants.connectTimeoutMs),
      receiveTimeout: const Duration(milliseconds: AppConstants.receiveTimeoutMs),
      sendTimeout: const Duration(milliseconds: AppConstants.sendTimeoutMs),
      contentType: Headers.jsonContentType,
    ));
    dio.interceptors.addAll([
      LogInterceptor(
        requestBody: true,
        responseBody: true,
        logPrint: (o) => _log.d(o),
      ),
      AuthInterceptor(storage, onForceLogout: () {
        storage.clear();
      }),
      ErrorInterceptor(),
    ]);
    _instance = ApiClient._(dio, storage);
    return _instance!;
  }

  /// 取底层 dio（便于 custom options）
  Dio get raw => _dio;
  TokenStorage get storage => _storage;

  // ---------- GET ----------
  Future<T> get<T>(
    String path, {
    Map<String, dynamic>? query,
    T Function(Map<String, dynamic>)? fromJson,
    CancelToken? cancel,
  }) async {
    final r = await _request(
      method: 'GET',
      path: path,
      query: query,
      cancelToken: cancel,
    );
    return _decode(r.data, fromJson);
  }

  Future<List<T>> getList<T>(
    String path, {
    Map<String, dynamic>? query,
    required T Function(Map<String, dynamic>) fromJson,
    CancelToken? cancel,
  }) async {
    final r = await _request(
      method: 'GET',
      path: path,
      query: query,
      cancelToken: cancel,
    );
    final list = (r.data is Map)
        ? ((r.data as Map)['items'] ?? r.data['data'] ?? r.data['list'] ?? [])
        : r.data;
    return (list as List).map((e) => fromJson(e as Map<String, dynamic>)).toList();
  }

  // ---------- POST / PUT / DELETE ----------
  Future<T> post<T>(
    String path, {
    Object? body,
    Map<String, dynamic>? query,
    T Function(Map<String, dynamic>)? fromJson,
    CancelToken? cancel,
  }) async {
    final r = await _request(
      method: 'POST',
      path: path,
      body: body,
      query: query,
      cancelToken: cancel,
    );
    return _decode(r.data, fromJson);
  }

  Future<T> put<T>(
    String path, {
    Object? body,
    Map<String, dynamic>? query,
    T Function(Map<String, dynamic>)? fromJson,
  }) async {
    final r = await _request(method: 'PUT', path: path, body: body, query: query);
    return _decode(r.data, fromJson);
  }

  Future<void> delete(String path, {Object? body}) async {
    await _request(method: 'DELETE', path: path, body: body);
  }

  // ---------- 上传 ----------
  Future<T> upload<T>(
    String path, {
    required String filePath,
    String fieldName = 'file',
    Map<String, dynamic> fields = const {},
    T Function(Map<String, dynamic>)? fromJson,
  }) async {
    final form = FormData.fromMap({
      fieldName: await MultipartFile.fromFile(filePath),
      ...fields,
    });
    final r = await _request(method: 'POST', path: path, body: form);
    return _decode(r.data, fromJson);
  }

  // ---------- 内部 ----------
  Future<Response> _request({
    required String method,
    required String path,
    Object? body,
    Map<String, dynamic>? query,
    CancelToken? cancelToken,
  }) async {
    try {
      return await _dio.request(
        path,
        data: body,
        queryParameters: query,
        cancelToken: cancelToken,
        options: Options(method: method),
      );
    } on DioException catch (e) {
      if (e.error is AppException) rethrow;
      if (e.type == DioExceptionType.cancel) rethrow;
      throw e.error is Exception ? (e.error as Exception).toAppException() : e;
    }
  }

  /// 解包 KnowFlow 标准响应：{ "code": 200, "message": "...", "data": ... }
  T _decode<T>(dynamic raw, T Function(Map<String, dynamic>)? fromJson) {
    // 支持标准包裹和裸数据两种
    Map<String, dynamic>? wrapped;
    Object? inner = raw;
    if (raw is Map &&
        (raw.containsKey('code') || raw.containsKey('data'))) {
      wrapped = raw.cast<String, dynamic>();
      // 业务错误
      final code = wrapped['code'] ?? 0;
      if (code is int && code >= 400) {
        throw AppException.fromHttpStatus(code, wrapped['message']?.toString() ?? '请求失败');
      }
      inner = wrapped['data'];
    }
    if (fromJson == null) {
      return inner as T;
    }
    if (inner == null) return _nullOrThrow<T>();
    return fromJson(inner as Map<String, dynamic>);
  }

  T _nullOrThrow<T>() {
    if (null is T) return null as T;
    throw const AppException(AppErrorCode.dataParse, '响应数据为空');
  }
}
