// Material 3 主题 + 设计令牌（对齐 Web 端色系：#3B6FE0 蓝 + #FF6B35 高亮）

import 'package:flutter/material.dart';

/// 设计令牌：颜色
class AppColors {
  AppColors._();

  // 主色
  static const Color primary = Color(0xFF3B6FE0);
  static const Color primarySoft = Color(0xFFE8F0FE);
  static const Color primaryBorder = Color(0xFFB8CEF7);

  // 高亮色（番茄钟/强调）
  static const Color highlight = Color(0xFFFF6B35);
  static const Color highlightSoft = Color(0xFFFFEFE7);
  static const Color highlightBorder = Color(0xFFFFCDB8);

  // 成功 / 错误 / 警告
  static const Color success = Color(0xFF10B981);
  static const Color danger = Color(0xFFEF4444);
  static const Color warning = Color(0xFFF59E0B);

  // 背景
  static const Color backgroundLight = Color(0xFFF7F8FA);
  static const Color backgroundDark = Color(0xFF0F172A);

  // 卡片
  static const Color cardLight = Color(0xFFFFFFFF);
  static const Color cardDark = Color(0xFF1E293B);

  // 文字
  static const Color foregroundLight = Color(0xFF0F172A);
  static const Color foregroundDark = Color(0xFFF1F5F9);
  static const Color mutedLight = Color(0xFF64748B);
  static const Color mutedDark = Color(0xFF94A3B8);

  // 边框/分割线
  static const Color borderLight = Color(0xFFE8ECF1);
  static const Color borderDark = Color(0xFF334155);

  // 模式色
  static const Color focusRed = Color(0xFFEF4444);
  static const Color shortBreakGreen = Color(0xFF10B981);
  static const Color longBreakBlue = Color(0xFF3B6FE0);
}

/// 设计令牌：尺寸（4px 网格）
class AppSpacing {
  AppSpacing._();
  static const double xs = 4;
  static const double sm = 8;
  static const double md = 12;
  static const double lg = 16;
  static const double xl = 20;
  static const double xxl = 24;
  static const double xxxl = 32;
}

/// 设计令牌：圆角
class AppRadius {
  AppRadius._();
  static const double sm = 4;
  static const double md = 8;
  static const double lg = 12;
  static const double xl = 16;
  static const double xxl = 24;
  static const double full = 9999;
}

/// 设计令牌：字号
class AppFontSize {
  AppFontSize._();
  static const double xs = 11;
  static const double sm = 12;
  static const double base = 14;
  static const double lg = 16;
  static const double xl = 18;
  static const double h3 = 20;
  static const double h2 = 24;
  static const double h1 = 28;
}

class AppTheme {
  AppTheme._();

