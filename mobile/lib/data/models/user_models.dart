// DTO 基类：登录响应、用户信息（不可变模型，freezed 后续可替换）

import 'package:json_annotation/json_annotation.dart';

part 'user_models.g.dart';

@JsonSerializable(fieldRename: FieldRename.snake)
class LoginRequest {
  LoginRequest({required this.username, required this.password});
  final String username;
  final String password;
  Map<String, dynamic> toJson() => _$LoginRequestToJson(this);
}

@JsonSerializable(fieldRename: FieldRename.snake)
class LoginResponse {
  LoginResponse({required this.accessToken, this.refreshToken, this.user});
  final String accessToken;
  final String? refreshToken;
  final UserDTO? user;
  factory LoginResponse.fromJson(Map<String, dynamic> json) =>
      _$LoginResponseFromJson(json);
}

@JsonSerializable(fieldRename: FieldRename.snake)
class UserDTO {
  UserDTO({
    required this.id,
    required this.username,
    this.nickname,
    this.email,
    this.avatar,
    this.role = 'USER',
    this.level = 1,
    this.points = 0,
    this.studyMinutes = 0,
    this.createdAt,
  });
  final int id;
  final String username;
  final String? nickname;
  final String? email;
  final String? avatar;
  final String role;
  final int level;
  final int points;
  final int studyMinutes;
  final DateTime? createdAt;

  String get displayName => (nickname?.isNotEmpty ?? false) ? nickname! : username;
  String get initials {
    if (displayName.isEmpty) return '?';
    final runes = displayName.runes;
    final first = runes.first;
    final ch = String.fromCharCode(first);
    return ch.toUpperCase();
  }
  bool get isAdmin => role.toUpperCase() == 'ADMIN';

  factory UserDTO.fromJson(Map<String, dynamic> json) =>
      _$UserDTOFromJson(json);
  Map<String, dynamic> toJson() => _$UserDTOToJson(this);
}

@JsonSerializable(fieldRename: FieldRename.snake, genericArgumentFactories: true)
class ApiResult<T> {
  ApiResult({required this.code, this.message, this.data});
  final int code;
  final String? message;
  final T? data;
  bool get isOk => code >= 200 && code < 300;

  factory ApiResult.fromJson(
    Map<String, dynamic> json,
    T Function(Object? json) fromJsonT,
  ) =>
      _$ApiResultFromJson(json, fromJsonT);
  Map<String, dynamic> toJson(Object? Function(T value) toJsonT) =>
      _$ApiResultToJson(this, toJsonT);
}
