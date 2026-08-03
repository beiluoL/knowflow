// Isar 本地数据库初始化
// 当前包含 AppSettings 占位 collection，后续在 data/db/models/ 下扩展

import 'package:isar/isar.dart';
import 'package:knowflow_mobile/data/db/models/app_settings.dart';
import 'package:path_provider/path_provider.dart';

class AppDatabase {
  AppDatabase._(this.isar);
  final Isar isar;

  static AppDatabase? _instance;
  Isar get db => isar;

  static Future<AppDatabase> init() async {
    if (_instance != null) return _instance!;
    final dir = await getApplicationDocumentsDirectory();
    final isar = await Isar.open(
      [AppSettingsSchema],
      directory: dir.path,
      name: 'knowflow',
      inspector: true,
    );
    _instance = AppDatabase._(isar);
    return _instance!;
  }
}
