// 本地存储 Key

class StorageKeys {
  StorageKeys._();

  // ===== SecureStorage =====
  static const String accessToken = 'kf_access_token';
  static const String refreshToken = 'kf_refresh_token';

  // ===== SharedPreferences =====
  static const String userId = 'kf_user_id';
  static const String themeMode = 'kf_theme_mode'; // 'system'|'light'|'dark'
  static const String locale = 'kf_locale';        // 'zh'|'en'
  static const String onboardingDone = 'kf_onboarding_done';
  static const String lastLoginAt = 'kf_last_login_at';

  // ===== Isar Collection Names（用 Isar 的 @collection 即可，这里仅作枚举）
  static const String dbChapter = 'chapters';
  static const String dbFlashcard = 'flashcards';
  static const String dbUserProfile = 'user_profiles';
}