  static ThemeData light() {
    const seed = AppColors.primary;
    final colorScheme = ColorScheme.fromSeed(
      seedColor: seed,
      brightness: Brightness.light,
      primary: seed,
    );
    return ThemeData(
      useMaterial3: true,
      colorScheme: colorScheme.copyWith(
        surface: AppColors.backgroundLight,
        surfaceContainerHighest: AppColors.cardLight,
        error: AppColors.danger,
      ),
      scaffoldBackgroundColor: AppColors.backgroundLight,
      primaryColor: seed,
      dividerColor: AppColors.borderLight,
      appBarTheme: const AppBarTheme(
        backgroundColor: AppColors.cardLight,
        foregroundColor: AppColors.foregroundLight,
        elevation: 0,
        scrolledUnderElevation: 0.5,
        centerTitle: false,
        titleTextStyle: TextStyle(
          fontSize: AppFontSize.h3,
          fontWeight: FontWeight.w600,
          color: AppColors.foregroundLight,
        ),
      ),
      cardTheme: CardTheme(
        color: AppColors.cardLight,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppRadius.md),
          side: const BorderSide(color: AppColors.borderLight, width: 0.5),
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: seed,
          foregroundColor: Colors.white,
          elevation: 0,
          minimumSize: const Size.fromHeight(44),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(AppRadius.md),
          ),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: seed,
          side: const BorderSide(color: AppColors.borderLight),
          minimumSize: const Size.fromHeight(44),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(AppRadius.md),
          ),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: seed,
          minimumSize: const Size.fromHeight(44),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(AppRadius.md),
          ),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AppColors.cardLight,
        contentPadding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.md,
          vertical: AppSpacing.md,
        ),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppRadius.md),
          borderSide: const BorderSide(color: AppColors.borderLight),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppRadius.md),
          borderSide: const BorderSide(color: AppColors.borderLight),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(AppRadius.md),
          borderSide: const BorderSide(color: AppColors.primary, width: 1.5),
        ),
        hintStyle: const TextStyle(
          color: AppColors.mutedLight,
          fontSize: AppFontSize.base,
        ),
      ),
      textTheme: const TextTheme(
        displayLarge: TextStyle(
          fontSize: AppFontSize.h1,
          fontWeight: FontWeight.w700,
          color: AppColors.foregroundLight,
        ),
        headlineMedium: TextStyle(
          fontSize: AppFontSize.h2,
          fontWeight: FontWeight.w700,
          color: AppColors.foregroundLight,
        ),
        titleLarge: TextStyle(
          fontSize: AppFontSize.h3,
          fontWeight: FontWeight.w600,
          color: AppColors.foregroundLight,
        ),
        titleMedium: TextStyle(
          fontSize: AppFontSize.lg,
          fontWeight: FontWeight.w600,
          color: AppColors.foregroundLight,
        ),
        bodyLarge: TextStyle(
          fontSize: AppFontSize.lg,
          color: AppColors.foregroundLight,
        ),
        bodyMedium: TextStyle(
          fontSize: AppFontSize.base,
          color: AppColors.foregroundLight,
        ),
        bodySmall: TextStyle(
          fontSize: AppFontSize.sm,
          color: AppColors.mutedLight,
        ),
        labelSmall: TextStyle(
          fontSize: AppFontSize.xs,
          color: AppColors.mutedLight,
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: AppColors.cardLight,
        indicatorColor: AppColors.primarySoft,
        height: 64,
        labelTextStyle: WidgetStateProperty.all(
          const TextStyle(
            fontSize: AppFontSize.xs,
            fontWeight: FontWeight.w500,
          ),
        ),
        iconTheme: WidgetStateProperty.all(
          const IconThemeData(size: 22),
        ),
      ),
      bottomSheetTheme: const BottomSheetThemeData(
        backgroundColor: AppColors.cardLight,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(AppRadius.xl)),
        ),
      ),
    );
  }

  static ThemeData dark() {
    const seed = AppColors.primary;
    final colorScheme = ColorScheme.fromSeed(
      seedColor: seed,
      brightness: Brightness.dark,
      primary: seed,
    );
    return ThemeData(
      useMaterial3: true,
      colorScheme: colorScheme.copyWith(
        surface: AppColors.backgroundDark,
        surfaceContainerHighest: AppColors.cardDark,
        error: AppColors.danger,
      ),
      scaffoldBackgroundColor: AppColors.backgroundDark,
      dividerColor: AppColors.borderDark,
      appBarTheme: const AppBarTheme(
        backgroundColor: AppColors.cardDark,
        foregroundColor: AppColors.foregroundDark,
        elevation: 0,
        centerTitle: false,
      ),
      cardTheme: CardTheme(
        color: AppColors.cardDark,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppRadius.md),
          side: const BorderSide(color: AppColors.borderDark, width: 0.5),
        ),
      ),
      textTheme: const TextTheme(
        displayLarge: TextStyle(
          fontSize: AppFontSize.h1,
          fontWeight: FontWeight.w700,
          color: AppColors.foregroundDark,
        ),
        headlineMedium: TextStyle(
          fontSize: AppFontSize.h2,
          fontWeight: FontWeight.w700,
          color: AppColors.foregroundDark,
        ),
        bodyMedium: TextStyle(
          fontSize: AppFontSize.base,
          color: AppColors.foregroundDark,
        ),
        bodySmall: TextStyle(
          fontSize: AppFontSize.sm,
          color: AppColors.mutedDark,
        ),
      ),
    );
  }
}
