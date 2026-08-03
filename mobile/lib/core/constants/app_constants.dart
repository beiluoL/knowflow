// 全局常量：API 地址、超时、存储 Key 等
// 生产环境用 --dart-define=API_BASE_URL=https://api.knowflow.com 覆盖

import 'package:flutter/foundation.dart';

class AppConstants {
  AppConstants._();

  /// 后端 API 根地址
  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: kReleaseMode
        ? 'https://api.knowflow.com'
        : 'http://10.0.2.2:8080', // Android 模拟器访问宿主机
  );

  /// WebSocket 地址
  static const String wsBaseUrl = String.fromEnvironment(
    'WS_BASE_URL',
    defaultValue: kReleaseMode
        ? 'wss://api.knowflow.com/ws'
        : 'ws://10.0.2.2:8080/ws',
  );

  /// 网络超时
  static const int connectTimeoutMs = 15000;
  static const int receiveTimeoutMs = 30000;
  static const int sendTimeoutMs = 30000;

  /// 分页
  static const int defaultPageSize = 20;
  static const int maxPageSize = 50;

  /// 本地通知 Channel（Android）
  static const String notifyChannelPomodoro = 'pomodoro';
  static const String notifyChannelReminder = 'reminder';
  static const String notifyChannelMessage = 'message';

  /// 本地通知 ID 基值
  static const int notifyIdPomodoro = 1000;
  static const int notifyIdReview = 2000;

  /// 缓存过期时长
  static const Duration cacheKnowledgeGraph = Duration(days: 7);
  static const Duration cacheLearningPath = Duration(days: 1);
  static const Duration cacheCommunity = Duration(minutes: 5);
}
