// Isar 本地实体：应用设置（键值对存储）
// 骨架阶段用作占位 collection，后续可扩展学习记录、错题等实体

import 'package:isar/isar.dart';

part 'app_settings.g.dart';

@collection
class AppSettings {
  AppSettings({
    this.id = Isar.autoIncrement,
    required this.key,
    required this.value,
    this.updatedAt,
  });

  final Id id;

  @Index(unique: true)
  final String key;

  final String value;

  final DateTime? updatedAt;
}
